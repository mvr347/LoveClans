package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.CurrencyDetector;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Real, drag-and-drop clan item storage (§2.3 "Предметы"). Unlocked slots (the first
 * {@code chestRows()*9}, at most 5 rows) are live storage; further rows up to slot 44 show a plain locked icon and can't hold
 * items - unlocking them is done via the "Сундук" upgrade (see ClanUpgradesMenu), not from here.
 * The sixth row is the gui_gen footer (see {@link ChestLayout}) and never stores items.
 * Self-registers as a listener scoped to its own inventory instance, unregistering and
 * persisting contents on close.
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
        this.unlockedSlots = ChestLayout.unlockedSlots(clan.chestRows(), contents.length);
        this.inventory = Bukkit.createInventory(null, ClanManager.CHEST_MAX_SIZE,
                plugin.getMessages().component("gui.chest-items-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        inventory.setContents(contents);
        drawLockedSlots();
        ChestLayout.drawFooter(inventory, plugin, player, true);
    }

    public static void open(LoveClansPlugin plugin, Clan clan, Player player) {
        // Reserve the chest first - two concurrent openers (or an opener and an active raid
        // looter, see RaidLootMenu) would each snapshot the same contents and overwrite each
        // other's changes on close, duplicating whatever both took out.
        if (!plugin.getClanManager().tryLockItemChest(clan.id())) {
            plugin.getMessages().send(player, "chest.busy");
            return;
        }
        plugin.getClanManager().loadChestContentsAsync(clan).thenAccept(contents ->
                plugin.runSync(() -> {
                    // The player may have left while the contents were loading: nobody would ever close
                    // the menu, so the lock would stay held forever.
                    if (!player.isOnline()) {
                        plugin.getClanManager().unlockItemChest(clan.id());
                        return;
                    }
                    ClanChestMenu menu = new ClanChestMenu(plugin, clan, player, contents);
                    if (player.openInventory(menu.inventory) == null) {
                        plugin.getClanManager().unlockItemChest(clan.id());
                        return;
                    }
                    // Register only once the inventory is really open, so a failed open leaks no listener.
                    plugin.getServer().getPluginManager().registerEvents(menu, plugin);
                })
        ).exceptionally(throwable -> {
            plugin.getClanManager().unlockItemChest(clan.id());
            plugin.runSync(() -> plugin.sendOperationError(player, throwable));
            return null;
        });
    }

    private void drawLockedSlots() {
        for (int slot = unlockedSlots; slot < ChestLayout.STORAGE_SLOTS; slot++) {
            inventory.setItem(slot, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.locked.name", player))
                    .lore(plugin.getMessages().components("gui.chest.locked.lore", player))
                    .build());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        boolean inPlayerInventory = event.getRawSlot() >= inventory.getSize();
        boolean inStorage = event.getRawSlot() >= 0 && event.getRawSlot() < unlockedSlots;
        if (inStorage || inPlayerInventory) {
            if (refuseCoins(event, inStorage, inPlayerInventory)) {
                return;
            }
        }
        if (inPlayerInventory) {
            return; // clicks in the player's own inventory behave normally
        }
        if (inStorage) {
            return; // real storage slot — allow normal item movement
        }
        event.setCancelled(true); // locked slot or footer — can't hold items
        int slot = event.getRawSlot();
        if (slot == ChestLayout.CLOSE_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        } else if (slot == ChestLayout.BACK_SLOT) {
            // Opening another inventory fires our close handler first, which persists and unlocks the chest.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getClanManager().getClanById(clan.id())
                    .ifPresent(fresh -> plugin.getGuiManager().openMain(player, fresh)));
        }
    }

    /**
     * The item chest holds items, not money: coins belong in the treasury. Cancels any click that would put LoveEconomy
     * coins into live storage (cursor placement, shift-click, hotbar or offhand swap). Returns true when it cancelled.
     */
    private boolean refuseCoins(InventoryClickEvent event, boolean inStorage, boolean inPlayerInventory) {
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack incoming = ChestInsertGuard.incoming(event.getAction(), inStorage, inPlayerInventory,
                event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND,
                event.getCursor(), event.getCurrentItem(), hotbar, player.getInventory().getItemInOffHand());
        if (incoming == null || !CurrencyDetector.isCurrency(incoming)) {
            return false;
        }
        event.setCancelled(true);
        plugin.getMessages().sendDeny(player, "deny.chest.currency-rejected", Map.of());
        return true;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        boolean touchesStorage = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < unlockedSlots);
        if (touchesStorage && CurrencyDetector.isCurrency(event.getOldCursor())) {
            event.setCancelled(true);
            plugin.getMessages().sendDeny(player, "deny.chest.currency-rejected", Map.of());
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
        release();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            release();
        }
    }

    private boolean released = false;

    /** Persists the contents and frees the chest lock exactly once (close and quit can both arrive). */
    private void release() {
        if (released) return;
        released = true;
        HandlerList.unregisterAll(this);
        ItemStack[] full = inventory.getContents();
        ItemStack[] toPersist = new ItemStack[full.length];
        System.arraycopy(full, 0, toPersist, 0, unlockedSlots); // footer/locked heads are never persisted
        returnStrayCoins(toPersist);
        plugin.getClanManager().saveChestContentsAsync(clan.id(), toPersist);
        plugin.getClanManager().unlockItemChest(clan.id());
    }

    /**
     * Safety net for coins that got in anyway (stored before the ban, or through a path the click guard cannot see):
     * they are taken out before saving and handed back to the player, so money never ends up inside the item blob.
     */
    private void returnStrayCoins(ItemStack[] contents) {
        boolean returned = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && CurrencyDetector.isCurrency(stack)) {
                contents[i] = null;
                if (!player.getInventory().addItem(stack).isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                }
                returned = true;
            }
        }
        if (returned) {
            plugin.getMessages().send(player, "chest.coins-returned");
        }
    }
}
