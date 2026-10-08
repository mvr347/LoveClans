package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanBannerCreationMenu;
import me.lovelace.loveclans.gui.ClanCapitalManagementMenu;
import me.lovelace.loveclans.gui.ClanChestMenu;
import me.lovelace.loveclans.gui.ClanChestMoneyMenu;
import me.lovelace.loveclans.gui.ClanColorPickerMenu;
import me.lovelace.loveclans.gui.ClanLettersMenu;
import me.lovelace.loveclans.gui.ClanConfirmMenu;
import me.lovelace.loveclans.gui.ClanContractChoiceMenu;
import me.lovelace.loveclans.gui.ClanContractsMenu;
import me.lovelace.loveclans.gui.ClanRecognitionConfirmMenu;
import me.lovelace.loveclans.gui.GuildmasterMenu;
import me.lovelace.loveclans.gui.ClanCreateMenu;
import me.lovelace.loveclans.gui.ClanDiplomacyMenu;
import me.lovelace.loveclans.gui.ClanRelationMenu;
import me.lovelace.loveclans.gui.ClanPerkMenu;
import me.lovelace.loveclans.gui.ClanDiplomacySelectMenu;
import me.lovelace.loveclans.gui.ClanInfoMenu;
import me.lovelace.loveclans.gui.ClanBannerCreationMenu;
import me.lovelace.loveclans.gui.ClanCreateMenu;
import me.lovelace.loveclans.gui.ClanListMenu;
import me.lovelace.loveclans.gui.ClanMainMenu;
import me.lovelace.loveclans.gui.ClanMemberDetailMenu;
import me.lovelace.loveclans.gui.ClanMembersMenu;
import me.lovelace.loveclans.gui.MembersView;
import me.lovelace.loveclans.gui.ClanMenuHolder;
import me.lovelace.loveclans.gui.ClanMenuType;
import me.lovelace.loveclans.gui.ClanRankPermissionsMenu;
import me.lovelace.loveclans.gui.ClanRoleSettingsMenu;
import me.lovelace.loveclans.gui.ClanSettingsMenu;
import me.lovelace.loveclans.gui.ClanSpiritAbilityMenu;
import me.lovelace.loveclans.gui.ClanSpiritMenu;
import me.lovelace.loveclans.gui.ClanTerritoriesMenu;
import me.lovelace.loveclans.gui.ClanTradeMenu;
import me.lovelace.loveclans.gui.ClanUpgradesMenu;
import me.lovelace.loveclans.gui.PlayerApplicationsMenu;
import me.lovelace.loveclans.gui.TerritorySettingsMenu;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private final LoveClansPlugin plugin;
    private final NamespacedKey memberKey;
    private final ClanDiplomacyMenu diplomacyMenu;
    private final ClanRelationMenu relationMenu;
    private final ClanPerkMenu perkMenu;
    private final ClanTerritoriesMenu territoriesMenu;
    private final ClanMembersMenu membersMenu;
    private final ClanUpgradesMenu upgradesMenu;
    private final ClanSettingsMenu settingsMenu;
    private final ClanConfirmMenu confirmMenu;
    private final ClanMemberDetailMenu memberDetailMenu;
    private final ClanColorPickerMenu colorPickerMenu;
    private final ClanRoleSettingsMenu roleSettingsMenu;
    private final ClanRankPermissionsMenu rankPermissionsMenu;
    private final ClanContractsMenu contractsMenu;
    private final ClanRecognitionConfirmMenu recognitionMenu;
    private final GuildmasterMenu guildmasterMenu;
    private final ClanChestMoneyMenu chestMoneyMenu;
    private final ClanLettersMenu lettersMenu;
    private final ClanTradeMenu tradeMenu;

    private final Map<UUID, Runnable> confirmYes = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> confirmNo = new ConcurrentHashMap<>();

    public GuiManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.memberKey = new NamespacedKey(plugin, "gui_member");
        this.diplomacyMenu = new ClanDiplomacyMenu(plugin);
        this.relationMenu = new ClanRelationMenu(plugin);
        this.perkMenu = new ClanPerkMenu(plugin);
        this.territoriesMenu = new ClanTerritoriesMenu(plugin);
        this.membersMenu = new ClanMembersMenu(plugin);
        this.upgradesMenu = new ClanUpgradesMenu(plugin);
        this.settingsMenu = new ClanSettingsMenu(plugin);
        this.confirmMenu = new ClanConfirmMenu(plugin);
        this.memberDetailMenu = new ClanMemberDetailMenu(plugin);
        this.colorPickerMenu = new ClanColorPickerMenu(plugin);
        this.roleSettingsMenu = new ClanRoleSettingsMenu(plugin);
        this.rankPermissionsMenu = new ClanRankPermissionsMenu(plugin);
        this.contractsMenu = new ClanContractsMenu(plugin);
        this.recognitionMenu = new ClanRecognitionConfirmMenu(plugin);
        this.guildmasterMenu = new GuildmasterMenu(plugin, recognitionMenu);
        this.chestMoneyMenu = new ClanChestMoneyMenu(plugin);
        this.lettersMenu = new ClanLettersMenu(plugin);
        this.tradeMenu = new ClanTradeMenu(plugin);
    }

    /** "Торговля": other clans, trade requests and state orders; {@code tab} null keeps the remembered tab. */
    public void openTrade(Player player, Clan clan, ClanTradeMenu.Tab tab) {
        if (!clan.hasCapital()) {
            plugin.getMessages().send(player, "clan.no-territory");
            return;
        }
        tradeMenu.open(player, clan, tab);
    }

    public void openTradeRequests(Player player, Clan clan) {
        openTrade(player, clan, ClanTradeMenu.Tab.REQUESTS);
    }

    /**
     * Entry point of the clan item storage ({@code /clan chest} and the main menu's "Сундук" button): the
     * authoritative "clan has a territory" and "tax is paid" checks live here, not in ClanChestMenu itself.
     */
    public void openChestItems(Player player, Clan clan) {
        if (!clan.hasCapital()) {
            plugin.getMessages().send(player, "chest.no-capital");
            return;
        }
        if (clan.isChestTaxLocked()) {
            plugin.getMessages().send(player, "chest.tax-locked");
            return;
        }
        player.closeInventory();
        ClanChestMenu.open(plugin, clan, player);
    }

    /** Money side of the chest ("Казна"); same territory guard as {@link #openChestItems}. */
    public void openChestMoney(Player player, Clan clan) {
        if (!clan.hasCapital()) {
            plugin.getMessages().send(player, "chest.no-capital");
            return;
        }
        chestMoneyMenu.open(player, clan);
    }

    public void openServerTrade(Player player, Clan clan) {
        openTrade(player, clan, ClanTradeMenu.Tab.STATE);
    }

    public void openLetters(Player player, Clan sourceClan, Clan targetClan) {
        lettersMenu.open(player, sourceClan, targetClan);
    }

    public void openContracts(Player player, Clan clan) {
        contractsMenu.open(player, clan);
    }

    public void openGuildmaster(Player player, Clan clan) {
        guildmasterSession.add(player.getUniqueId());
        guildmasterMenu.open(player, clan);
    }

    public NamespacedKey memberKey() {
        return memberKey;
    }

    // ── Main menu ──────────────────────────────────────────────────────────────

    /** Players who came through the Guildmaster NPC menu: their Back buttons lead there instead of the main menu. */
    private final java.util.Set<java.util.UUID> guildmasterSession = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean inGuildmasterSession(Player player) {
        return guildmasterSession.contains(player.getUniqueId());
    }

    public void endGuildmasterSession(Player player) {
        guildmasterSession.remove(player.getUniqueId());
    }

    public void openMain(Player player, Clan clan) {
        endGuildmasterSession(player);
        new ClanMainMenu(plugin, clan, player).open();
    }

    // Если у игрока сейчас открыто главное меню клана — перерисовываем его.
    // Нужно после действий, которые меняют права/ранг на лету (например, передача лидерства),
    // чтобы кнопки (например, «Покинуть клан») не оставались устаревшими после смены роли.
    public void refreshMainMenuIfOpen(Player player, Clan clan) {
        if (player == null || !player.isOnline()) return;
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof ClanMainMenu mainMenu && mainMenu.clan().id().equals(clan.id())) {
            openMain(player, clan);
        }
    }

    public void openMembers(Player player, Clan clan) {
        membersMenu.open(player, clan);
    }

    public void openUpgrades(Player player, Clan clan) {
        upgradesMenu.open(player, clan);
    }

    public void openPerks(Player player, Clan clan) {
        perkMenu.open(player, clan);
    }

    public void openSettings(Player player, Clan clan) {
        settingsMenu.open(player, clan);
    }

    /** {@code /clan applications}: the members screen, already filtered to the applications. */
    public void openApplications(Player player, Clan clan) {
        membersMenu.open(player, clan, MembersView.Filter.APPLICATIONS);
    }

    /** Clan list opened from the Guildmaster menu: keeps the session so the list gets a Back button. */
    public void openClanListFromGuildmaster(Player player) {
        openClanListInternal(player);
    }

    public void openClanList(Player player) {
        endGuildmasterSession(player);
        openClanListInternal(player);
    }

    private void openClanListInternal(Player player) {
        // Same guard as /clans: an empty list is a message, not an empty screen.
        if (plugin.getClanManager().getAllClans().isEmpty()) {
            plugin.getMessages().send(player, "clan.list.empty");
            return;
        }
        new ClanListMenu(plugin, player).open();
    }

    // ── Confirm dialog ─────────────────────────────────────────────────────────

    public void openConfirm(Player player, Clan clan, Component title, Component lore, Runnable onYes, Runnable onNo) {
        confirmYes.put(player.getUniqueId(), onYes);
        confirmNo.put(player.getUniqueId(), onNo);
        confirmMenu.open(player, clan, title, lore);
    }

    // ── Member management ──────────────────────────────────────────────────────

    public void openMemberDetail(Player player, Clan clan, UUID targetId) {
        memberDetailMenu.open(player, clan, targetId);
    }

    public void openColorPicker(Player player, Clan clan) {
        colorPickerMenu.open(player, clan);
    }

    public void openRoleSettings(Player player, Clan clan) {
        roleSettingsMenu.open(player, clan);
    }

    public void openRankPermissions(Player player, Clan clan, ClanRank rank) {
        rankPermissionsMenu.open(player, clan, rank);
    }

    // ── Territory menus ────────────────────────────────────────────────────────

    public void openSpiritMenu(Player player, Clan clan) {
        new ClanSpiritMenu(plugin, clan).open(player);
    }

    public void openClanCapitalManagementMenu(Player player, Clan clan) {
        new ClanCapitalManagementMenu(plugin, clan, player).open();
    }

    public void openTerritories(Player player, Clan clan) {
        territoriesMenu.open(player, clan);
    }

    public void openTerritorySettings(Player player, Clan clan, ClanTerritory territory) {
        new TerritorySettingsMenu(plugin, clan, territory).open(player);
    }

    // ── Diplomacy ──────────────────────────────────────────────────────────────

    public void openDiplomacy(Player player, Clan sourceClan, Clan targetClan) {
        diplomacyMenu.open(player, sourceClan, targetClan);
    }

    public void openRelations(Player player, Clan sourceClan, Clan targetClan) {
        relationMenu.open(player, sourceClan, targetClan);
    }

    public void openDiplomacySelect(Player player, Clan sourceClan) {
        if (!sourceClan.hasCapital()) {
            plugin.getMessages().send(player, "clan.no-territory");
            return;
        }
        ClanDiplomacySelectMenu menu = new ClanDiplomacySelectMenu(plugin, player, sourceClan);
        if (!menu.hasClans()) {
            plugin.getMessages().send(player, "diplomacy.no-other-clans");
            return;
        }
        menu.open();
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    public void clearPlayerCache(UUID playerId) {
        membersMenu.clearPlayer(playerId);
        tradeMenu.clearPlayer(playerId);
        ClanListMenu.clearPlayer(playerId);
        rankPermissionsMenu.clearPlayer(playerId);
        confirmYes.remove(playerId);
        confirmNo.remove(playerId);
    }

    // ── Click routing ──────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof ClanMainMenu mainMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            mainMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanCapitalManagementMenu capitalMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            capitalMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanSpiritMenu spiritMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            spiritMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanSpiritAbilityMenu spiritAbilityMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            spiritAbilityMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof TerritorySettingsMenu territorySettingsMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            territorySettingsMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanListMenu clanListMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            clanListMenu.handleInventoryClick(event.getRawSlot(), event.isRightClick());
            return;
        }

        if (holder instanceof ClanDiplomacySelectMenu diplomacySelectMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            diplomacySelectMenu.handleInventoryClick(event.getRawSlot(), event.isRightClick());
            return;
        }

        if (holder instanceof ClanCreateMenu createMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            createMenu.handleInventoryClick(event.getRawSlot());
            return;
        }

        if (holder instanceof ClanBannerCreationMenu bannerCreateMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            bannerCreateMenu.handleInventoryClick(event.getRawSlot());
            return;
        }

        if (holder instanceof PlayerApplicationsMenu playerApplicationsMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            playerApplicationsMenu.handleInventoryClick(event);
            return;
        }

        if (holder instanceof ClanInfoMenu infoMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            infoMenu.handleInventoryClick(event.getRawSlot());
            return;
        }

        if (holder instanceof ClanMenuHolder clanMenuHolder) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                // A click in the player's own inventory is harmless, except for the actions that move items
                // across: shift-click would push the item into the menu (and lose it on close), and
                // "collect to cursor" (double click) would pull the menu's own items out of it.
                InventoryAction action = event.getAction();
                if (event.isShiftClick() || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || action == InventoryAction.COLLECT_TO_CURSOR) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (clanMenuHolder.type() == ClanMenuType.CONFIRM) {
                // A click on the glass must not throw away the pending callbacks.
                if (!ClanConfirmMenu.isAnswerSlot(slot)) return;
                Runnable onYes = confirmYes.remove(player.getUniqueId());
                Runnable onNo = confirmNo.remove(player.getUniqueId());
                confirmMenu.handleInventoryClick(player, slot, onYes, onNo);
                return;
            }

            if (clanMenuHolder.type() == ClanMenuType.UPGRADES) {
                upgradesMenu.handleInventoryClick(player, slot);
                return;
            }

            plugin.getClanManager().getClanById(clanMenuHolder.clanId()).ifPresentOrElse(clan -> {
                switch (clanMenuHolder.type()) {
                    case MEMBERS -> {
                        if (holder instanceof ClanMembersMenu.Holder membersHolder) {
                            membersMenu.handleInventoryClick(event, player, clan, membersHolder);
                        }
                    }
                    case TERRITORIES -> territoriesMenu.handleTerritoryClick(player, clan, slot, event.isRightClick());
                    case SETTINGS -> settingsMenu.handleInventoryClick(player, clan, slot);
                    case DIPLOMACY -> diplomacyMenu.handleInventoryClick(player, clan, slot);
                    case RELATIONS -> relationMenu.handleInventoryClick(player, clan, slot);
                    case PERKS -> perkMenu.handleInventoryClick(player, clan, slot);
                    case MEMBER_DETAIL -> memberDetailMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case COLOR_PICKER -> colorPickerMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case ROLE_SETTINGS -> roleSettingsMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case RANK_PERMISSIONS -> rankPermissionsMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case CONTRACTS -> contractsMenu.handleInventoryClick(player, clan, slot);
                    case CONTRACT_CHOICE -> {
                        if (clanMenuHolder instanceof ClanContractChoiceMenu.Holder choiceHolder) {
                            contractsMenu.choiceMenu().handleInventoryClick(player, clan, slot, choiceHolder);
                        }
                    }
                    case GUILDMASTER -> {
                        if (clanMenuHolder instanceof GuildmasterMenu.Holder guildmasterHolder) {
                            guildmasterMenu.handleInventoryClick(player, clan, slot, guildmasterHolder);
                        }
                    }
                    case RECOGNITION_CONFIRM -> {
                        if (clanMenuHolder instanceof ClanRecognitionConfirmMenu.Holder recognitionHolder) {
                            recognitionMenu.handleInventoryClick(player, slot, recognitionHolder);
                        }
                    }
                    case LETTERS -> lettersMenu.handleInventoryClick(player, clan, slot);
                    case TRADE -> {
                        if (holder instanceof ClanTradeMenu.Holder tradeHolder) {
                            tradeMenu.handleInventoryClick(event, player, clan, tradeHolder);
                        }
                    }
                    default -> {
                    }
                }
            }, player::closeInventory);
        }
    }

    /** Dragging an item over a menu would drop it into the menu inventory, where it is lost on close. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
