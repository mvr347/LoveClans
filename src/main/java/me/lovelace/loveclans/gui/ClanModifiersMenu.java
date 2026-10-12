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
                .name(plugin.getMessages().component("gui.modifiers.info.name", Map.of("clan", clan.name()), player))
                .lore(plugin.getMessages().components("gui.modifiers.info.lore", Map.of(), player))
                .build());

        List<ItemStack> items = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();

        List<ClanModifier> modifiers = plugin.getModifierManager().getModifiers(clan.id());
        for (ClanModifier mod : modifiers) {
            items.add(renderModifier(player, mod, now));
        }

        if (plugin.getDiplomacyManager() != null) {
            for (UUID targetId : plugin.getDiplomacyManager().getEmbargoesForClan(clan.id())) {
                String targetTag = resolveClanTag(targetId);
                items.add(entry(player, Material.BARRIER, "embargo", Map.of("tag", targetTag)));
            }

            for (UUID targetId : plugin.getDiplomacyManager().getOutgoingBlockades(clan.id())) {
                String targetTag = resolveClanTag(targetId);
                items.add(entry(player, Material.ANVIL, "blockade-out", Map.of("tag", targetTag)));
            }

            for (UUID blockerId : plugin.getDiplomacyManager().getIncomingBlockades(clan.id())) {
                String blockerTag = resolveClanTag(blockerId);
                items.add(entry(player, Material.IRON_BARS, "blockade-in", Map.of("tag", blockerTag)));
            }

            for (me.lovelace.loveclans.model.diplomacy.ClanPact pact : plugin.getDiplomacyManager().getPactsForClan(clan.id())) {
                UUID otherId = pact.otherClanId(clan.id());
                String otherTag = resolveClanTag(otherId);
                String timeLeft = TimeUtil.formatDuration(pact.remainingMillis(now));
                switch (pact.type()) {
                    case NAP -> items.add(entry(player, Material.WHITE_BANNER, "pact-nap", Map.of("tag", otherTag, "time", timeLeft)));
                    case TRADE -> items.add(entry(player, Material.EMERALD, "pact-trade", Map.of("tag", otherTag, "time", timeLeft)));
                    case ALLIANCE -> items.add(entry(player, Material.DIAMOND, "pact-alliance", Map.of("tag", otherTag, "time", timeLeft)));
                }
            }
        }

        if (items.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                    .name(plugin.getMessages().component("gui.modifiers.empty.name", player))
                    .lore(plugin.getMessages().components("gui.modifiers.empty.lore", Map.of(), player))
                    .build());
        } else {
            int slotIdx = 0;
            for (ItemStack item : items) {
                if (slotIdx >= CONTENT_SLOTS.length) break;
                inventory.setItem(CONTENT_SLOTS[slotIdx++], item);
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

    private ItemStack renderModifier(Player player, ClanModifier mod, long now) {
        String timeLeft = TimeUtil.formatDuration(mod.remainingMillis(now));
        return switch (mod.type()) {
            case ClanModifier.TYPE_REPARATIONS_DEBT -> entry(player, Material.RED_BANNER, "reparations-debt", Map.of(
                    "tag", resolveClanTag(mod.targetClanId()), "amount", String.valueOf(mod.dailyAmount()),
                    "days", String.valueOf(mod.daysLeft()),
                    "arrears", mod.arrears() > 0 ? "<red>" + mod.arrears() + "</red>" : "<green>0</green>"));
            case ClanModifier.TYPE_REPARATIONS_INCOME -> entry(player, Material.LIME_BANNER, "reparations-income", Map.of(
                    "tag", resolveClanTag(mod.targetClanId()), "amount", String.valueOf(mod.dailyAmount()),
                    "days", String.valueOf(mod.daysLeft())));
            case ClanModifier.TYPE_POST_RAID_SHIELD -> entry(player, Material.SHIELD, "post-raid-shield", Map.of("time", timeLeft));
            case ClanModifier.TYPE_SIEGE_PRESSURE -> entry(player, Material.CRACKED_STONE_BRICKS, "siege-pressure", Map.of("time", timeLeft));
            case ClanModifier.TYPE_SIEGE_REPELLED -> entry(player, Material.BEACON, "siege-repelled", Map.of("time", timeLeft));
            case ClanModifier.TYPE_JUST_CASUS -> entry(player, Material.WRITTEN_BOOK, "just-casus", Map.of(
                    "type", String.valueOf(mod.conflictType()), "tag", resolveClanTag(mod.targetClanId()),
                    "reason", String.valueOf(mod.reasonId()),
                    "time", mod.endsAt() > 0 ? timeLeft : plugin.getMessages().raw("gui.modifiers.forever")));
            case ClanModifier.TYPE_GUILD_SHIELD -> entry(player, Material.GOLDEN_APPLE, "guild-shield", Map.of("time", timeLeft));
            case ClanModifier.TYPE_GUILD_SHIELD_COOLDOWN -> entry(player, Material.CLOCK, "guild-shield-cooldown", Map.of("time", timeLeft));
            case ClanModifier.TYPE_CAPITAL_RELOCATE_COOLDOWN -> entry(player, Material.CLOCK, "relocate-cooldown", Map.of("time", timeLeft));
            default -> entry(player, Material.PAPER, "unknown", Map.of("type", mod.type()));
        };
    }

    /** One list entry: {@code gui.modifiers.entry.<id>.name} and {@code .lore}, all text lives in lang. */
    private ItemStack entry(Player player, Material icon, String id, Map<String, String> placeholders) {
        String base = "gui.modifiers.entry." + id;
        return ItemBuilder.of(icon)
                .name(plugin.getMessages().component(base + ".name", placeholders, player))
                .lore(plugin.getMessages().components(base + ".lore", placeholders, player))
                .build();
    }

    private String resolveClanTag(UUID clanId) {
        if (clanId == null) return plugin.getMessages().raw("gui.modifiers.unknown-clan");
        return plugin.getClanManager().getClanById(clanId)
                .map(Clan::name)
                .orElse(clanId.toString().substring(0, 8));
    }
}
