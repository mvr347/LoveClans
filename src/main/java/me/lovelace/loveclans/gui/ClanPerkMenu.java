package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPerk;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

/**
 * Выбор перка клана: земледелие, ресурсодобыча, война. Раньше все три занимали отдельный
 * ряд в меню улучшений и растягивали его; теперь улучшения открывают это подменю одной
 * кнопкой «Перк».
 *
 * <p>Раскладка gui_gen v1.4, 27 слотов: голова темы в слоте 0, три перка — в шапке (3, 4, 5),
 * строка 9-17 пустая, футер со стеклом, назад и закрытие.
 */
public final class ClanPerkMenu {
    private static final int SLOT_INFO = 0;
    private static final int[] PERK_SLOTS = {3, 4, 5};
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    private static final int INVENTORY_SIZE = 27;

    private final LoveClansPlugin plugin;

    public ClanPerkMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private String headFor(ClanPerk perk) {
        return switch (perk) {
            case HARVESTER -> ItemBuilder.HEAD_PERK_HARVESTER;
            case MINER -> ItemBuilder.HEAD_PERK_MINER;
            case WARRIOR -> ItemBuilder.HEAD_PERK_WARRIOR;
        };
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.PERKS, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.upgrades.perks.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        int unlockLevel = plugin.getClanManager().perkUnlockLevel();
        boolean locked = clan.level() < unlockLevel;
        Optional<ClanPerk> current = clan.perk();
        long respecCost = plugin.getClanManager().perkRespecCost();

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT_ABILITIES)
                .name(plugin.getMessages().component("gui.upgrades.perk-button.name", player))
                .lore(plugin.getMessages().components("gui.upgrades.perk-button.lore", Map.of(
                        "perk", current.map(ClanPerk::displayName)
                                .orElseGet(() -> plugin.getMessages().raw("gui.upgrades.perk-button.none"))
                ), player))
                .build());

        ClanPerk[] perks = ClanPerk.values();
        for (int i = 0; i < PERK_SLOTS.length && i < perks.length; i++) {
            ClanPerk perk = perks[i];
            boolean active = current.map(p -> p == perk).orElse(false);

            // Перки всегда видны и просматриваемы (лор/описание), но неактивны (серая иконка,
            // клик ничего не делает - см. handleInventoryClick), если клан ниже требуемого уровня
            // или это уже выбранный перк.
            ItemBuilder builder = ItemBuilder.head(locked ? ItemBuilder.HEAD_INACTIVE : headFor(perk))
                    .name(plugin.getMessages().component("gui.upgrades.perks." + perk.name().toLowerCase() + ".name", player))
                    .lore(plugin.getMessages().components("gui.upgrades.perks." + perk.name().toLowerCase() + ".lore",
                            perkLorePlaceholders(perk), player));

            if (locked) {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.locked.lore",
                        Map.of("level", String.valueOf(unlockLevel)), player));
            } else if (active) {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.active", player)).glow(true);
            } else if (current.isPresent()) {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.click-to-change",
                        Map.of("cost", String.valueOf(respecCost)), player));
            } else {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.click-to-select", player));
            }

            inventory.setItem(PERK_SLOTS[i], builder.build());
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openUpgrades(player, clan);
            return;
        }

        for (int i = 0; i < PERK_SLOTS.length; i++) {
            if (slot != PERK_SLOTS[i]) continue;
            ClanPerk perk = ClanPerk.values()[i];
            if (clan.level() < plugin.getClanManager().perkUnlockLevel()) {
                plugin.getMessages().send(player, "gui.upgrades.perk-locked");
                return;
            }
            if (clan.perk().map(p -> p == perk).orElse(false)) {
                return;
            }
            plugin.getClanManager().choosePerkAsync(clan, player.getUniqueId(), perk)
                    .thenAccept(updated -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "gui.upgrades.perk-selected", Map.of("perk", perk.displayName()));
                        open(player, updated);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
    }

    private Map<String, String> perkLorePlaceholders(ClanPerk perk) {
        return switch (perk) {
            case HARVESTER -> Map.of(
                    "percent", String.valueOf(plugin.getConfig().getInt("perks.harvester.crop-growth-bonus-percent", 40)),
                    "hearts", String.valueOf(plugin.getConfig().getInt("perks.harvester.pvp-bonus-hearts", 2)),
                    "yield", String.valueOf(plugin.getConfig().getInt("perks.harvester.harvest-yield-bonus-percent", 10))
            );
            case MINER -> Map.of(
                    "percent", String.valueOf(plugin.getConfig().getInt("perks.miner.resource-yield-bonus-percent", 25)),
                    "rare", String.valueOf(plugin.getConfig().getInt("perks.miner.rare-drop-bonus-percent", 15))
            );
            case WARRIOR -> Map.of(
                    "percent", String.valueOf(plugin.getConfig().getInt("perks.warrior.siege-duration-reduction-percent", 25)),
                    "banner", String.valueOf(plugin.getConfig().getInt("perks.warrior.banner-damage-bonus-percent", 10))
            );
        };
    }
}
