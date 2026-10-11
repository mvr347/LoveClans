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
 * Persists after each take and again on close; the item-chest lock is held until the final save completes.
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
        for (int slot = ChestLayout.STORAGE_SLOTS; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        ChestLayout.drawFooter(inventory, plugin, looter, false);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(LoveClansPlugin plugin, ClanRaid raid, Clan defender, Player looter) {
        if (!plugin.getRaidManager().canLoot(raid, looter)) {
            plugin.getMessages().send(looter, "raid.loot.not-in-zone");
            return;
        }
        if (!plugin.getClanManager().tryLockItemChest(defender.id())) {
            plugin.getMessages().send(looter, "chest.busy");
            return;
        }
        plugin.getClanManager().loadChestContentsAsync(defender).thenAccept(contents ->
                plugin.runSync(() -> {
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
                if (rawSlot >= inventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            default -> { }
        }
        if (rawSlot >= inventory.getSize()) {
            return;
        }
        if (rawSlot >= unlockedSlots) {
            event.setCancelled(true);
            if (rawSlot == ChestLayout.CLOSE_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> looter.closeInventory());
            }
            return;
        }

        event.setCancelled(true);
        ItemStack stack = inventory.getItem(rawSlot);
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ClanRaid raid = plugin.getRaidManager().getRaid(raidId).orElse(null);
        if (!plugin.getRaidManager().canLoot(raid, looter)) {
            plugin.getMessages().send(looter, "raid.loot.not-in-zone");
            Bukkit.getScheduler().runTask(plugin, () -> looter.closeInventory());
            return;
        }
        if (raid.itemSlotsRemaining() <= 0) {
            plugin.getMessages().send(looter, "raid.nothing-left");
            return;
        }
        Map<Integer, ItemStack> leftover = looter.getInventory().addItem(stack.clone());
        if (leftover.isEmpty()) {
            inventory.setItem(rawSlot, null);
            plugin.getRaidManager().recordItemSlotLooted(raidId);
        } else {
            inventory.setItem(rawSlot, leftover.values().iterator().next());
        }
        persistChestSnapshot();
    }

    private void persistChestSnapshot() {
        ItemStack[] full = inventory.getContents();
        ItemStack[] toPersist = new ItemStack[full.length];
        System.arraycopy(full, 0, toPersist, 0, unlockedSlots);
        plugin.getClanManager().saveChestContentsAsync(defender.id(), toPersist)
                .exceptionally(t -> {
                    plugin.getLogger().warning("Raid loot chest save failed for clan " + defender.id() + ": " + t.getMessage());
                    return null;
                });
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize()) {
                event.setCancelled(true);
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
        UUID clanId = defender.id();
        plugin.getClanManager().saveChestContentsAsync(clanId, toPersist)
                .whenComplete((v, err) -> {
                    if (err != null) {
                        plugin.getLogger().warning("Raid loot final chest save failed for clan " + clanId + ": " + err.getMessage());
                    }
                    plugin.getClanManager().unlockItemChest(clanId);
                });
    }
}
