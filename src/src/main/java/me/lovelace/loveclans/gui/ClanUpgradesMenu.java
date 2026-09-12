package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPerk;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

public final class ClanUpgradesMenu {
    // Раскладка gui_gen v1.4. Меню было на 45 слотов — размер вне стандарта, из-за чего
    // рамка и рабочая зона не совпадали с остальными меню клана. Переведено на 54:
    // голова уровня в слоте 0, улучшения и выбор перка — в рабочей зоне, назад и
    // закрытие — в футере (52, 53).
    private static final int[] UPGRADE_SLOTS = {19, 21, 23, 25};
    private static final int SLOT_PERK = 31;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int INVENTORY_SIZE = 54;

    private final LoveClansPlugin plugin;

    public ClanUpgradesMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private String headFor(ClanUpgrade upgrade) {
        return switch (upgrade) {
            case MEMBERS -> ItemBuilder.HEAD_MEMBERS;
            case TERRITORIES -> ItemBuilder.HEAD_TERRITORIES;
            case EXPERIENCE -> ItemBuilder.HEAD_EXPERIENCE;
            case CHEST -> ItemBuilder.HEAD_EXPAND;
        };
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.UPGRADES, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.upgrades-title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        long currentExp = clan.experience();
        long expForCurrent = plugin.getClanManager().experienceForLevel(clan.level());
        long expForNext = plugin.getClanManager().experienceForLevel(clan.level() + 1);
        long required = expForNext - expForCurrent;
        long progress = currentExp - expForCurrent;
        double percent = required > 0 ? (double) progress / required * 100 : 100.0;

        // Слот 0: голова "уровень клана" — тематический профиль этого экрана (не игрока).
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_LEVEL_INFO)
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

        renderPerkButton(inventory, player, clan);

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> plugin.getGuiManager().openMain(player, clan));
            return;
        }
        if (slot == SLOT_PERK) {
            plugin.getClanManager().getPlayerClan(player.getUniqueId())
                    .ifPresent(clan -> plugin.getGuiManager().openPerks(player, clan));
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

    /** Один вход в подменю перков: показывает выбранный перк, выбор — внутри. */
    private void renderPerkButton(Inventory inventory, Player player, Clan clan) {
        int unlockLevel = plugin.getClanManager().perkUnlockLevel();
        boolean locked = clan.level() < unlockLevel;
        Optional<ClanPerk> current = clan.perk();

        ItemBuilder builder = ItemBuilder.head(locked ? ItemBuilder.HEAD_INACTIVE : ItemBuilder.HEAD_SPIRIT_ABILITIES)
                .name(plugin.getMessages().component("gui.upgrades.perk-button.name", player))
                .lore(plugin.getMessages().components("gui.upgrades.perk-button.lore", Map.of(
                        "perk", current.map(ClanPerk::displayName)
                                .orElseGet(() -> plugin.getMessages().raw("gui.upgrades.perk-button.none"))
                ), player));

        if (locked) {
            builder.lore(plugin.getMessages().component("gui.upgrades.perks.locked.lore",
                    Map.of("level", String.valueOf(unlockLevel)), player));
        } else {
            builder.lore(plugin.getMessages().component("gui.upgrades.perk-button.open", player));
        }

        inventory.setItem(SLOT_PERK, builder.build());
    }

}
