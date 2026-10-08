package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.modifier.ClanModifier;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Меню активных модификаторов клана (репарации, щиты, дебаффы, casus belli).
 * Спроектировано по стандарту gui-gen-5 (54 слота).
 */
public final class ClanModifiersMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;

    // Диапазон рабочей зоны (gui-gen-5: 18-44, боковые стенки пустые, без стекла)
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LoveClansPlugin plugin;

    public ClanModifiersMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.MODIFIERS, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                plugin.getMessages().component("gui.modifiers.title", Map.of("clan", clan.name()), player)
        );
        holder.setInventory(inventory);

        // Рамка 54 слота: стекло в 1-8, 9-17 (Row1), 45-52. В рабочей зоне (18-44) стекла нет.
        GuiFrames.fillFrame54(inventory);

        // Слот 0 — эмблема/инфо клана
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(Component.text("§6Модификаторы клана §f" + clan.name()))
                .lore(Component.text("§7Список активных эффектов, репараций,"))
                .lore(Component.text("§7щитов и доступных казус белли."))
                .build());

        List<ClanModifier> modifiers = plugin.getModifierManager().getModifiers(clan.id());
        long now = System.currentTimeMillis();

        if (modifiers.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                    .name(Component.text("§cНет активных модификаторов"))
                    .lore(Component.text("§7У вашего клана в данный момент нет"))
                    .lore(Component.text("§7выплат дани, щитов или казус белли."))
                    .build());
        } else {
            int slotIdx = 0;
            for (ClanModifier mod : modifiers) {
                if (slotIdx >= CONTENT_SLOTS.length) break;
                int targetSlot = CONTENT_SLOTS[slotIdx++];
                inventory.setItem(targetSlot, renderModifier(mod, now));
            }
        }

        // Footer: кнопка Назад (52) и Закрыть (53)
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == SLOT_BACK) {
            new ClanMainMenu(plugin, clan, player).open();
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private ItemStack renderModifier(ClanModifier mod, long now) {
        return switch (mod.type()) {
            case ClanModifier.TYPE_REPARATIONS_DEBT -> {
                String targetTag = resolveClanTag(mod.targetClanId());
                yield ItemBuilder.of(Material.RED_BANNER)
                        .name(Component.text("§cВыплата дани: §f" + targetTag))
                        .lore(Component.text("§7Ежедневная выплата: §e" + mod.dailyAmount() + "⛃"))
                        .lore(Component.text("§7Осталось дней: §f" + mod.daysLeft()))
                        .lore(Component.text("§7Неуплат подряд: " + (mod.arrears() > 0 ? "§c" + mod.arrears() : "§a0")))
                        .lore(Component.empty())
                        .lore(Component.text("§8Списывается автоматически раз в сутки."))
                        .build();
            }
            case ClanModifier.TYPE_REPARATIONS_INCOME -> {
                String targetTag = resolveClanTag(mod.targetClanId());
                yield ItemBuilder.of(Material.LIME_BANNER)
                        .name(Component.text("§aПолучение дани: §f" + targetTag))
                        .lore(Component.text("§7Ежедневное поступление: §e" + mod.dailyAmount() + "⛃"))
                        .lore(Component.text("§7Осталось дней: §f" + mod.daysLeft()))
                        .lore(Component.empty())
                        .lore(Component.text("§8Поступает в казну сундука раз в сутки."))
                        .build();
            }
            case ClanModifier.TYPE_POST_RAID_SHIELD -> {
                String timeLeft = TimeUtil.formatDuration(mod.remainingMillis(now));
                yield ItemBuilder.of(Material.SHIELD)
                        .name(Component.text("§bЩит после набега"))
                        .lore(Component.text("§7Осталось времени: §f" + timeLeft))
                        .lore(Component.empty())
                        .lore(Component.text("§8Защищает клан от новых набегов."))
                        .build();
            }
            case ClanModifier.TYPE_SIEGE_PRESSURE -> {
                String timeLeft = TimeUtil.formatDuration(mod.remainingMillis(now));
                yield ItemBuilder.of(Material.CRACKED_STONE_BRICKS)
                        .name(Component.text("§4Осадное давление"))
                        .lore(Component.text("§7Осталось времени: §f" + timeLeft))
                        .lore(Component.text("§cШтраф после поражения в осаде."))
                        .build();
            }
            case ClanModifier.TYPE_SIEGE_REPELLED -> {
                String timeLeft = TimeUtil.formatDuration(mod.remainingMillis(now));
                yield ItemBuilder.of(Material.BEACON)
                        .name(Component.text("§6Отражение осады"))
                        .lore(Component.text("§7Осталось времени: §f" + timeLeft))
                        .lore(Component.text("§aБоевой дух клана укреплен!"))
                        .build();
            }
            case ClanModifier.TYPE_JUST_CASUS -> {
                String targetTag = resolveClanTag(mod.targetClanId());
                String timeLeft = mod.endsAt() > 0 ? TimeUtil.formatDuration(mod.remainingMillis(now)) : "бессрочно";
                yield ItemBuilder.of(Material.WRITTEN_BOOK)
                        .name(Component.text("§eПовод к войне: §f" + mod.conflictType()))
                        .lore(Component.text("§7Цель: §f" + targetTag))
                        .lore(Component.text("§7Причина: §a" + mod.reasonId()))
                        .lore(Component.text("§7Срок действия: §f" + timeLeft))
                        .lore(Component.empty())
                        .lore(Component.text("§8Справедливый повод для конфликта."))
                        .build();
            }
            default -> ItemBuilder.of(Material.PAPER)
                    .name(Component.text("§7Модификатор: " + mod.type()))
                    .build();
        };
    }

    private String resolveClanTag(UUID clanId) {
        if (clanId == null) return "Неизвестно";
        return plugin.getClanManager().getClanById(clanId)
                .map(Clan::name)
                .orElse(clanId.toString().substring(0, 8));
    }
}
