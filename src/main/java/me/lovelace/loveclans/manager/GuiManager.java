package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.CampFortifyMenu;
import me.lovelace.loveclans.gui.ClanBannerCreationMenu;
import me.lovelace.loveclans.gui.ClanCapitalManagementMenu;
import me.lovelace.loveclans.gui.ClanChestMenu;
import me.lovelace.loveclans.gui.ClanChestMoneyMenu;
import me.lovelace.loveclans.gui.ClanColorPickerMenu;
import me.lovelace.loveclans.gui.ClanConflictMenu;
import me.lovelace.loveclans.gui.ClanGiftMenu;

import me.lovelace.loveclans.gui.ClanConfirmMenu;
import me.lovelace.loveclans.gui.ClanContractChoiceMenu;
import me.lovelace.loveclans.gui.ClanContractsMenu;
import me.lovelace.loveclans.gui.ClanRecognitionConfirmMenu;
import me.lovelace.loveclans.gui.GuildmasterMenu;
import me.lovelace.loveclans.gui.ClanCreateMenu;
import me.lovelace.loveclans.gui.ClanDiplomacyMenu;
import me.lovelace.loveclans.gui.ClanDirectionMenu;
import me.lovelace.loveclans.gui.ClanUpgradeOfferMenu;
import me.lovelace.loveclans.gui.ClanDiplomacySelectMenu;
import me.lovelace.loveclans.gui.ClanInfoMenu;
import me.lovelace.loveclans.gui.ClanListMenu;
import me.lovelace.loveclans.gui.ClanMainMenu;
import me.lovelace.loveclans.gui.ClanMemberDetailMenu;
import me.lovelace.loveclans.gui.ClanMembersMenu;
import me.lovelace.loveclans.gui.ClanActivityMenu;
import me.lovelace.loveclans.gui.ClanHistoryMenu;
import me.lovelace.loveclans.gui.ClanModifiersMenu;
import me.lovelace.loveclans.gui.CasusBelliMenu;
import me.lovelace.loveclans.gui.MembersView;
import me.lovelace.loveclans.gui.ClanMenuHolder;
import me.lovelace.loveclans.gui.ClanMenuType;
import me.lovelace.loveclans.gui.ClanRankPermissionsMenu;
import me.lovelace.loveclans.gui.ClanRoleSettingsMenu;
import me.lovelace.loveclans.gui.ClanSettingsMenu;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private final LoveClansPlugin plugin;
    private final NamespacedKey memberKey;
    private final ClanDiplomacyMenu diplomacyMenu;
    private final ClanDirectionMenu directionMenu;
    private final ClanUpgradeOfferMenu offerMenu;
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
    private final ClanTradeMenu tradeMenu;
    private final ClanModifiersMenu modifiersMenu;
    private final ClanActivityMenu activityMenu;
    private final ClanHistoryMenu historyMenu;
    private final CasusBelliMenu casusBelliMenu;
    private final CampFortifyMenu campFortifyMenu;

    private final Map<UUID, Runnable> confirmYes = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> confirmNo = new ConcurrentHashMap<>();

    public GuiManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.memberKey = new NamespacedKey(plugin, "gui_member");
        this.diplomacyMenu = new ClanDiplomacyMenu(plugin);
        this.directionMenu = new ClanDirectionMenu(plugin);
        this.offerMenu = new ClanUpgradeOfferMenu(plugin);
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
        this.tradeMenu = new ClanTradeMenu(plugin);
        this.modifiersMenu = new ClanModifiersMenu(plugin);
        this.activityMenu = new ClanActivityMenu(plugin);
        this.historyMenu = new ClanHistoryMenu(plugin);
        this.casusBelliMenu = new CasusBelliMenu(plugin);
        this.campFortifyMenu = new CampFortifyMenu(plugin);
    }

    public void openCampFortify(Player player, UUID siegeId, int campIndex) {
        campFortifyMenu.open(player, siegeId, campIndex);
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

    public void openActivity(Player player, Clan clan) {
        activityMenu.open(player, clan);
    }

    public void openHistory(Player player, Clan clan) {
        historyMenu.open(player, clan);
    }

    public void openModifiers(Player player, Clan clan) {
        modifiersMenu.open(player, clan);
    }

    public void openCasusBelli(Player player, Clan clan) {
        casusBelliMenu.open(player, clan);
    }

    /**
     * Entry point of the clan item storage ({@code /clan chest} and the main menu's "Сундук" button): the
     * authoritative "clan has a territory" and "tax is paid" checks live here, not in ClanChestMenu itself.
     */
    public void openChestItems(Player player, Clan clan) {
    if (plugin.getSiegeManager().isSettling(clan.id())) {
      plugin.getMessages().send(player, "siege.models.settling");
      return;
    }
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
    if (plugin.getSiegeManager().isSettling(clan.id())) {
      plugin.getMessages().send(player, "siege.models.settling");
      return;
    }
        if (!clan.hasCapital()) {
            plugin.getMessages().send(player, "chest.no-capital");
            return;
        }
        chestMoneyMenu.open(player, clan);
    }

    public void openServerTrade(Player player, Clan clan) {
        openTrade(player, clan, ClanTradeMenu.Tab.STATE);
    }

    public void openGiftMenu(Player player, Clan sourceClan, Clan targetClan) {
        new ClanGiftMenu(plugin, player, sourceClan, targetClan).open();
    }

    public void openConflict(Player player, Clan sourceClan, Clan targetClan, ClanDiplomacyMenu diplomacyMenu) {
        new ClanConflictMenu(plugin, player, sourceClan, targetClan, diplomacyMenu).open();
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

    public void openUpgradeOffer(Player player, Clan clan) {
        offerMenu.open(player, clan);
    }

    public void openDirection(Player player, Clan clan) {
        directionMenu.open(player, clan);
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

    /** Confirm dialog whose "confirm" button lists what will happen ({@link ClanConfirmMenu#effects}). */
    public void openConfirm(Player player, Clan clan, Component title, List<Component> effects, Runnable onYes, Runnable onNo) {
        confirmYes.put(player.getUniqueId(), onYes);
        confirmNo.put(player.getUniqueId(), onNo);
        confirmMenu.open(player, clan, title, effects);
    }

    /** Convenience: the effects of confirm kind {@code kind} straight from lang. */
    public List<Component> confirmEffects(Player player, String kind, Map<String, String> placeholders) {
        return ClanConfirmMenu.effects(plugin, player, kind, placeholders);
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
        openClanCapitalManagementMenu(player, clan);
    }

    public void openTerritorySettings(Player player, Clan clan, ClanTerritory territory) {
        new TerritorySettingsMenu(plugin, clan, territory).open(player);
    }

    // ── Diplomacy ──────────────────────────────────────────────────────────────

    public void openDiplomacy(Player player, Clan sourceClan, Clan targetClan) {
        diplomacyMenu.open(player, sourceClan, targetClan);
    }

    public void openRelations(Player player, Clan sourceClan, Clan targetClan) {
        openDiplomacy(player, sourceClan, targetClan);
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
        activityMenu.clear(playerId);
        historyMenu.clear(playerId);
        tradeMenu.clearPlayer(playerId);
        ClanListMenu.clearPlayer(playerId);
        rankPermissionsMenu.clearPlayer(playerId);
        confirmYes.remove(playerId);
        confirmNo.remove(playerId);
    }

    // ── Click routing ──────────────────────────────────────────────────────────

    /**
     * True when the click happened in the player's own inventory (the menu is not affected). Actions that would move
     * items across, shift-click and "collect to cursor" (double click), are cancelled: otherwise an item would slide
     * into a free menu slot and vanish on close, or the menu's own items could be pulled out.
     */
    private boolean isOwnInventoryClick(InventoryClickEvent event) {
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            return false;
        }
        InventoryAction action = event.getAction();
        if (event.isShiftClick() || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof ClanMainMenu mainMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            mainMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanCapitalManagementMenu capitalMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            capitalMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanSpiritMenu spiritMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            spiritMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanConflictMenu conflictMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            conflictMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanGiftMenu giftMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            giftMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof TerritorySettingsMenu territorySettingsMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            territorySettingsMenu.handleInventoryClick(player, event.getRawSlot());
            return;
        }

        if (holder instanceof ClanListMenu clanListMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            clanListMenu.handleInventoryClick(event.getRawSlot(), event.isRightClick());
            return;
        }

        if (holder instanceof ClanDiplomacySelectMenu diplomacySelectMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            diplomacySelectMenu.handleInventoryClick(event.getRawSlot(), event.isRightClick());
            return;
        }

        if (holder instanceof ClanCreateMenu createMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            createMenu.handleInventoryClick(event.getRawSlot());
            return;
        }

        if (holder instanceof ClanBannerCreationMenu bannerCreateMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            bannerCreateMenu.handleInventoryClick(event.getRawSlot());
            return;
        }

        if (holder instanceof PlayerApplicationsMenu playerApplicationsMenu) {
            if (isOwnInventoryClick(event)) return;
            event.setCancelled(true);
            playerApplicationsMenu.handleInventoryClick(event);
            return;
        }

        if (holder instanceof ClanInfoMenu infoMenu) {
            if (isOwnInventoryClick(event)) return;
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
                    case DIPLOMACY -> diplomacyMenu.handleInventoryClick(player, clan, slot, event.isRightClick());
                    case UPGRADE_OFFER -> {
                        if (clanMenuHolder instanceof ClanUpgradeOfferMenu.Holder offerHolder) {
                            offerMenu.handleInventoryClick(player, clan, slot, offerHolder);
                        }
                    }
                    case DIRECTION -> directionMenu.handleInventoryClick(player, clan, slot);
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

                    case TRADE -> {
                        if (holder instanceof ClanTradeMenu.Holder tradeHolder) {
                            tradeMenu.handleInventoryClick(event, player, clan, tradeHolder);
                        }
                    }
                    case MODIFIERS -> modifiersMenu.handleInventoryClick(player, clan, slot);
                    case CASUS_BELLI -> casusBelliMenu.handleInventoryClick(player, clan, slot);
                    case CAMP_FORTIFY -> campFortifyMenu.handleInventoryClick(player, slot);
                    case ACTIVITY -> activityMenu.handleInventoryClick(event, player, clan);
                    case HISTORY -> historyMenu.handleInventoryClick(event, player, clan);
                    default -> {
                    }
                }
            }, player::closeInventory);
        }
    }


    // ---- Menu sounds (sounds.gui-*) ----

    /** Tick of each player's last click in a menu; a menu opened in the same tick is navigation, not a fresh open. */
    private final Map<UUID, Integer> lastClickTick = new ConcurrentHashMap<>();

    private static String backTexture;
    private static String closeTexture;
    private static String inactiveTexture;

    /**
     * Menus made of buttons. Item storages (clan chest, trade item picker, raid loot, trade session, treasury) have
     * their own holders and are deliberately not listed: their slots hold real items, not buttons.
     */
    private static boolean isButtonMenu(InventoryHolder holder) {
        return holder instanceof ClanMenuHolder
                || holder instanceof ClanMainMenu
                || holder instanceof ClanCapitalManagementMenu
                || holder instanceof ClanSpiritMenu
                || holder instanceof ClanConflictMenu
                || holder instanceof ClanGiftMenu
                || holder instanceof TerritorySettingsMenu
                || holder instanceof ClanListMenu
                || holder instanceof ClanDiplomacySelectMenu
                || holder instanceof ClanCreateMenu
                || holder instanceof ClanBannerCreationMenu
                || holder instanceof PlayerApplicationsMenu
                || holder instanceof ClanInfoMenu;
    }

    /** True when the player currently looks at one of the button menus (used to voice errors only for GUI actions). */
    public boolean hasButtonMenuOpen(Player player) {
        return isButtonMenu(player.getOpenInventory().getTopInventory().getHolder());
    }

    /** Remembers the click moment before any menu handler runs, so a menu it opens is not announced as a fresh open. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClickMark(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isButtonMenu(event.getView().getTopInventory().getHolder())) {
            lastClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    /** Central click feedback: back/close/denied/plain click by what was clicked; glass and empty slots stay silent. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClickSound(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var top = event.getView().getTopInventory();
        if (!isButtonMenu(top.getHolder())) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;
        ItemStack item = event.getCurrentItem();
        boolean passive = item == null || item.getType().isAir() || item.getType().name().endsWith("GLASS_PANE");
        GuiSounds.Kind kind = passive ? null : classifyClicked(player, item);
        if (kind == null) return;
        if (plugin.getSoundService().guiClickSilenced(player)) return;
        plugin.getSoundService().playGui(player, kind);
    }

    private GuiSounds.Kind classifyClicked(Player player, ItemStack item) {
        if (backTexture == null) {
            backTexture = ItemBuilder.textureIdOf(ItemBuilder.HEAD_BACK);
            closeTexture = ItemBuilder.textureIdOf(ItemBuilder.HEAD_CLOSE);
            inactiveTexture = ItemBuilder.textureIdOf(ItemBuilder.HEAD_INACTIVE);
        }
        String texture = null;
        String name = "";
        if (item.getItemMeta() instanceof SkullMeta skull) {
            var profile = skull.getOwnerProfile();
            if (profile != null && profile.getTextures().getSkin() != null) {
                texture = GuiSounds.textureId(profile.getTextures().getSkin().toString());
            }
        }
        var display = item.getItemMeta() == null ? null : item.getItemMeta().displayName();
        if (display != null) name = PlainTextComponentSerializer.plainText().serialize(display);
        boolean backNamed = name.equals(plainName("gui.back", player));
        boolean closeNamed = name.equals(plainName("gui.close", player));
        return GuiSounds.classify(false, texture, backTexture, closeTexture, inactiveTexture, backNamed, closeNamed);
    }

    private String plainName(String key, Player player) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.getMessages().component(key, player));
    }

    /** Opening a menu from a command or an item makes the open sound; menu-to-menu navigation is covered by the click. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuOpenSound(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isButtonMenu(event.getInventory().getHolder())) return;
        Integer clicked = lastClickTick.get(player.getUniqueId());
        if (clicked != null && clicked == Bukkit.getCurrentTick()) return;
        if (plugin.getSoundService().guiOpenSilenced(player)) return;
        plugin.getSoundService().playGui(player, GuiSounds.Kind.OPEN);
    }

    @EventHandler
    public void onSoundStateQuit(PlayerQuitEvent event) {
        lastClickTick.remove(event.getPlayer().getUniqueId());
        plugin.getSoundService().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDiplomacyClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanMenuHolder h) || h.type() != me.lovelace.loveclans.gui.ClanMenuType.DIPLOMACY) return;
        Player player = (Player) event.getPlayer();
        // A refresh re-opens the menu right after this event with a freshly stored layout: only forget
        // when, a tick later, the player no longer has a diplomacy menu open.
        Bukkit.getScheduler().runTask(plugin, () -> {
            InventoryHolder now = player.isOnline() ? player.getOpenInventory().getTopInventory().getHolder() : null;
            if (!(now instanceof ClanMenuHolder nh && nh.type() == me.lovelace.loveclans.gui.ClanMenuType.DIPLOMACY)) {
                diplomacyMenu.forget(player.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onDiplomacyQuit(PlayerQuitEvent event) {
        diplomacyMenu.forget(event.getPlayer().getUniqueId());
    }

    /** Dragging an item over a menu would drop it into the menu inventory, where it is lost on close. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ClanMenuHolder
                || holder instanceof ClanMainMenu
                || holder instanceof ClanCapitalManagementMenu
                || holder instanceof ClanSpiritMenu
                || holder instanceof ClanConflictMenu
                || holder instanceof ClanGiftMenu
                || holder instanceof me.lovelace.loveclans.gui.ClanCreateMenu
                || holder instanceof me.lovelace.loveclans.gui.ClanBannerCreationMenu
                || holder instanceof me.lovelace.loveclans.gui.ClanDiplomacySelectMenu
                || holder instanceof me.lovelace.loveclans.gui.PlayerApplicationsMenu
                || holder instanceof me.lovelace.loveclans.gui.ClanInfoMenu
                || holder instanceof me.lovelace.loveclans.gui.ClanListMenu) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
