package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

public final class ClanMainMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;

    public ClanMainMenu(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
    }

    public Clan clan() {
        return clan;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.main.title", Map.of("clan", clan.name(), "color", clan.tagColor()), player));

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Row 0, slot 0 — clan info (same slot used for player profile in chained menus)
        inventory.setItem(0, ItemBuilder.of(clan.emblem())
                .name(plugin.getMessages().component("gui.main.info.name", Map.of("clan", clan.name(), "color", clan.tagColor()), player))
                .lore(plugin.getMessages().component("gui.main.info.tag",
                        Map.of("tag", clan.tag()), player))
                .lore(plugin.getMessages().component("gui.main.info.level",
                        Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.main.info.members",
                        Map.of("current", String.valueOf(clan.members().size()),
                               "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .build());

        // Row 2 — main nav buttons
        inventory.setItem(19, ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.main.members.name", player))
                .lore(plugin.getMessages().component("gui.main.members.lore", player))
                .build());

        // Кнопки управления территориями/улучшениями/настройками/дипломатией становятся
        // неактивными (серый череп), если у игрока нет соответствующего права клана.
        // Кнопка территорий — особый случай: при отсутствии права на управление
        // она всё равно открывается, но в режиме просмотра/телепортации (см. handleInventoryClick).
        boolean atWar = plugin.getClanManager().inAnyConflict(clan.id());
        UUID clickerId = player.getUniqueId();
        boolean canManageTerritories = clan.hasPermission(clickerId, ClanPermission.CLAIM);
        boolean canUpgrade = clan.hasPermission(clickerId, ClanPermission.UPGRADE);
        boolean canManageSettings = clan.hasPermission(clickerId, ClanPermission.SETTINGS);
        boolean canManageDiplomacy = clan.hasPermission(clickerId, ClanPermission.DIPLOMACY);

        ItemBuilder diplomacyItem = canManageDiplomacy
                ? ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE);
        diplomacyItem.name(plugin.getMessages().component("gui.main.diplomacy.name", player))
                .lore(plugin.getMessages().component(canManageDiplomacy ? "gui.main.diplomacy.lore" : "gui.main.diplomacy.no-permission-lore", player));
        inventory.setItem(21, diplomacyItem.build());

        boolean territoriesInactive = atWar || !canManageTerritories;
        ItemBuilder territoriesItem = territoriesInactive
                ? ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                : ItemBuilder.head(ItemBuilder.HEAD_TERRITORIES);
        territoriesItem.name(plugin.getMessages().component("gui.main.territories.name", player))
                .lore(plugin.getMessages().component("gui.main.territories.lore", player));
        if (atWar) {
            territoriesItem.lore(plugin.getMessages().component("gui.capital.war-blocked", player));
        } else if (!canManageTerritories) {
            territoriesItem.lore(plugin.getMessages().component("gui.main.territories.no-permission-lore", player));
        }
        inventory.setItem(23, territoriesItem.build());

        boolean upgradesInactive = atWar || !canUpgrade;
        ItemBuilder upgradesItem = upgradesInactive
                ? ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                : ItemBuilder.head(ItemBuilder.HEAD_EXPERIENCE);
        upgradesItem.name(plugin.getMessages().component("gui.main.upgrades.name", player))
                .lore(plugin.getMessages().component("gui.main.upgrades.lore", player));
        if (atWar) {
            upgradesItem.lore(plugin.getMessages().component("gui.capital.war-blocked", player));
        } else if (!canUpgrade) {
            upgradesItem.lore(plugin.getMessages().component("gui.main.upgrades.no-permission-lore", player));
        }
        inventory.setItem(25, upgradesItem.build());

        // Row 4 — secondary buttons
        inventory.setItem(38, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT)
                .name(plugin.getMessages().component("gui.main.spirit.name", player))
                .lore(plugin.getMessages().component("gui.main.spirit.lore", player))
                .build());

        ItemBuilder settingsItem = canManageSettings
                ? ItemBuilder.head(ItemBuilder.HEAD_MAIN_SETTINGS)
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE);
        settingsItem.name(plugin.getMessages().component("gui.main.settings.name", player))
                .lore(plugin.getMessages().component(canManageSettings ? "gui.main.settings.lore" : "gui.main.settings.no-permission-lore", player));
        inventory.setItem(40, settingsItem.build());

        int applicationsCount = plugin.getClanManager().getClanApplications(clan.id()).size();
        boolean isLeaderOrGuardian = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .orElse(false);
        inventory.setItem(42, ItemBuilder.head(ItemBuilder.HEAD_MAIN_APPLICATIONS)
                .name(plugin.getMessages().component("gui.main.applications.name", player))
                .lore(isLeaderOrGuardian
                        ? plugin.getMessages().component("gui.main.applications.lore",
                                Map.of("count", String.valueOf(applicationsCount)), player)
                        : plugin.getMessages().component("gui.main.applications.no-permission-lore", player))
                .build());

        // Footer — standalone menu: no Back button (slot 52 stays glass), Leave Clan uses the extra slot (51)
        boolean isLeader = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER)
                .orElse(false);
        if (!isLeader) {
            inventory.setItem(51, ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                    .name(plugin.getMessages().component("gui.main.leave.name", player))
                    .lore(plugin.getMessages().component("gui.main.leave.lore", player))
                    .build());
        }

        inventory.setItem(53, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        switch (slot) {
            case 19 -> plugin.getGuiManager().openMembers(clicker, clan);
            case 21 -> {
                if (clan.hasPermission(clicker.getUniqueId(), ClanPermission.DIPLOMACY)) {
                    plugin.getGuiManager().openDiplomacySelect(clicker, clan);
                } else {
                    plugin.getMessages().send(clicker, "general.no-permission");
                }
            }
            case 23 -> {
                // Территории — особый случай: даже без права CLAIM меню всё равно открывается,
                // но в режиме просмотра/телепортации (см. ClanTerritoriesSelectionGui.isManagement).
                if (plugin.getClanManager().inAnyConflict(clan.id())) {
                    plugin.getMessages().send(clicker, "gui.capital.war-blocked");
                } else {
                    plugin.getGuiManager().openClanTerritoriesMenu(clicker, clan);
                }
            }
            case 25 -> {
                if (!clan.hasPermission(clicker.getUniqueId(), ClanPermission.UPGRADE)) {
                    plugin.getMessages().send(clicker, "general.no-permission");
                } else if (plugin.getClanManager().inAnyConflict(clan.id())) {
                    plugin.getMessages().send(clicker, "gui.capital.war-blocked");
                } else {
                    plugin.getGuiManager().openUpgrades(clicker, clan);
                }
            }
            case 38 -> plugin.getGuiManager().openSpiritMenu(clicker, clan);
            case 40 -> {
                if (clan.hasPermission(clicker.getUniqueId(), ClanPermission.SETTINGS)) {
                    plugin.getGuiManager().openSettings(clicker, clan);
                } else {
                    plugin.getMessages().send(clicker, "general.no-permission");
                }
            }
            case 42 -> {
                boolean canViewApps = clan.member(clicker.getUniqueId())
                        .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                        .orElse(false);
                if (canViewApps) {
                    plugin.getGuiManager().openApplications(clicker, clan);
                } else {
                    plugin.getMessages().send(clicker, "general.no-permission");
                }
            }
            case 53 -> clicker.closeInventory();
            case 51 -> {
                boolean isLeader = clan.member(clicker.getUniqueId())
                        .map(m -> m.rank() == ClanRank.LEADER)
                        .orElse(false);
                if (isLeader) return;

                plugin.getGuiManager().openConfirm(clicker, clan, 
                        plugin.getMessages().component("gui.confirm.leave.title", clicker), 
                        Component.empty(),
                        () -> plugin.getClanManager().removeMemberAsync(clan, clicker.getUniqueId(), clicker.getUniqueId(), false)
                                .thenRun(() -> plugin.runSync(() -> {
                                    plugin.getMessages().send(clicker, "clan.left");
                                    clicker.closeInventory();
                                }))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.runSync(this::open)
                );
            }
            default -> {}
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
