package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drag-and-drop trade offer builder (§4.2) - items dragged into the top two rows come out of the
 * proposing player's own inventory (not the shared clan chest directly, a deliberate scope
 * simplification; the chest-item side of §4.2's full "Diplomacy &amp; Trade" tab is task §6/§9's
 * job). Money is fixed for the lifetime of this menu (set via the /clan trade command argument) -
 * editing it in-GUI would need a chat prompt, and opening chat force-closes this inventory
 * client-side in vanilla Minecraft, which would strand the staged items.
 *
 * <p>Self-registers as a listener scoped to its own inventory instance (mirrors ClanChestMenu):
 * on close, anything still sitting in the offer area is returned to the player unless the offer
 * was actually sent.
 */
public final class ClanTradeOfferMenu implements Listener {
    private static final int[] OFFER_SLOTS;
    static {
        OFFER_SLOTS = new int[18];
        for (int i = 0; i < 18; i++) OFFER_SLOTS[i] = i;
    }
    private static final int SLOT_MONEY = 22;
    private static final int SLOT_SEND = 31;
    private static final int SLOT_CLOSE = 35;
    private static final int INVENTORY_SIZE = 36;

    private final LoveClansPlugin plugin;
    private final Clan sourceClan;
    private final Clan targetClan;
    private final Player player;
    private final long money;
    private final Inventory inventory;
    private boolean sent = false;

    private ClanTradeOfferMenu(LoveClansPlugin plugin, Clan sourceClan, Clan targetClan, Player player, long money) {
        this.plugin = plugin;
        this.sourceClan = sourceClan;
        this.targetClan = targetClan;
        this.player = player;
        this.money = money;
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessages().component("gui.trade-offer.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));
        render();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(LoveClansPlugin plugin, Clan sourceClan, Clan targetClan, Player player, long money) {
        ClanTradeOfferMenu menu = new ClanTradeOfferMenu(plugin, sourceClan, targetClan, player, money);
        player.openInventory(menu.inventory);
    }

    private void render() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isOfferSlot(slot)) continue;
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
        inventory.setItem(SLOT_MONEY, ItemBuilder.of(Material.SUNFLOWER)
                .name(plugin.getMessages().component("gui.trade-offer.money.name", player))
                .lore(plugin.getMessages().component("gui.trade-offer.money.lore", Map.of("amount", String.valueOf(money)), player))
                .build());
        inventory.setItem(SLOT_SEND, ItemBuilder.head(ItemBuilder.HEAD_COMPLETED_QUESTS)
                .name(plugin.getMessages().component("gui.trade-offer.send.name", player))
                .lore(plugin.getMessages().component("gui.trade-offer.send.lore", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());
    }

    private boolean isOfferSlot(int slot) {
        return slot < 18;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= inventory.getSize()) {
            return; // player's own inventory — normal behaviour
        }
        if (isOfferSlot(rawSlot)) {
            return; // free drag zone
        }
        event.setCancelled(true);
        if (rawSlot == SLOT_SEND) {
            handleSend();
        } else if (rawSlot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        boolean touchesReserved = event.getRawSlots().stream()
                .anyMatch(slot -> slot < inventory.getSize() && !isOfferSlot(slot));
        if (touchesReserved) {
            event.setCancelled(true);
        }
    }

    private void handleSend() {
        List<ItemStack> offered = new ArrayList<>();
        for (int slot : OFFER_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                offered.add(item.clone());
            }
        }
        plugin.getClanTradeManager().proposeTradeAsync(sourceClan, player.getUniqueId(), targetClan, money, offered)
                .thenAccept(trade -> plugin.runSync(() -> {
                    sent = true;
                    for (int slot : OFFER_SLOTS) inventory.setItem(slot, null);
                    plugin.getMessages().send(player, "trade.sent", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
                    player.closeInventory();
                }))
                .exceptionally(t -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, t));
                    return null;
                });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        HandlerList.unregisterAll(this);
        if (sent) {
            return;
        }
        for (int slot : OFFER_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
