package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.trade.ClanTradeDelivery;
import me.lovelace.loveclans.storage.ClanStorage;
import me.lovelace.loveclans.util.InventorySerialization;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Post-trade payout queue (§4.2): once a {@link me.lovelace.loveclans.gui.ClanTradeSessionMenu}
 * finishes its 3-minute confirmation window, each side's payout doesn't land in the clan chest
 * immediately - it's held here until {@code dueAt} (10 minutes later). If the recipient's chest
 * doesn't have room for all the items once due, only what fits is deposited and the remainder
 * stays queued, retried on every {@link #tick()} until it all fits - it never falls back to
 * dropping items on the ground the way an interactive session abort does.
 */
public final class ClanTradeDeliveryManager {
    private static final int ITEM_SLOTS = 9;

    private final LoveClansPlugin plugin;
    private final ClanStorage storage;
    private final Map<UUID, ClanTradeDelivery> deliveries = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ClanTradeDeliveryManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllTradeDeliveriesAsync().thenAccept(all ->
                all.forEach(delivery -> deliveries.put(delivery.id(), delivery)));
    }

    /** Queues one side's payout of a just-confirmed trade. A no-op if there's nothing to deliver. */
    public CompletableFuture<Void> scheduleAsync(UUID clanId, String fromTag, String fromTagColor,
                                                  List<ItemStack> items, long money, long dueAt) {
        boolean hasItems = items != null && !items.isEmpty();
        if (money <= 0 && !hasItems) {
            return CompletableFuture.completedFuture(null);
        }
        ClanTradeDelivery delivery = new ClanTradeDelivery(UUID.randomUUID(), clanId, fromTag, fromTagColor,
                money, serializeItems(items), dueAt, money <= 0);
        return storage.saveTradeDeliveryAsync(delivery).thenRun(() -> deliveries.put(delivery.id(), delivery));
    }

    /** Called periodically off a repeating task - attempts every delivery whose due time has passed. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (ClanTradeDelivery delivery : deliveries.values()) {
            if (delivery.dueAt() > now) continue;
            if (!inFlight.add(delivery.id())) continue;
            attempt(delivery);
        }
    }

    private void attempt(ClanTradeDelivery delivery) {
        plugin.getClanManager().getClanById(delivery.clanId()).ifPresentOrElse(
                clan -> attemptFor(clan, delivery),
                () -> {
                    // Clan disbanded while the delivery was pending - there's no chest left to
                    // credit and nothing sensible to refund it to.
                    plugin.getLogger().warning("Discarding clan trade delivery " + delivery.id()
                            + " - clan " + delivery.clanId() + " no longer exists.");
                    deliveries.remove(delivery.id());
                    storage.deleteTradeDeliveryAsync(delivery.id());
                    inFlight.remove(delivery.id());
                });
    }

    private void attemptFor(Clan clan, ClanTradeDelivery delivery) {
        // depositRewardToChestAsync bumps clan.chestMoney() synchronously and only persists to the
        // DB asynchronously (same "already committed in memory" contract ClanTradeSessionMenu#execute
        // relies on) - so the money side is considered delivered the moment it's invoked, not when
        // its future resolves, otherwise a slow/failed DB write would double-credit on retry.
        ClanTradeDelivery working = delivery;
        if (!working.moneyDelivered() && working.money() > 0) {
            plugin.getClanManager().depositRewardToChestAsync(clan, working.money()).exceptionally(t -> {
                plugin.getLogger().warning("Trade delivery money persist failed for " + delivery.id() + ": " + t.getMessage());
                return null;
            });
            working = working.withMoneyDelivered();
        }

        List<ItemStack> pendingItems = deserializeItems(working.items());
        if (pendingItems.isEmpty()) {
            ClanTradeDelivery done = working;
            plugin.runSync(() -> {
                finish(done);
                inFlight.remove(delivery.id());
            });
            return;
        }

        ClanTradeDelivery afterMoney = working;
        plugin.getClanManager().depositItemsToChestAsync(clan, pendingItems).whenComplete((leftovers, throwable) ->
                plugin.runSync(() -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("Clan trade delivery attempt failed for " + delivery.id() + ": " + throwable.getMessage());
                        requeue(afterMoney);
                    } else if (leftovers.isEmpty()) {
                        finish(afterMoney);
                    } else {
                        requeue(afterMoney.withItems(serializeItems(leftovers)));
                    }
                    inFlight.remove(delivery.id());
                }));
    }

    private void requeue(ClanTradeDelivery updated) {
        deliveries.put(updated.id(), updated);
        storage.saveTradeDeliveryAsync(updated);
    }

    private void finish(ClanTradeDelivery delivery) {
        deliveries.remove(delivery.id());
        storage.deleteTradeDeliveryAsync(delivery.id());
        plugin.getClanManager().getClanById(delivery.clanId()).ifPresent(clan ->
                plugin.getClanManager().getOnlineMembersWithPermission(clan, ClanPermission.TRADE).forEach(p ->
                        plugin.getMessages().send(p, "trade.delivery.arrived",
                                Map.of("tag", delivery.fromTag() == null ? "" : delivery.fromTag(),
                                        "color", delivery.fromTagColor() == null ? "" : delivery.fromTagColor()))));
    }

    private byte[] serializeItems(List<ItemStack> items) {
        Inventory temp = Bukkit.createInventory(null, ITEM_SLOTS);
        if (items != null) {
            int index = 0;
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir() || index >= ITEM_SLOTS) continue;
                temp.setItem(index++, item);
            }
        }
        return InventorySerialization.serialize(temp);
    }

    private List<ItemStack> deserializeItems(byte[] data) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : InventorySerialization.deserialize(data, ITEM_SLOTS)) {
            if (item != null && !item.getType().isAir()) items.add(item);
        }
        return items;
    }
}
