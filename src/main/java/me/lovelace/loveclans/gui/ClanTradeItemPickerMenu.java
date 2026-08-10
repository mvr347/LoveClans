package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Picker overlay for {@link ClanTradeSessionMenu}'s "Add item" button (§4.2 redesign): shows the
 * clan's own chest contents (unlocked rows only - locked rows are guaranteed empty by
 * {@link ClanChestMenu}) so a trade representative can pull a single stack straight from the
 * treasury into their offer. Clicking a stack removes it from the chest and persists that
 * immediately, before the callback even runs - so the item is never counted in both the chest
 * and the trade offer at once. If the callback can't fit it into the offer (grid full), its
 * failure path is responsible for depositing it back into the chest.
 */
public final class ClanTradeItemPickerMenu implements Listener {

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private final Inventory inventory;
    private final int unlockedSlots;
    private final BiConsumer<Player, ItemStack> onPick;
    private boolean handled = false;

    private ClanTradeItemPickerMenu(LoveClansPlugin plugin, Clan clan, Player player, ItemStack[] contents, BiConsumer<Player, ItemStack> onPick) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
        this.onPick = onPick;
        this.unlockedSlots = Math.min(contents.length, clan.chestRows() * 9);
        this.inventory = Bukkit.createInventory(null, contents.length,
                plugin.getMessages().component("gui.trade-session.picker.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        inventory.setContents(contents);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(LoveClansPlugin plugin, Clan clan, Player player, BiConsumer<Player, ItemStack> onPick) {
        plugin.getClanManager().loadChestContentsAsync(clan).thenAccept(contents ->
                plugin.runSync(() -> {
                    ClanTradeItemPickerMenu menu = new ClanTradeItemPickerMenu(plugin, clan, player, contents.clone(), onPick);
                    player.openInventory(menu.inventory);
                })
        ).exceptionally(t -> {
            plugin.runSync(() -> plugin.sendOperationError(player, t));
            return null;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (handled) return;
        int slot = event.getRawSlot();
        if (slot >= inventory.getSize() || slot >= unlockedSlots) return;
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir()) return;

        handled = true;
        ItemStack taken = item.clone();
        ItemStack[] updated = inventory.getContents().clone();
        updated[slot] = null;
        plugin.getClanManager().saveChestContentsAsync(clan.id(), updated);

        Player picker = (Player) event.getWhoClicked();
        picker.closeInventory();
        onPick.accept(picker, taken);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }
}
