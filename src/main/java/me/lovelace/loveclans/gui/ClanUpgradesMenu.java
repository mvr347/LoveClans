package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class ClanUpgradesMenu {
    private static final int[] UPGRADE_SLOTS = {20, 22, 24};

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

        inventory.setItem(40, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == 40) {
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
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
