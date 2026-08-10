package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanTradeSessionMenu;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for live clan trade negotiations ({@link ClanTradeSessionMenu}) - the part of a trade
 * that happens after both clans have agreed to talk (see ClanTradeManager's propose/accept
 * invite). Only bookkeeping lives here (one session per clan pair); the negotiation state,
 * lock/delivery timers and chest transfer logic live on the menu itself.
 *
 * <p>A session is no longer tied to the two players who happened to be online when it started -
 * once accepted, it runs as a background job (confirm -> 3-minute lock window -> 10-minute
 * delivery delay) that survives players logging off or closing the window. Any online member of
 * either clan who holds {@link ClanPermission#TRADE} can view and act on it at any time (see
 * {@link ClanTradeSessionMenu#reopenFor}), so lookups here are by clan, not by a fixed player.
 */
public final class ClanTradeSessionManager {
    private final LoveClansPlugin plugin;
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, ClanTradeSessionMenu> byClanPair = new ConcurrentHashMap<>();

    public ClanTradeSessionManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new AbstractMap.SimpleImmutableEntry<>(a, b) : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    public boolean hasActiveSession(UUID clanAId, UUID clanBId) {
        return byClanPair.containsKey(pairKey(clanAId, clanBId));
    }

    /** Finds a live session involving this clan, regardless of which side it's on - used by the
     *  trade-requests menu to offer a "reopen" button and by {@link #reopenFor}. */
    public Optional<ClanTradeSessionMenu> activeSessionForClan(UUID clanId) {
        return byClanPair.entrySet().stream()
                .filter(e -> e.getKey().getKey().equals(clanId) || e.getKey().getValue().equals(clanId))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** Reopens the shared negotiation window for any online member of a clan with an active session, provided they hold Trade permission. */
    public boolean reopenFor(Player player) {
        Optional<Clan> clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clan.isEmpty()) return false;
        if (!plugin.getClanManager().hasClanPermission(player, clan.get(), ClanPermission.TRADE)) return false;
        return activeSessionForClan(clan.get().id())
                .map(session -> session.reopenFor(player))
                .orElse(false);
    }

    /**
     * Starts a trade session between clanFrom and clanTo. The initial viewer for clanFrom is
     * whichever of its online members currently holds the Trade permission - not necessarily the
     * original proposer, who may have logged off since sending the invite. This is only who the
     * window opens for automatically; any other authorized member can join in via {@link #reopenFor}.
     */
    public void startSession(Clan clanFrom, Clan clanTo, Player accepter) {
        var key = pairKey(clanFrom.id(), clanTo.id());
        if (byClanPair.containsKey(key)) {
            plugin.getMessages().send(accepter, "trade.already-pending");
            return;
        }
        List<Player> candidates = plugin.getClanManager().getOnlineMembersWithPermission(clanFrom, ClanPermission.TRADE);
        Player representative = candidates.isEmpty() ? null : candidates.get(0);
        if (representative == null) {
            plugin.getMessages().send(accepter, "trade.session.needs-online-rep", Map.of("tag", clanFrom.tag(), "color", clanFrom.tagColor()));
            return;
        }
        ClanTradeSessionMenu session = new ClanTradeSessionMenu(plugin, this, clanFrom, clanTo, representative, accepter);
        byClanPair.put(key, session);
        session.openForBoth();
    }

    public void unregister(ClanTradeSessionMenu session, UUID clanAId, UUID clanBId) {
        byClanPair.remove(pairKey(clanAId, clanBId), session);
    }

    /**
     * Called on PlayerQuitEvent. A session now runs as a background job independent of who's
     * online (it can commit and even deliver while every representative is offline), so quitting
     * no longer cancels it - this is intentionally a no-op, kept so the call site doesn't need
     * touching if that ever changes again.
     */
    public void handlePlayerQuit(Player player) {
    }
}
