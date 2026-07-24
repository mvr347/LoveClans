package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Клановый дом (бывшая «Столица») — открывается напрямую из главного меню клана.
 * Если столица ещё не создана, вместо управления показывается получение баннера
 * (перенесено из бывшего ClanTerritoriesSelectionGui, который эта менюха заменила).
 */
public class ClanCapitalManagementMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;

    public ClanCapitalManagementMenu(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.capital.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        boolean isAtWar = plugin.getClanManager().inAnyConflict(clan.id());
        boolean isLeader = clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.LEADER).orElse(false);
        boolean isAssistant = clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.GUARDIAN).orElse(false);
        boolean canManage = isLeader || isAssistant;
        // Право CLAIM (настраиваемое per-ранг) даёт доступ к получению/установке баннера клан. дома,
        // отдельно от canManage (жёстко LEADER/GUARDIAN), которое требуется для сноса/переноса —
        // то же разделение прав, что было в ClanTerritoriesSelectionGui.
        boolean isManagement = clan.hasPermission(player.getUniqueId(), ClanPermission.CLAIM);

        Optional<ClanTerritory> capitalTerritoryOpt = clan.territories().stream().filter(ClanTerritory::isCapital).findFirst();

        if (capitalTerritoryOpt.isPresent()) {
            // Slot 10: Move Home Point
            ItemStack moveHomeItem;
            if (!canManage) {
                moveHomeItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.move-home.name", player))
                        .lore(plugin.getMessages().component("gui.capital.no-permission-lore", player))
                        .build();
            } else if (isAtWar) {
                moveHomeItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.move-home.name", player))
                        .lore(plugin.getMessages().component("gui.capital.war-blocked", player))
                        .build();
            } else {
                moveHomeItem = ItemBuilder.of(Material.COMPASS)
                        .name(plugin.getMessages().component("gui.capital.move-home.name", player))
                        .lore(plugin.getMessages().component("gui.capital.move-home.lore", player))
                        .build();
            }
            inventory.setItem(10, moveHomeItem);

            // Slot 12: Relocate Capital Territory
            ItemStack relocateItem;
            if (!canManage) {
                relocateItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.relocate-territory.name", player))
                        .lore(plugin.getMessages().component("gui.capital.no-permission-lore", player))
                        .build();
            } else if (isAtWar) {
                relocateItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.relocate-territory.name", player))
                        .lore(plugin.getMessages().component("gui.capital.war-blocked", player))
                        .build();
            } else {
                relocateItem = ItemBuilder.of(Material.ENDER_CHEST)
                        .name(plugin.getMessages().component("gui.capital.relocate-territory.name", player))
                        .lore(plugin.getMessages().component("gui.capital.relocate-territory.lore", player))
                        .build();
            }
            inventory.setItem(12, relocateItem);

            // Slot 14: Disband Capital Base
            ItemStack disbandItem;
            if (!canManage) {
                disbandItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.disband.name", player))
                        .lore(plugin.getMessages().component("gui.capital.no-permission-lore", player))
                        .build();
            } else if (isAtWar) {
                disbandItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.capital.disband.name", player))
                        .lore(plugin.getMessages().component("gui.capital.war-blocked", player))
                        .build();
            } else {
                disbandItem = ItemBuilder.of(Material.LAVA_BUCKET)
                        .name(plugin.getMessages().component("gui.capital.disband.name", player))
                        .lore(plugin.getMessages().component("gui.capital.disband.lore", player))
                        .build();
            }
            inventory.setItem(14, disbandItem);
        } else {
            // Столица (клан. дом) ещё не создана — показываем получение/установку баннера,
            // как раньше делал ClanTerritoriesSelectionGui. Слоты 10/12/14 (перенос спавна/переезд/
            // роспуск) неприменимы без дома — показываем их неактивными с причиной, а не голым
            // стеклом, как остальные неактивные кнопки в этом меню.
            ItemStack bannerItem;
            if (isManagement) {
                boolean hasBanner = plugin.getClanManager().getClanItemFactory().hasExistingBanner(player, "CAPITAL", clan.id());
                if (hasBanner) {
                    bannerItem = ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                            .name(plugin.getMessages().component("gui.territories.capital.already-have-banner-item", player))
                            .lore(plugin.getMessages().component("gui.territories.capital.place-it", player))
                            .build();
                } else {
                    bannerItem = ItemBuilder.of(Material.RED_BANNER)
                            .name(plugin.getMessages().component("gui.territories.capital.get-banner", player))
                            .lore(plugin.getMessages().component("gui.territories.capital.get-banner-lore", player))
                            .build();
                }
            } else {
                bannerItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.territories.capital.not-created", player))
                        .lore(plugin.getMessages().component("gui.territories.capital.no-permission-management", player))
                        .build();
            }
            inventory.setItem(13, bannerItem);

            inventory.setItem(10, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.capital.move-home.name", player))
                    .lore(plugin.getMessages().component("gui.capital.no-house-lore", player))
                    .build());
            inventory.setItem(12, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.capital.relocate-territory.name", player))
                    .lore(plugin.getMessages().component("gui.capital.no-house-lore", player))
                    .build());
            inventory.setItem(14, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.capital.disband.name", player))
                    .lore(plugin.getMessages().component("gui.capital.no-house-lore", player))
                    .build());
        }

        // Slot 16: Остальные территории клана — всегда доступно для просмотра
        boolean hasOtherTerritories = clan.territories().stream().anyMatch(t -> !t.isCapital());
        inventory.setItem(16, ItemBuilder.head(hasOtherTerritories ? ItemBuilder.HEAD_MORE_TERRITORIES : ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component("gui.territories.other.name", player))
                .lore(plugin.getMessages().component(hasOtherTerritories ? "gui.territories.other.info" : "gui.territories.other.none-lore", player))
                .build());

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        boolean isAtWar = plugin.getClanManager().inAnyConflict(clan.id());
        boolean isLeader = clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.LEADER).orElse(false);
        boolean isAssistant = clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.GUARDIAN).orElse(false);
        boolean canManage = isLeader || isAssistant;
        boolean isManagement = clan.hasPermission(clicker.getUniqueId(), ClanPermission.CLAIM);
        boolean hasCapital = clan.territories().stream().anyMatch(ClanTerritory::isCapital);

        switch (slot) {
            case 25: // Back button — клан. дом теперь открывается напрямую из главного меню
                plugin.getGuiManager().openMain(clicker, clan);
                break;
            case 26: // Close button
                clicker.closeInventory();
                break;
            case 16: // Остальные территории клана
                plugin.getGuiManager().openClanOtherTerritoriesMenu(clicker, clan);
                break;
            case 13: // Получить/установить баннер клан. дома (только если столица ещё не создана)
                if (hasCapital || !isManagement) return;
                ItemStack clicked = clicker.getOpenInventory().getTopInventory().getItem(13);
                if (clicked != null && clicked.getType() == Material.RED_BANNER) {
                    boolean hasBanner = plugin.getClanManager().getClanItemFactory().hasExistingBanner(clicker, "CAPITAL", clan.id());
                    if (hasBanner) {
                        plugin.getMessages().send(clicker, "gui.territories.capital.already-have-banner");
                    } else {
                        ItemStack capitalBanner = plugin.getClanManager().getClanItemFactory().createCapitalBanner(clan.id(), clan.name());
                        clicker.getInventory().addItem(capitalBanner);
                        plugin.getMessages().send(clicker, "territory.banner-given");
                        clicker.closeInventory();
                    }
                }
                break;
            case 10: // Relocate clan spawn
                if (!hasCapital) return;
                if (!canManage) {
                    plugin.getMessages().send(clicker, "gui.capital.no-permission");
                    return;
                }
                clicker.closeInventory();
                Location target = clicker.getLocation();
                plugin.getMessages().sendChatConfirmPrompt(clicker, "gui.capital.move-home.confirm-prompt", Map.of(),
                        () -> plugin.getClanManager().relocateHomeAsync(clan, clicker.getUniqueId(), target)
                                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(clicker, "gui.capital.move-home.action")))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.getMessages().send(clicker, "general.chat-input-cancelled"));
                break;
            case 12: // Relocate Capital Territory
                if (!hasCapital) return;
                if (!canManage) {
                    plugin.getMessages().send(clicker, "gui.capital.no-permission");
                    return;
                }
                if (isAtWar) {
                    plugin.getMessages().send(clicker, "gui.capital.war-blocked");
                    return;
                }
                clicker.closeInventory();
                plugin.getMessages().sendChatConfirmPrompt(clicker, "gui.capital.relocate-territory.confirm-prompt", Map.of(),
                        () -> plugin.getClanManager().relocateCapitalTerritoryAsync(clan, clicker.getUniqueId())
                                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(clicker, "gui.capital.relocate-territory.action")))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.getMessages().send(clicker, "general.chat-input-cancelled"));
                break;
            case 14: // Disband Capital Base
                if (!hasCapital) return;
                if (!canManage) {
                    plugin.getMessages().send(clicker, "gui.capital.no-permission");
                    return;
                }
                if (isAtWar) {
                    plugin.getMessages().send(clicker, "gui.capital.war-blocked");
                    return;
                }
                plugin.getGuiManager().openConfirm(clicker, clan,
                        plugin.getMessages().component("gui.confirm.disband-capital.title", clicker), Component.empty(),
                        () -> plugin.getClanManager().relocateCapitalTerritoryAsync(clan, clicker.getUniqueId())
                                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(clicker, "gui.capital.disband.action")))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.runSync(() -> open())
                );
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}