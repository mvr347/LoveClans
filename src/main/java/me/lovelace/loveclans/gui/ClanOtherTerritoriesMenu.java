package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
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

        int invSlot = 9; // Start from second row, first usable slot
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
            inventory.setItem(invSlot, territoryItem);
            invSlot++;
            if ((invSlot % 9 == 0) && (invSlot < 45)) { // Skip border slots
                invSlot += 2; // Move to next row, skipping the first two border slots
            }
        }

        // Navigation buttons
        if (page > 0) {
            inventory.setItem(45, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player))
                    .build());
        }
        if ((page + 1) * ITEMS_PER_PAGE < otherTerritories.size()) {
            inventory.setItem(53, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player))
                    .build());
        }

        // Back button
        inventory.setItem(49, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        boolean canUnclaim = clan.member(clicker.getUniqueId())
                .map(member -> member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN)
                .orElse(false);

        if (slot == 49) { // Back button
            plugin.getGuiManager().openClanTerritoriesMenu(clicker, clan);
            return;
        }

        if (slot == 45 && page > 0) { // Previous page
            page--;
            open();
            return;
        }

        if (slot == 53 && (page + 1) * ITEMS_PER_PAGE < otherTerritories.size()) { // Next page
            page++;
            open();
            return;
        }

        if (slot >= 9 && slot < 45 && (slot % 9 != 0) && (slot % 9 != 8)) { // Territory item slots
            int index = page * ITEMS_PER_PAGE + (slot - 9 - (slot / 9) * 2); // Adjust for border slots
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
                    plugin.getMessages().send(clicker, "territory.unclaim.no-permission");
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
