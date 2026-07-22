package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanApplicationsMenu;
import me.lovelace.loveclans.gui.ClanCapitalManagementMenu;
import me.lovelace.loveclans.gui.ClanChestHubMenu;
import me.lovelace.loveclans.gui.ClanChestMoneyMenu;
import me.lovelace.loveclans.gui.ClanColorPickerMenu;
import me.lovelace.loveclans.gui.ClanLettersMenu;
import me.lovelace.loveclans.gui.ClanConfirmMenu;
import me.lovelace.loveclans.gui.ClanContractsMenu;
import me.lovelace.loveclans.gui.ClanCreateMenu;
import me.lovelace.loveclans.gui.ClanDiplomacyMenu;
import me.lovelace.loveclans.gui.ClanDiplomacySelectMenu;
import me.lovelace.loveclans.gui.ClanInfoMenu;
import me.lovelace.loveclans.gui.ClanListMenu;
import me.lovelace.loveclans.gui.ClanMainMenu;
import me.lovelace.loveclans.gui.ClanMemberDetailMenu;
import me.lovelace.loveclans.gui.ClanMembersMenu;
import me.lovelace.loveclans.gui.ClanMenuHolder;
import me.lovelace.loveclans.gui.ClanMenuType;
import me.lovelace.loveclans.gui.ClanOtherTerritoriesMenu;
import me.lovelace.loveclans.gui.ClanRankPermissionsMenu;
import me.lovelace.loveclans.gui.ClanRoleSettingsMenu;
import me.lovelace.loveclans.gui.ClanSettingsMenu;
import me.lovelace.loveclans.gui.ClanSpiritAbilityMenu;
import me.lovelace.loveclans.gui.ClanSpiritMenu;
import me.lovelace.loveclans.gui.ClanTerritoriesMenu;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager implements Listener {

    private final LoveClansPlugin plugin;
    private final NamespacedKey memberKey;
    private final ClanDiplomacyMenu diplomacyMenu;
    private final ClanTerritoriesMenu territoriesMenu;
    private final ClanMembersMenu membersMenu;
    private final ClanUpgradesMenu upgradesMenu;
    private final ClanSettingsMenu settingsMenu;
    private final ClanApplicationsMenu applicationsMenu;
    private final ClanConfirmMenu confirmMenu;
    private final ClanMemberDetailMenu memberDetailMenu;
    private final ClanColorPickerMenu colorPickerMenu;
    private final ClanRoleSettingsMenu roleSettingsMenu;
    private final ClanRankPermissionsMenu rankPermissionsMenu;
    private final ClanContractsMenu contractsMenu;
    private final ClanChestHubMenu chestHubMenu;
    private final ClanChestMoneyMenu chestMoneyMenu;
    private final ClanLettersMenu lettersMenu;

    private final Map<UUID, Runnable> confirmYes = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> confirmNo = new ConcurrentHashMap<>();

    public GuiManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.memberKey = new NamespacedKey(plugin, "gui_member");
        this.diplomacyMenu = new ClanDiplomacyMenu(plugin);
        this.territoriesMenu = new ClanTerritoriesMenu(plugin);
        this.membersMenu = new ClanMembersMenu(plugin);
        this.upgradesMenu = new ClanUpgradesMenu(plugin);
        this.settingsMenu = new ClanSettingsMenu(plugin);
        this.applicationsMenu = new ClanApplicationsMenu(plugin);
        this.confirmMenu = new ClanConfirmMenu(plugin);
        this.memberDetailMenu = new ClanMemberDetailMenu(plugin);
        this.colorPickerMenu = new ClanColorPickerMenu(plugin);
        this.roleSettingsMenu = new ClanRoleSettingsMenu(plugin);
        this.rankPermissionsMenu = new ClanRankPermissionsMenu(plugin);
        this.contractsMenu = new ClanContractsMenu(plugin);
        this.chestHubMenu = new ClanChestHubMenu(plugin);
        this.chestMoneyMenu = new ClanChestMoneyMenu(plugin);
        this.lettersMenu = new ClanLettersMenu(plugin);
    }

    public void openChestHub(Player player, Clan clan) {
        chestHubMenu.open(player, clan);
    }

    public void openChestMoney(Player player, Clan clan) {
        chestMoneyMenu.open(player, clan);
    }

    public void openLetters(Player player, Clan sourceClan, Clan targetClan) {
        lettersMenu.open(player, sourceClan, targetClan);
    }

    public void openContracts(Player player, Clan clan) {
        contractsMenu.open(player, clan);
    }

    public NamespacedKey memberKey() {
        return memberKey;
    }

    // ── Main menu ──────────────────────────────────────────────────────────────

    public void openMain(Player player, Clan clan) {
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

    public void openSettings(Player player, Clan clan) {
        settingsMenu.open(player, clan);
    }

    public void openApplications(Player player, Clan clan) {
        applicationsMenu.open(player, clan);
    }

    public void openClanList(Player player) {
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

    public void openClanOtherTerritoriesMenu(Player player, Clan clan) {
        new ClanOtherTerritoriesMenu(plugin, clan, player).open();
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

    public void openDiplomacySelect(Player player, Clan sourceClan) {
        ClanDiplomacySelectMenu menu = new ClanDiplomacySelectMenu(plugin, player, sourceClan);
        if (!menu.hasClans()) {
            plugin.getMessages().send(player, "diplomacy.no-other-clans");
            return;
        }
        menu.open();
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    public void clearPlayerCache(UUID playerId) {
        applicationsMenu.clearPlayer(playerId);
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

        if (holder instanceof ClanOtherTerritoriesMenu otherMenu) {
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            otherMenu.handleInventoryClick(player, event.getRawSlot());
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
            clanListMenu.handleInventoryClick(event.getRawSlot());
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
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (clanMenuHolder.type() == ClanMenuType.CONFIRM) {
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
                    case MEMBERS -> membersMenu.handleInventoryClick(player, clan, slot);
                    case TERRITORIES -> territoriesMenu.handleTerritoryClick(player, clan, slot, event.isRightClick());
                    case SETTINGS -> settingsMenu.handleInventoryClick(player, clan, slot);
                    case APPLICATIONS -> applicationsMenu.handleInventoryClick(event, player, clan);
                    case DIPLOMACY -> diplomacyMenu.handleInventoryClick(player, clan, slot);
                    case MEMBER_DETAIL -> memberDetailMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case COLOR_PICKER -> colorPickerMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case ROLE_SETTINGS -> roleSettingsMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case RANK_PERMISSIONS -> rankPermissionsMenu.handleInventoryClick(player, clan, slot, event.getCurrentItem());
                    case CONTRACTS -> contractsMenu.handleInventoryClick(player, clan, slot);
                    case CHEST_HUB -> chestHubMenu.handleInventoryClick(player, clan, slot);
                    case CHEST_MONEY -> chestMoneyMenu.handleInventoryClick(player, clan, slot);
                    case LETTERS -> lettersMenu.handleInventoryClick(player, clan, slot);
                    default -> {
                    }
                }
            }, player::closeInventory);
        }
    }
}
