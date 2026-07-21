package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.trade.ClanTrade;
import me.lovelace.loveclans.model.trade.TradeStatus;
import me.lovelace.loveclans.storage.ClanStorage;
import me.lovelace.loveclans.util.InventorySerialization;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan-to-clan trading through the chest (§4.2) - a clan offers money and/or items from its own
 * chest to another clan; the receiving clan's leader/officers accept or decline. Money and items
 * are escrowed (removed from the proposer's chest) the moment the offer is sent, not on accept,
 * so a clan can't over-promise resources it no longer has by the time the other side responds.
 *
 * <p>This only covers the underlying mechanics plus a minimal propose/review flow - the full
 * "Diplomacy &amp; Trade" tab redesign (§6/§9) is a separate task.
 */
public final class ClanTradeManager {
    private final LoveClansPlugin plugin;
    private final ClanStorage storage;

    private final Map<UUID, ClanTrade> pendingTrades = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> lastTradeAt = new ConcurrentHashMap<>();

    public ClanTradeManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadPendingTradesAsync().thenAccept(all ->
                all.forEach(trade -> pendingTrades.put(trade.id(), trade)));
    }

    public Optional<ClanTrade> pendingTrade(UUID tradeId) {
        return Optional.ofNullable(pendingTrades.get(tradeId));
    }

    public Collection<ClanTrade> pendingTradesFor(UUID clanId) {
        return pendingTrades.values().stream()
                .filter(t -> t.fromClanId().equals(clanId) || t.toClanId().equals(clanId))
                .toList();
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new AbstractMap.SimpleImmutableEntry<>(a, b) : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    private Duration cooldown() {
        return Duration.ofMinutes(plugin.getConfig().getLong("clans.trade.cooldown-minutes", 30));
    }

    private boolean hasPendingBetween(UUID fromClanId, UUID toClanId) {
        return pendingTrades.values().stream()
                .anyMatch(t -> t.fromClanId().equals(fromClanId) && t.toClanId().equals(toClanId));
    }

    /** True if trading between these two clans is currently blocked - reused by LoveTradesHook for the player-to-player side too. */
    public boolean tradeBlocked(UUID clanA, UUID clanB) {
        return plugin.getClanManager().inConflictWith(clanA, clanB)
                || plugin.getDiplomacyManager().isEmbargoed(clanA, clanB)
                || plugin.getDiplomacyManager().isBlockading(clanA, clanB)
                || plugin.getDiplomacyManager().isBlockading(clanB, clanA);
    }

    public CompletableFuture<ClanTrade> proposeTradeAsync(Clan from, UUID actorId, Clan to, long money, List<ItemStack> items) {
        if (from == null || actorId == null || to == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clans and actor ID cannot be null."));
        }
        List<ItemStack> offeredItems = items == null ? List.of() : List.copyOf(items);
        return plugin.supplySync(() -> {
            if (!from.hasPermission(actorId, ClanPermission.BANK)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (from.id().equals(to.id())) {
                throw new IllegalStateException("general.error");
            }
            if (money <= 0 && offeredItems.isEmpty()) {
                throw new IllegalStateException("trade.empty-offer");
            }
            if (from.isChestTaxLocked()) {
                throw new IllegalStateException("chest.tax-locked");
            }
            if (money > from.chestMoney()) {
                throw new IllegalStateException("chest.insufficient-items");
            }
            if (tradeBlocked(from.id(), to.id())) {
                throw new IllegalStateException("trade.blocked");
            }
            if (hasPendingBetween(from.id(), to.id())) {
                throw new IllegalStateException("trade.already-pending");
            }
            Long last = lastTradeAt.get(pairKey(from.id(), to.id()));
            if (last != null) {
                long remaining = cooldown().toMillis() - (System.currentTimeMillis() - last);
                if (remaining > 0) {
                    throw new TradeCooldownException(remaining / 1000);
                }
            }
            return null;
        }).thenCompose(ignored -> plugin.getClanManager().removeChestMoneyAsync(from, money))
                .thenCompose(ignored -> {
                    ClanTrade trade = new ClanTrade(UUID.randomUUID(), from.id(), to.id(), money,
                            serializeItems(offeredItems), TradeStatus.PENDING, System.currentTimeMillis(), 0L);
                    return storage.saveTradeAsync(trade).thenApply(v -> trade);
                }).thenApply(trade -> {
                    pendingTrades.put(trade.id(), trade);
                    lastTradeAt.put(pairKey(from.id(), to.id()), trade.createdAt());
                    plugin.runSync(() -> notifyProposal(from, to, trade));
                    return trade;
                });
    }

    public CompletableFuture<ClanTrade> acceptTradeAsync(UUID tradeId, Clan to, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.toClanId().equals(to.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!to.hasPermission(actorId, ClanPermission.BANK)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        return plugin.getClanManager().getClanById(trade.fromClanId()).map(from ->
                settleTrade(trade, to, from, TradeStatus.ACCEPTED)
        ).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("clan.not-found")));
    }

    public CompletableFuture<ClanTrade> declineTradeAsync(UUID tradeId, Clan to, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.toClanId().equals(to.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!to.hasPermission(actorId, ClanPermission.BANK)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        return plugin.getClanManager().getClanById(trade.fromClanId()).map(from ->
                refundTrade(trade, from, TradeStatus.DECLINED)
        ).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("clan.not-found")));
    }

    /** Lets the proposer take back a still-pending offer instead of waiting for the other side. */
    public CompletableFuture<ClanTrade> cancelTradeAsync(UUID tradeId, Clan from, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.fromClanId().equals(from.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!from.hasPermission(actorId, ClanPermission.BANK)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        return refundTrade(trade, from, TradeStatus.CANCELLED);
    }

    private CompletableFuture<Void> creditMoney(Clan clan, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getClanManager().depositRewardToChestAsync(clan, amount).thenApply(v -> null);
    }

    private CompletableFuture<ClanTrade> settleTrade(ClanTrade trade, Clan to, Clan from, TradeStatus status) {
        return creditMoney(to, trade.money())
                .thenCompose(ignored -> plugin.getClanManager().depositItemsToChestAsync(to, deserializeItems(trade.items())))
                .thenCompose(leftovers -> {
                    ClanTrade resolved = trade.resolved(status);
                    return storage.saveTradeAsync(resolved).thenApply(v -> {
                        pendingTrades.remove(trade.id());
                        plugin.runSync(() -> {
                            dropLeftovers(to, leftovers);
                            notifyResolution(from, to, resolved);
                        });
                        return resolved;
                    });
                });
    }

    private CompletableFuture<ClanTrade> refundTrade(ClanTrade trade, Clan from, TradeStatus status) {
        return creditMoney(from, trade.money())
                .thenCompose(ignored -> plugin.getClanManager().depositItemsToChestAsync(from, deserializeItems(trade.items())))
                .thenCompose(leftovers -> {
                    ClanTrade resolved = trade.resolved(status);
                    return storage.saveTradeAsync(resolved).thenApply(v -> {
                        pendingTrades.remove(trade.id());
                        plugin.runSync(() -> {
                            dropLeftovers(from, leftovers);
                            plugin.getClanManager().getClanById(resolved.toClanId())
                                    .ifPresent(to -> notifyResolution(from, to, resolved));
                        });
                        return resolved;
                    });
                });
    }

    /** If a clan's chest was too full to absorb the settled items, drop them at an online member's feet instead of losing them. */
    private void dropLeftovers(Clan clan, List<ItemStack> leftovers) {
        if (leftovers.isEmpty()) return;
        Optional<Player> online = plugin.getClanManager().getOnlineLeader(clan)
                .or(() -> onlineMembers(clan).findFirst());
        online.ifPresent(player -> {
            Location location = player.getLocation();
            for (ItemStack item : leftovers) {
                location.getWorld().dropItemNaturally(location, item);
            }
        });
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull);
    }

    private void notifyProposal(Clan from, Clan to, ClanTrade trade) {
        plugin.getClanManager().getOnlineMembersWithPermission(to, ClanPermission.BANK).forEach(p ->
                plugin.getMessages().sendClickableTrade(p, trade.id(), from.tag(), from.tagColor()));
        plugin.getClanManager().getOnlineMembersWithPermission(from, ClanPermission.BANK).forEach(p ->
                plugin.getMessages().send(p, "trade.sent", Map.of("tag", to.tag(), "color", to.tagColor())));
    }

    private void notifyResolution(Clan from, Clan to, ClanTrade trade) {
        String key = switch (trade.status()) {
            case ACCEPTED -> "trade.accepted";
            case DECLINED -> "trade.declined";
            case CANCELLED -> "trade.cancelled";
            default -> null;
        };
        if (key == null) return;
        onlineMembers(from).forEach(p -> plugin.getMessages().send(p, key, Map.of("tag", to.tag(), "color", to.tagColor())));
        if (trade.status() == TradeStatus.ACCEPTED) {
            onlineMembers(to).forEach(p -> plugin.getMessages().send(p, "trade.received", Map.of("tag", from.tag(), "color", from.tagColor())));
        }
    }

    private byte[] serializeItems(List<ItemStack> items) {
        if (items.isEmpty()) return null;
        Inventory temp = Bukkit.createInventory(null, roundUpToNine(items.size()));
        for (int i = 0; i < items.size(); i++) {
            temp.setItem(i, items.get(i));
        }
        return InventorySerialization.serialize(temp);
    }

    private List<ItemStack> deserializeItems(byte[] data) {
        if (data == null) return List.of();
        ItemStack[] contents = InventorySerialization.deserialize(data, ClanManager.CHEST_MAX_SIZE);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                result.add(item);
            }
        }
        return result;
    }

    private int roundUpToNine(int size) {
        return Math.max(9, ((size + 8) / 9) * 9);
    }
}
