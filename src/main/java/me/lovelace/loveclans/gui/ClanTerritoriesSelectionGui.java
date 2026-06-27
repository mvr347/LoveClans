package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ClanTerritoriesSelectionGui implements Listener {

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private final Inventory inventory;

    public ClanTerritoriesSelectionGui(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 3 * 9,
                plugin.getMessages().component("gui.territories-selection-title", Map.of("clan", clan.name()), player));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setupItems();
    }

    private void setupItems() {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        boolean isManagement = clan.hasPermission(player.getUniqueId(), ClanPermission.CLAIM);

        ItemStack capitalItem;
        if (clan.getCapitalTerritory().isPresent()) {
            if (isManagement) {
                capitalItem = ItemBuilder.head(ItemBuilder.HEAD_CAPITAL)
                        .name(plugin.getMessages().component("gui.territories.capital.name"))
                        .lore(List.of(
                                plugin.getMessages().component("gui.territories.capital.info.coords", Map.of(
                                        "x", String.valueOf(clan.getCapitalTerritory().get().bannerX() != null ? clan.getCapitalTerritory().get().bannerX() : 0),
                                        "y", String.valueOf(clan.getCapitalTerritory().get().bannerY() != null ? clan.getCapitalTerritory().get().bannerY() : 0),
                                        "z", String.valueOf(clan.getCapitalTerritory().get().bannerZ() != null ? clan.getCapitalTerritory().get().bannerZ() : 0)
                                )),
                                plugin.getMessages().component("gui.territories.capital.info.created", Map.of(
                                        "date", plugin.getMessages().formatDate(clan.getCapitalTerritory().get().claimedAt())
                                ))
                        ))
                        .build();
            } else {
                capitalItem = ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.territories.capital.name"))
                        .lore(List.of(plugin.getMessages().component("gui.territories.capital.no-permission-management")))
                        .build();
            }
        } else {
            if (isManagement) {
                boolean hasBanner = plugin.getClanManager().getClanItemFactory().hasExistingBanner(player, "CAPITAL", clan.id());
                if (hasBanner) {
                    capitalItem = ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                            .name(plugin.getMessages().component("gui.territories.capital.already-have-banner"))
                            .lore(List.of(plugin.getMessages().component("gui.territories.capital.place-it")))
                            .build();
                } else {
                    capitalItem = ItemBuilder.of(Material.RED_BANNER)
                            .name(plugin.getMessages().component("gui.territories.capital.get-banner"))
                            .lore(List.of(plugin.getMessages().component("gui.territories.capital.get-banner-lore")))
                            .build();
                }
            } else {
                capitalItem = ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                        .name(plugin.getMessages().component("gui.territories.capital.not-created"))
                        .lore(List.of(plugin.getMessages().component("gui.territories.capital.no-permission-management")))
                        .build();
            }
        }
        inventory.setItem(11, capitalItem);

        ItemStack otherTerritoriesItem;
        if (clan.territories().stream().anyMatch(t -> !t.isCapital())) {
            otherTerritoriesItem = ItemBuilder.head(ItemBuilder.HEAD_MORE_TERRITORIES)
                    .name(plugin.getMessages().component("gui.territories.other.name"))
                    .lore(List.of(plugin.getMessages().component("gui.territories.other.info")))
                    .build();
        } else {
            otherTerritoriesItem = ItemBuilder.head(ItemBuilder.HEAD_LEAVE_CLAN)
                    .name(plugin.getMessages().component("gui.territories.other.none"))
                    .lore(List.of(plugin.getMessages().component("gui.territories.other.none-lore")))
                    .build();
        }
        inventory.setItem(15, otherTerritoriesItem);

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
    }

    public void open() {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();

        boolean isManagement = clan.hasPermission(player.getUniqueId(), ClanPermission.CLAIM);

        if (slot == 22) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }

        if (slot == 11) {
            if (isManagement) {
                if (clan.getCapitalTerritory().isPresent()) {
                    plugin.getGuiManager().openClanCapitalManagementMenu(player, clan);
                } else {
                    if (clickedItem.getType() == Material.RED_BANNER) {
                        boolean hasBanner = plugin.getClanManager().getClanItemFactory().hasExistingBanner(player, "CAPITAL", clan.id());
                        if (hasBanner) {
                            plugin.getMessages().send(player, "gui.territories.capital.already-have-banner");
                        } else {
                            ItemStack capitalBanner = plugin.getClanManager().getClanItemFactory().createCapitalBanner(clan.id(), clan.name());
                            player.getInventory().addItem(capitalBanner);
                            plugin.getMessages().send(player, "territory.banner-given");
                            player.closeInventory();
                        }
                    }
                }
            } else if (clan.getCapitalTerritory().isPresent()) {
                // Без права CLAIM управление недоступно, но столицу можно посетить —
                // телепортируем к баннеру столицы, если его координаты известны.
                teleportToTerritory(clan.getCapitalTerritory().get());
            } else {
                plugin.getMessages().send(player, "gui.territories.capital.no-permission-management");
            }
        } else if (slot == 15) {
            if (clan.territories().stream().anyMatch(t -> !t.isCapital())) {
                plugin.getGuiManager().openClanOtherTerritoriesMenu(player, clan);
            } else {
                plugin.getMessages().send(player, "gui.territories.other.no-territories");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    // Телепортирует игрока к баннеру территории (если координаты известны), иначе — в центр чанка
    // на безопасную высоту. Используется как замена управлению для игроков без права CLAIM.
    private void teleportToTerritory(me.lovelace.loveclans.model.ClanTerritory territory) {
        org.bukkit.World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            plugin.getMessages().send(player, "territory.world-not-found");
            return;
        }
        org.bukkit.Location location;
        if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
            location = new org.bukkit.Location(world, territory.bannerX() + 0.5, territory.bannerY() + 1, territory.bannerZ() + 0.5);
        } else {
            int centerX = (territory.minX() + territory.maxX()) / 2;
            int centerZ = (territory.minZ() + territory.maxZ()) / 2;
            int safeY = world.getHighestBlockYAt(centerX, centerZ) + 1;
            location = new org.bukkit.Location(world, centerX + 0.5, safeY, centerZ + 0.5);
        }
        player.closeInventory();
        player.teleportAsync(location)
                .thenRun(() -> plugin.getMessages().send(player, "territory.teleported"))
                .exceptionally(throwable -> {
                    plugin.sendOperationError(player, throwable);
                    return null;
                });
    }
}
