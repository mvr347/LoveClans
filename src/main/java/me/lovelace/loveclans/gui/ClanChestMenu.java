package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
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

import java.util.List;
import java.util.Map;

/**
 * Real, drag-and-drop clan storage chest. Unlocked slots (the first {@code chestRows()*9}) are
 * live storage; the immediate next locked row shows a purchase prompt, further locked rows show
 * a plain locked icon — none of the locked slots can hold items until unlocked. Self-registers as
 * a listener scoped to its own inventory instance (mirrors {@link ClanTerritoriesSelectionGui}),
 * unregistering and persisting the storage-region contents on close.
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
                plugin.getMessages().component("gui.chest-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
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
        int nextRowEnd = Math.min(inventory.getSize(), unlockedSlots + 9);
        long cost = plugin.getClanManager().nextChestRowCost(clan);

        for (int slot = unlockedSlots; slot < inventory.getSize(); slot++) {
            if (slot < nextRowEnd && cost >= 0) {
                inventory.setItem(slot, ItemBuilder.head(ItemBuilder.HEAD_EXPAND)
                        .name(plugin.getMessages().component("gui.chest.unlock-row.name", player))
                        .lore(costLore(cost))
                        .build());
            } else {
                inventory.setItem(slot, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.chest.locked.name", player))
                        .build());
            }
        }
    }

    private List<Component> costLore(long cost) {
        return plugin.getMessages().components("gui.chest.unlock-row.lore", Map.of("cost", String.valueOf(cost)), player);
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
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        attemptPurchase();
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

    private void attemptPurchase() {
        plugin.getClanManager().purchaseChestRowAsync(clan, player.getUniqueId())
                .thenAccept(updated -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "chest.row-purchased", Map.of(
                            "rows", String.valueOf(updated.chestRows()),
                            "max", String.valueOf(plugin.getClanManager().maxChestRows())));
                    player.closeInventory();
                    open(plugin, updated, player);
                }))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
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
