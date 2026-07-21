package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPerk;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

public final class ClanUpgradesMenu {
    private static final int[] UPGRADE_SLOTS = {20, 22, 24};
    private static final int[] PERK_SLOTS = {11, 13, 15};
    private static final int PERK_LOCKED_SLOT = 13;

    private final LoveClansPlugin plugin;

    public ClanUpgradesMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private String headFor(ClanUpgrade upgrade) {
        return switch (upgrade) {
            case MEMBERS -> ItemBuilder.HEAD_MEMBERS;
            case TERRITORIES -> ItemBuilder.HEAD_TERRITORIES;
            case EXPERIENCE -> ItemBuilder.HEAD_EXPERIENCE;
        };
    }

    private Material iconFor(ClanPerk perk) {
        return switch (perk) {
            case HARVESTER -> Material.WHEAT;
            case MINER -> Material.DIAMOND_PICKAXE;
            case WARRIOR -> Material.IRON_SWORD;
        };
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.UPGRADES, clan.id()), 45,
                plugin.getMessages().component("gui.upgrades-title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        fillGlass(inventory);

        long currentExp = clan.experience();
        long expForCurrent = plugin.getClanManager().experienceForLevel(clan.level());
        long expForNext = plugin.getClanManager().experienceForLevel(clan.level() + 1);
        long required = expForNext - expForCurrent;
        long progress = currentExp - expForCurrent;
        double percent = required > 0 ? (double) progress / required * 100 : 100.0;

        inventory.setItem(4, ItemBuilder.head(ItemBuilder.HEAD_LEVEL_INFO)
                .name(plugin.getMessages().component("gui.upgrades.level-info.name", player))
                .lore(plugin.getMessages().components("gui.upgrades.level-info.lore", Map.of(
                        "level", String.valueOf(clan.level()),
                        "current_exp", String.valueOf(currentExp),
                        "next_exp", String.valueOf(expForNext),
                        "percent", String.format("%.1f", percent)
                ), player))
                .lore(plugin.getMessages().component("gui.upgrades.points-info.lore", Map.of("points", String.valueOf(clan.upgradePoints())), player))
                .build());

        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            ClanUpgrade upgrade = ClanUpgrade.values()[i];
            int level = clan.upgradeLevel(upgrade);
            int maxLevel = plugin.getClanManager().maxUpgradeLevel(upgrade);
            boolean maxed = level >= maxLevel;
            boolean canUpgrade = !maxed && clan.upgradePoints() > 0;

            ItemBuilder builder = ItemBuilder.head(maxed || !canUpgrade ? ItemBuilder.HEAD_INACTIVE : headFor(upgrade))
                    .name(plugin.getMessages().component("gui.upgrades.item.name", Map.of("name", upgrade.displayName()), player))
                    .lore(plugin.getMessages().component("gui.upgrades.item.level", Map.of(
                            "level", String.valueOf(level),
                            "max", String.valueOf(maxLevel)
                    ), player))
                    .lore(plugin.getMessages().component("gui.upgrades.item." + upgrade.name().toLowerCase(), player));

            if (maxed) {
                builder.lore(plugin.getMessages().component("gui.upgrades.item.maxed", player));
            } else if (canUpgrade) {
                builder.lore(plugin.getMessages().component("gui.upgrades.item.click-to-upgrade", player));
            } else {
                builder.lore(plugin.getMessages().component("gui.upgrades.item.no-points", player));
            }

            inventory.setItem(UPGRADE_SLOTS[i], builder.build());
        }

        renderPerks(inventory, player, clan);

        inventory.setItem(43, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(44, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == 44) {
            player.closeInventory();
            return;
        }
        if (slot == 43) {
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> plugin.getGuiManager().openMain(player, clan));
            return;
        }

        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            if (slot != UPGRADE_SLOTS[i]) continue;
            ClanUpgrade upgrade = ClanUpgrade.values()[i];
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan ->
                    plugin.getClanManager().purchaseUpgradeAsync(clan, player.getUniqueId(), upgrade)
                            .thenAccept(updated -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, "gui.upgrades.success", Map.of("upgrade", upgrade.displayName()));
                                open(player, updated);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; })
            );
            return;
        }

        for (int i = 0; i < PERK_SLOTS.length; i++) {
            if (slot != PERK_SLOTS[i]) continue;
            ClanPerk perk = ClanPerk.values()[i];
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
                if (clan.level() < plugin.getClanManager().perkUnlockLevel() || clan.perk().map(p -> p == perk).orElse(false)) {
                    return;
                }
                plugin.getClanManager().choosePerkAsync(clan, player.getUniqueId(), perk)
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "gui.upgrades.perk-selected", Map.of("perk", perk.displayName()));
                            open(player, updated);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            });
            return;
        }
    }

    private void renderPerks(Inventory inventory, Player player, Clan clan) {
        int unlockLevel = plugin.getClanManager().perkUnlockLevel();
        if (clan.level() < unlockLevel) {
            inventory.setItem(PERK_LOCKED_SLOT, ItemBuilder.of(Material.GRAY_DYE)
                    .name(plugin.getMessages().component("gui.upgrades.perks.locked.name", player))
                    .lore(plugin.getMessages().component("gui.upgrades.perks.locked.lore",
                            Map.of("level", String.valueOf(unlockLevel)), player))
                    .build());
            return;
        }

        Optional<ClanPerk> current = clan.perk();
        long respecCost = plugin.getClanManager().perkRespecCost();
        ClanPerk[] perks = ClanPerk.values();
        for (int i = 0; i < PERK_SLOTS.length && i < perks.length; i++) {
            ClanPerk perk = perks[i];
            boolean active = current.map(p -> p == perk).orElse(false);

            ItemBuilder builder = ItemBuilder.of(iconFor(perk))
                    .name(plugin.getMessages().component("gui.upgrades.perks." + perk.name().toLowerCase() + ".name", player))
                    .lore(plugin.getMessages().components("gui.upgrades.perks." + perk.name().toLowerCase() + ".lore",
                            perkLorePlaceholders(perk), player));

            if (active) {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.active", player)).glow(true);
            } else if (current.isPresent()) {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.click-to-change",
                        Map.of("cost", String.valueOf(respecCost)), player));
            } else {
                builder.lore(plugin.getMessages().component("gui.upgrades.perks.click-to-select", player));
            }

            inventory.setItem(PERK_SLOTS[i], builder.build());
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

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
