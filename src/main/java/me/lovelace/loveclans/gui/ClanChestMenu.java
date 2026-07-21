package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Real, drag-and-drop clan item storage (§2.3 "Предметы"). Unlocked slots (the first
 * {@code chestRows()*9}) are live storage; further rows show a plain locked icon and can't hold
 * items - unlocking them is done via the "Сундук" upgrade (see ClanUpgradesMenu), not from here.
 * Self-registers as a listener scoped to its own inventory instance (mirrors
 * {@link ClanTerritoriesSelectionGui}), unregistering and persisting contents on close.
 */
public final class ClanChestMenu implements Listener {

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private final Inventory inventory;
    private final int unlockedSlots;

    private ClanChestMenu(LoveClansPlugin plugin, Clan clan, Player player, ItemStack[] contents) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
        this.unlockedSlots = Math.min(contents.length, clan.chestRows() * 9);
        this.inventory = Bukkit.createInventory(null, ClanManager.CHEST_MAX_SIZE,
                plugin.getMessages().component("gui.chest-items-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        inventory.setContents(contents);
        drawLockedSlots();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(LoveClansPlugin plugin, Clan clan, Player player) {
        plugin.getClanManager().loadChestContentsAsync(clan).thenAccept(contents ->
                plugin.runSync(() -> {
                    ClanChestMenu menu = new ClanChestMenu(plugin, clan, player, contents);
                    player.openInventory(menu.inventory);
                })
        ).exceptionally(throwable -> {
            plugin.runSync(() -> plugin.sendOperationError(player, throwable));
            return null;
        });
    }

    private void drawLockedSlots() {
        for (int slot = unlockedSlots; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.locked.name", player))
                    .lore(plugin.getMessages().component("gui.chest.locked.lore", player))
                    .build());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        if (event.getRawSlot() >= inventory.getSize()) {
            return; // clicks in the player's own inventory behave normally
        }
        if (event.getRawSlot() < unlockedSlots) {
            return; // real storage slot — allow normal item movement
        }
        event.setCancelled(true); // locked slot — can't hold items until the CHEST upgrade unlocks it
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        boolean touchesLocked = event.getRawSlots().stream()
                .anyMatch(slot -> slot < inventory.getSize() && slot >= unlockedSlots);
        if (touchesLocked) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        HandlerList.unregisterAll(this);
        ItemStack[] full = inventory.getContents();
        ItemStack[] toPersist = new ItemStack[full.length];
        System.arraycopy(full, 0, toPersist, 0, unlockedSlots);
        plugin.getClanManager().saveChestContentsAsync(clan.id(), toPersist);
    }
}
