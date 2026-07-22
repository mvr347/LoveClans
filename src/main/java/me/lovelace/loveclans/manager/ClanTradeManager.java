package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.trade.ClanTrade;
import me.lovelace.loveclans.model.trade.TradeStatus;
import me.lovelace.loveclans.storage.ClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan-to-clan trade invitations (§4.2/§6.3) - the first, lightweight half of trading: one clan
 * asks another "want to trade?" with nothing at stake yet. Accepting an invite doesn't move any
 * money or items by itself - it hands off to {@link ClanTradeSessionManager}, which opens a live,
 * two-sided negotiation window (both clans stage their own items/money and must both confirm)
 * mirroring a normal player-to-player LoveTrades trade, just clan-vs-clan.
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

    public CompletableFuture<ClanTrade> proposeTradeAsync(Clan from, UUID actorId, Clan to) {
        if (from == null || actorId == null || to == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clans and actor ID cannot be null."));
        }
        return plugin.supplySync(() -> {
            if (!from.hasPermission(actorId, ClanPermission.TRADE)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (from.id().equals(to.id())) {
                throw new IllegalStateException("general.error");
            }
            if (tradeBlocked(from.id(), to.id())) {
                throw new IllegalStateException("trade.blocked");
            }
            if (hasPendingBetween(from.id(), to.id())) {
                throw new IllegalStateException("trade.already-pending");
            }
            if (plugin.getClanTradeSessionManager().hasActiveSession(from.id(), to.id())) {
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
        }).thenCompose(ignored -> {
            ClanTrade trade = new ClanTrade(UUID.randomUUID(), from.id(), to.id(), 0L,
                    null, TradeStatus.PENDING, System.currentTimeMillis(), 0L);
            return storage.saveTradeAsync(trade).thenApply(v -> trade);
        }).thenApply(trade -> {
            pendingTrades.put(trade.id(), trade);
            lastTradeAt.put(pairKey(from.id(), to.id()), trade.createdAt());
            plugin.runSync(() -> notifyProposal(from, to, trade));
            return trade;
        });
    }

    /** Accepting an invite starts the live two-sided negotiation session; nothing is transferred here. */
    public CompletableFuture<ClanTrade> acceptTradeAsync(UUID tradeId, Clan to, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.toClanId().equals(to.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!to.hasPermission(actorId, ClanPermission.TRADE)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        Player accepter = Bukkit.getPlayer(actorId);
        if (accepter == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.must-be-online"));
        }
        return plugin.getClanManager().getClanById(trade.fromClanId()).map(from -> {
            ClanTrade resolved = trade.resolved(TradeStatus.ACCEPTED);
            return storage.saveTradeAsync(resolved).thenApply(v -> {
                pendingTrades.remove(trade.id());
                plugin.runSync(() -> plugin.getClanTradeSessionManager().startSession(from, to, accepter));
                return resolved;
            });
        }).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("clan.not-found")));
    }

    public CompletableFuture<ClanTrade> declineTradeAsync(UUID tradeId, Clan to, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.toClanId().equals(to.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!to.hasPermission(actorId, ClanPermission.TRADE)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        return plugin.getClanManager().getClanById(trade.fromClanId()).map(from ->
                resolveInvite(trade, from, to, TradeStatus.DECLINED)
        ).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("clan.not-found")));
    }

    /** Lets the proposer take back a still-pending invite instead of waiting for the other side. */
    public CompletableFuture<ClanTrade> cancelTradeAsync(UUID tradeId, Clan from, UUID actorId) {
        ClanTrade trade = pendingTrades.get(tradeId);
        if (trade == null || !trade.fromClanId().equals(from.id())) {
            return CompletableFuture.failedFuture(new IllegalStateException("trade.not-found"));
        }
        if (!from.hasPermission(actorId, ClanPermission.TRADE)) {
            return CompletableFuture.failedFuture(new IllegalStateException("general.no-permission"));
        }
        return plugin.getClanManager().getClanById(trade.toClanId()).map(to ->
                resolveInvite(trade, from, to, TradeStatus.CANCELLED)
        ).orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("clan.not-found")));
    }

    private CompletableFuture<ClanTrade> resolveInvite(ClanTrade trade, Clan from, Clan to, TradeStatus status) {
        ClanTrade resolved = trade.resolved(status);
        return storage.saveTradeAsync(resolved).thenApply(v -> {
            pendingTrades.remove(trade.id());
            plugin.runSync(() -> notifyInviteResolution(from, to, resolved));
            return resolved;
        });
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull);
    }

    private void notifyProposal(Clan from, Clan to, ClanTrade trade) {
        plugin.getClanManager().getOnlineMembersWithPermission(to, ClanPermission.TRADE).forEach(p ->
                plugin.getMessages().sendClickableTrade(p, trade.id(), from.tag(), from.tagColor()));
        plugin.getClanManager().getOnlineMembersWithPermission(from, ClanPermission.TRADE).forEach(p ->
                plugin.getMessages().send(p, "trade.sent", Map.of("tag", to.tag(), "color", to.tagColor())));
    }

    private void notifyInviteResolution(Clan from, Clan to, ClanTrade trade) {
        String key = switch (trade.status()) {
            case DECLINED -> "trade.declined";
            case CANCELLED -> "trade.cancelled";
            default -> null;
        };
        if (key == null) return;
        onlineMembers(from).forEach(p -> plugin.getMessages().send(p, key, Map.of("tag", to.tag(), "color", to.tagColor())));
    }
}
