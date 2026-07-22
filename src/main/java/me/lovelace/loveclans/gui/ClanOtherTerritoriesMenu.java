package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClanOtherTerritoriesMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;
    private int page = 0;
    private final List<ClanTerritory> otherTerritories;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows of 7 slots (excluding borders)
    // Framed content grid — columns 0 and 8 of each row stay reserved for the border/pagination.
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public ClanOtherTerritoriesMenu(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
        this.otherTerritories = clan.territories().stream()
                .filter(t -> clan.getCapitalTerritory().isEmpty() || !t.advancedClaimId().equals(clan.getCapitalTerritory().get().advancedClaimId()))
                .sorted(Comparator.comparing(ClanTerritory::claimedAt))
                .collect(Collectors.toList());
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessages().component("gui.other-territories.title", Map.of("clan", clan.name()), player));

        // Fill background
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Display territories for current page
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, otherTerritories.size());

        for (int i = start; i < end; i++) {
            ClanTerritory territory = otherTerritories.get(i);
            ItemStack territoryItem = ItemBuilder.of(Material.GRASS_BLOCK)
                    .name(plugin.getMessages().component("gui.other-territories.item.name", Map.of(
                            "x", String.valueOf(territory.key().chunkX() * 16),
                            "z", String.valueOf(territory.key().chunkZ() * 16)
                    ), player))
                    .lore(plugin.getMessages().component("gui.other-territories.item.world", Map.of("world", territory.key().world()), player))
                    .lore(plugin.getMessages().component("gui.other-territories.item.claimed-at", Map.of("date", plugin.getMessages().formatDate(territory.claimedAt())), player))
                    .lore(plugin.getMessages().component("gui.other-territories.item.unclaim-hint", player))
                    .build();
            inventory.setItem(CONTENT_SLOTS[i - start], territoryItem);
        }

        // Pagination — standard 54-slot slots (36 = Previous, 44 = Next)
        if (page > 0) {
            inventory.setItem(36, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player))
                    .build());
        }
        if ((page + 1) * ITEMS_PER_PAGE < otherTerritories.size()) {
            inventory.setItem(44, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player))
                    .build());
        }

        inventory.setItem(52, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(53, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        boolean canUnclaim = clan.hasPermission(clicker.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.CLAIM);

        if (slot == 53) { // Close button
            clicker.closeInventory();
            return;
        }

        if (slot == 52) { // Back button — родитель теперь клановый дом, а не отдельный хаб
            plugin.getGuiManager().openClanCapitalManagementMenu(clicker, clan);
            return;
        }

        if (slot == 36 && page > 0) { // Previous page
            page--;
            open();
            return;
        }

        if (slot == 44 && (page + 1) * ITEMS_PER_PAGE < otherTerritories.size()) { // Next page
            page++;
            open();
            return;
        }

        int contentIndex = -1;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) { contentIndex = i; break; }
        }
        if (contentIndex >= 0) { // Territory item slots
            int index = page * ITEMS_PER_PAGE + contentIndex;
            if (index >= 0 && index < otherTerritories.size()) {
                ClanTerritory clickedTerritory = otherTerritories.get(index);
                if (canUnclaim) {
                    plugin.getClanManager().unclaimTerritoryAsync(clan, clickedTerritory.key(), clicker.getUniqueId())
                            .thenRun(() -> {
                                plugin.getMessages().send(clicker, "territory.unclaimed-success", Map.of(
                                        "x", String.valueOf(clickedTerritory.key().chunkX() * 16),
                                        "z", String.valueOf(clickedTerritory.key().chunkZ() * 16)
                                ));
                                plugin.getGuiManager().openClanOtherTerritoriesMenu(clicker, clan); // Refresh GUI
                            })
                            .exceptionally(ex -> {
                                plugin.getMessages().send(clicker, "general.error", Map.of("error", ex.getMessage()));
                                return null;
                            });
                } else {
                    // Без права CLAIM снять захват нельзя — вместо этого телепортируем
                    // игрока на территорию, чтобы список оставался полезным для просмотра.
                    teleportToTerritory(clicker, clickedTerritory);
                }
            }
        }
    }

    // Телепортирует игрока в центр чанка территории на безопасную высоту —
    // используется для игроков без права CLAIM, которым доступен только просмотр списка.
    private void teleportToTerritory(Player clicker, ClanTerritory territory) {
        org.bukkit.World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            plugin.getMessages().send(clicker, "territory.world-not-found");
            return;
        }
        int centerX = (territory.minX() + territory.maxX()) / 2;
        int centerZ = (territory.minZ() + territory.maxZ()) / 2;
        int safeY = world.getHighestBlockYAt(centerX, centerZ) + 1;
        org.bukkit.Location location = new org.bukkit.Location(world, centerX + 0.5, safeY, centerZ + 0.5);
        clicker.closeInventory();
        clicker.teleportAsync(location)
                .thenRun(() -> plugin.getMessages().send(clicker, "territory.teleported"))
                .exceptionally(throwable -> {
                    plugin.sendOperationError(clicker, throwable);
                    return null;
                });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
