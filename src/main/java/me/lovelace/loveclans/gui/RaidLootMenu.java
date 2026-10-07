package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.raid.ClanRaid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Item side of an active raid's loot (§3.1): shows the defender's current chest storage to an
 * attacking clan member. Taking an item is allowed until the raid's item-slot cap
 * (ClanRaid#itemSlotsRemaining) is used up, tracked in real time - once a full slot is emptied it
 * counts against the cap immediately, further full slots beyond the cap can't be touched.
 * Persists the defender's chest with whatever was taken removed when closed.
 */
public final class RaidLootMenu implements Listener {
    private final LoveClansPlugin plugin;
    private final Clan defender;
    private final Player looter;
    private final UUID raidId;
    private final Inventory inventory;
    private final int unlockedSlots;

    private RaidLootMenu(LoveClansPlugin plugin, Clan defender, Player looter, UUID raidId, ItemStack[] contents) {
        this.plugin = plugin;
        this.defender = defender;
        this.looter = looter;
        this.raidId = raidId;
        this.unlockedSlots = ChestLayout.unlockedSlots(defender.chestRows(), contents.length);
        this.inventory = Bukkit.createInventory(null, ClanManager.CHEST_MAX_SIZE,
                plugin.getMessages().component("raid.loot.title", Map.of("tag", defender.tag(), "color", defender.tagColor()), looter));
        inventory.setContents(contents);
        // The sixth row is the footer: never show stored items there.
        for (int slot = ChestLayout.STORAGE_SLOTS; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        ChestLayout.drawFooter(inventory, plugin, looter, false);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(LoveClansPlugin plugin, ClanRaid raid, Clan defender, Player looter) {
        // Same lock as ClanChestMenu, keyed by the defender's clan id - it's the identical
        // physical chest storage. Without it, two attackers looting concurrently (or an attacker
        // and the defender's own officer opening chest storage) each snapshot the same contents
        // and both walk away with whatever they took, duplicating it.
        if (!plugin.getClanManager().tryLockItemChest(defender.id())) {
            plugin.getMessages().send(looter, "chest.busy");
            return;
        }
        plugin.getClanManager().loadChestContentsAsync(defender).thenAccept(contents ->
                plugin.runSync(() -> {
                    // The looter may have logged out while the chest was loading: no close event would ever
                    // fire, so the lock would stay and the defender's chest would read "busy" until a restart.
                    if (!looter.isOnline()) {
                        plugin.getClanManager().unlockItemChest(defender.id());
                        return;
                    }
                    RaidLootMenu menu = new RaidLootMenu(plugin, defender, looter, raid.id(), contents);
                    looter.openInventory(menu.inventory);
                })
        ).exceptionally(throwable -> {
            plugin.getClanManager().unlockItemChest(defender.id());
            plugin.runSync(() -> plugin.sendOperationError(looter, throwable));
            return null;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        // The chest is take-only and whole stacks only: anything that could put an item in, swap, split a stack or
        // sweep items to the cursor would dodge the per-raid slot cap that is counted below.
        switch (event.getAction()) {
            case COLLECT_TO_CURSOR, PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP,
                 HOTBAR_MOVE_AND_READD, CLONE_STACK, UNKNOWN -> {
                if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR || rawSlot < inventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            case PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, DROP_ONE_SLOT -> {
                if (rawSlot < inventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                if (rawSlot >= inventory.getSize()) { // shift-click from the looter's inventory would fill the chest
                    event.setCancelled(true);
                    return;
                }
            }
            default -> { }
        }
        if (rawSlot >= inventory.getSize()) {
            return; // player's own inventory — normal behaviour
        }
        if (rawSlot >= unlockedSlots) {
            event.setCancelled(true); // beyond the defender's unlocked rows — nothing there to loot
            if (rawSlot == ChestLayout.CLOSE_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> looter.closeInventory());
            }
            return;
        }

        ClanRaid raid = plugin.getRaidManager().getRaid(raidId).orElse(null);
        boolean slotHadItem = event.getCurrentItem() != null && event.getCurrentItem().getType() != org.bukkit.Material.AIR;
        boolean capReached = raid == null || raid.itemSlotsRemaining() <= 0;
        if (slotHadItem && capReached) {
            event.setCancelled(true);
            plugin.getMessages().send(looter, "raid.nothing-left");
            return;
        }

        if (slotHadItem) {
            int slot = rawSlot;
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack after = inventory.getItem(slot);
                if (after == null || after.getType() == org.bukkit.Material.AIR) {
                    plugin.getRaidManager().recordItemSlotLooted(raidId);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize()) {
                event.setCancelled(true); // dragging items into the chest would be deleted on close
                return;
            }
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
        plugin.getClanManager().saveChestContentsAsync(defender.id(), toPersist);
        plugin.getClanManager().unlockItemChest(defender.id());
    }
}
