package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClanMainMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;

    private static final int SLOT_CHEST = 29;
    private static final int SLOT_TREASURY = 31;
    private static final int SLOT_SPIRIT = 33;
    // Row 4: centered buttons (Trade 39, Modifiers 40, Settings 41)
    private static final int SLOT_TRADE = 39;
    private static final int SLOT_MODIFIERS = 40;
    private static final int SLOT_SETTINGS = 41;
    private static final int SLOT_LEAVE = 52;
    private static final int SLOT_CLOSE = 53;

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

        GuiFrames.fillFrame54(inventory);

        // Row 0, slot 0 — clan info (same slot used for player profile in chained menus).
        // A non-banner emblem (misconfigured clans.default-emblem, or a legacy/corrupted row)
        // would otherwise leave this slot showing whatever that material's icon is - or nothing
        // at all for Material.AIR. Same fallback ClanDiplomacySelectMenu already applies when
        // rendering other clans' emblems; this was the one place still missing it.
        Material clanEmblem = clan.emblem() != null && clan.emblem().name().endsWith("_BANNER")
                ? clan.emblem() : Material.WHITE_BANNER;
        String statusKey = clan.isRecognized() ? "gui.main.info.status-recognized" : "gui.main.info.status-unrecognized";
        inventory.setItem(0, ItemBuilder.of(clanEmblem)
                .name(plugin.getMessages().component("gui.main.info.name", Map.of("clan", clan.name(), "color", clan.tagColor()), player))
                .lore(plugin.getMessages().component("gui.main.info.tag",
                        Map.of("tag", clan.tag()), player))
                .lore(plugin.getMessages().component("gui.main.info.level",
                        Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.main.info.members",
                        Map.of("current", String.valueOf(clan.members().size()),
                               "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .lore(plugin.getMessages().component(statusKey, player))
                .build());

        // Row 2 — main nav buttons. Applications live inside the members screen now; the count rides on this button.
        boolean canViewApps = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .orElse(false);
        int applicationsCount = canViewApps ? plugin.getClanManager().getClanApplications(clan.id()).size() : 0;
        ItemBuilder membersItem = ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.main.members.hub-name", player))
                .lore(plugin.getMessages().component("gui.main.members.hub-lore", player));
        if (applicationsCount > 0) {
            membersItem.lore(plugin.getMessages().component("gui.main.members.applications-lore",
                    Map.of("count", String.valueOf(applicationsCount)), player));
        }
        inventory.setItem(19, membersItem.build());

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

        boolean hasTerritory = clan.hasCapital();
        boolean diplomacyActive = canManageDiplomacy && hasTerritory;
        ItemBuilder diplomacyItem = diplomacyActive
                ? ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE);
        diplomacyItem.name(plugin.getMessages().component("gui.main.diplomacy.name", player))
                .lore(plugin.getMessages().component(!canManageDiplomacy ? "gui.main.diplomacy.no-permission-lore"
                        : !hasTerritory ? "gui.main.no-territory-lore" : "gui.main.diplomacy.lore", player));
        inventory.setItem(21, diplomacyItem.build());

        boolean clanHouseInactive = atWar || !canManageTerritories;
        ItemBuilder clanHouseItem;
        if (clanHouseInactive) {
            clanHouseItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.main.territories.name", player))
                    .lore(plugin.getMessages().component("gui.main.territories.lore", player));
            if (atWar) {
                clanHouseItem.lore(plugin.getMessages().component("gui.capital.war-blocked", player));
            } else {
                clanHouseItem.lore(plugin.getMessages().component("gui.main.territories.no-permission-lore", player));
            }
        } else {
            clanHouseItem = ItemBuilder.head(ItemBuilder.HEAD_CAPITAL)
                    .name(plugin.getMessages().component("gui.main.territories.name", player))
                    .lore(plugin.getMessages().component("gui.main.territories.lore", player));
        }
        inventory.setItem(23, clanHouseItem.build());

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

        // Row 3 — clan storage and the spirit
        boolean hasCapital = clan.hasCapital();
        boolean taxLocked = clan.isChestTaxLocked();
        inventory.setItem(SLOT_CHEST, chestItem(hasCapital, taxLocked));
        inventory.setItem(SLOT_TREASURY, treasuryItem(hasCapital));
        inventory.setItem(SLOT_SPIRIT, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT)
                .name(plugin.getMessages().component("gui.main.spirit.name", player))
                .lore(plugin.getMessages().component("gui.main.spirit.lore", player))
                .build());

        // Row 4 — trade and settings
        inventory.setItem(SLOT_TRADE, hasTerritory
                ? ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                        .name(plugin.getMessages().component("gui.main.trade.name", player))
                        .lore(plugin.getMessages().component("gui.main.trade.description", player))
                        .lore(Component.empty())
                        .lore(plugin.getMessages().component("gui.main.trade.action", player))
                        .build()
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.main.trade.name", player))
                        .lore(plugin.getMessages().component("gui.main.no-territory-lore", player))
                        .build());

        inventory.setItem(SLOT_MODIFIERS, ItemBuilder.of(Material.WRITTEN_BOOK)
                .name(Component.text("§6Модификаторы клана"))
                .lore(Component.text("§7Просмотр активных репараций, дани,"))
                .lore(Component.text("§7щитов от набегов и доступных казус белли."))
                .lore(Component.empty())
                .lore(Component.text("§eНажмите для открытия"))
                .build());

        ItemBuilder settingsItem = canManageSettings
                ? ItemBuilder.head(ItemBuilder.HEAD_MAIN_SETTINGS)
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE);
        settingsItem.name(plugin.getMessages().component("gui.main.settings.name", player))
                .lore(plugin.getMessages().component(canManageSettings ? "gui.main.settings.lore" : "gui.main.settings.no-permission-lore", player));
        inventory.setItem(SLOT_SETTINGS, settingsItem.build());

        // Footer — standalone menu: no Back button, so Leave Clan (with confirmation) takes slot 52 right before Close
        boolean isLeader = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER)
                .orElse(false);
        if (!isLeader) {
            inventory.setItem(SLOT_LEAVE, ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                    .name(plugin.getMessages().component("gui.main.leave.name", player))
                    .lore(plugin.getMessages().component("gui.main.leave.lore", player))
                    .build());
        }

        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack chestItem(boolean hasCapital, boolean taxLocked) {
        if (!hasCapital) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.items-button.name", player))
                    .lore(plugin.getMessages().component("gui.capital.no-house-lore", player))
                    .build();
        }
        if (taxLocked) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.locked-warning.name", player))
                    .lore(plugin.getMessages().components("gui.chest.locked-warning.lore", Map.of(), player))
                    .build();
        }
        return ItemBuilder.head(ItemBuilder.HEAD_CHEST)
                .name(plugin.getMessages().component("gui.chest.items-button.name", player))
                .lore(plugin.getMessages().component("gui.chest.items-button.lore", player))
                .lore(plugin.getMessages().component("gui.chest.info.rows",
                        Map.of("rows", String.valueOf(clan.chestRows()),
                               "max", String.valueOf(plugin.getClanManager().maxChestRows())), player))
                .lore(net.kyori.adventure.text.Component.empty())
                .lore(plugin.getMessages().component("gui.chest.open-action", player))
                .build();
    }

    /** Money of the clan chest plus the weekly tax status (what the old chest hub showed on its info head). */
    private ItemStack treasuryItem(boolean hasCapital) {
        if (!hasCapital) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.money-button.name", player))
                    .lore(plugin.getMessages().component("gui.capital.no-house-lore", player))
                    .build();
        }
        ItemBuilder item = ItemBuilder.head(clan.isChestTaxLocked() ? ItemBuilder.HEAD_CHEST_LOCKED : ItemBuilder.HEAD_CHEST_MONEY)
                .name(plugin.getMessages().component("gui.chest.money-button.name", player))
                .lore(plugin.getMessages().component("gui.chest.money-button.description", player))
                .lore(plugin.getMessages().component("gui.chest.money-button.lore",
                        Map.of("amount", CoinFormat.format(clan.chestMoney())), player));
        if (!plugin.getClanManager().isTaxApplicable(clan)) {
            item.lore(plugin.getMessages().component("gui.chest.info.tax-none", player));
        } else if (clan.isChestTaxLocked()) {
            item.lore(plugin.getMessages().component("gui.chest.info.tax-locked", player));
        } else {
            item.lore(plugin.getMessages().component("gui.chest.info.tax-ok", player));
            long remaining = clan.lastTaxAt() + java.time.Duration.ofDays(7).toMillis() - System.currentTimeMillis();
            item.lore(plugin.getMessages().component("gui.chest.info.next-tax",
                    Map.of("time", TimeUtil.formatDuration(Math.max(0, remaining))), player));
        }
        // The tax amount is only useful to whoever plans the clan's finances - BANK holders (the leader always).
        if (plugin.getClanManager().isTaxApplicable(clan) && clan.hasPermission(player.getUniqueId(), ClanPermission.BANK)) {
            item.lore(plugin.getMessages().component("gui.chest.info.tax-amount",
                    Map.of("amount", CoinFormat.format(plugin.getClanManager().weeklyChestTax(clan))), player));
        }
        item.lore(net.kyori.adventure.text.Component.empty())
                .lore(plugin.getMessages().component("gui.chest.open-action", player));
        return item.build();
    }

    public void handleInventoryClick(Player clicker, int slot) {
        switch (slot) {
            case 19 -> plugin.getGuiManager().openMembers(clicker, clan);
            case 21 -> {
                if (!clan.hasCapital()) {
                    plugin.getMessages().send(clicker, "clan.no-territory");
                } else if (clan.hasPermission(clicker.getUniqueId(), ClanPermission.DIPLOMACY)) {
                    plugin.getGuiManager().openDiplomacySelect(clicker, clan);
                } else {
                    plugin.getMessages().send(clicker, "general.no-permission");
                }
            }
            case 23 -> {
                // Клановый спавн — особый случай: даже без права CLAIM меню всё равно открывается,
                // но в режиме просмотра/телепортации (см. ClanCapitalManagementMenu.isManagement).
                if (plugin.getClanManager().inAnyConflict(clan.id())) {
                    plugin.getMessages().send(clicker, "gui.capital.war-blocked");
                    return;
                }
                plugin.getGuiManager().openClanCapitalManagementMenu(clicker, clan);
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
            case SLOT_SPIRIT -> plugin.getGuiManager().openSpiritMenu(clicker, clan);
            case SLOT_CHEST -> plugin.getGuiManager().openChestItems(clicker, clan);
            case SLOT_TREASURY -> plugin.getGuiManager().openChestMoney(clicker, clan);
            case SLOT_TRADE -> {
                if (!clan.hasCapital()) {
                    plugin.getMessages().send(clicker, "clan.no-territory");
                    return;
                }
                plugin.getGuiManager().openTrade(clicker, clan, null);
            }
            case SLOT_MODIFIERS -> plugin.getGuiManager().openModifiers(clicker, clan);
            case SLOT_SETTINGS -> {
                if (clan.hasPermission(clicker.getUniqueId(), ClanPermission.SETTINGS)) {
                    plugin.getGuiManager().openSettings(clicker, clan);
                } else {
                    plugin.getMessages().send(clicker, "general.no-permission");
                }
            }
            case SLOT_CLOSE -> clicker.closeInventory();
            case SLOT_LEAVE -> {
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
