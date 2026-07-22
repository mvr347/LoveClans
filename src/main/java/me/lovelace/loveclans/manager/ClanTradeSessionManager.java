package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanTradeSessionMenu;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for live, two-sided clan trade negotiations ({@link ClanTradeSessionMenu}) - the part
 * of a trade that happens after both clans have agreed to talk (see ClanTradeManager's
 * propose/accept invite). Only bookkeeping lives here (one session per clan pair, session lookup
 * by player for quit/close handling); the actual shared inventory, ready-state and chest transfer
 * logic live on the menu itself.
 */
public final class ClanTradeSessionManager {
    private final LoveClansPlugin plugin;
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, ClanTradeSessionMenu> byClanPair = new ConcurrentHashMap<>();
    private final Map<UUID, ClanTradeSessionMenu> byPlayer = new ConcurrentHashMap<>();

    public ClanTradeSessionManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new AbstractMap.SimpleImmutableEntry<>(a, b) : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    public boolean hasActiveSession(UUID clanAId, UUID clanBId) {
        return byClanPair.containsKey(pairKey(clanAId, clanBId));
    }

    /**
     * Starts a live trade session between clanFrom and clanTo. The representative for clanFrom is
     * whichever of its online members currently holds the Trade permission - not necessarily the
     * original proposer, who may have logged off since sending the invite.
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
        byPlayer.put(representative.getUniqueId(), session);
        byPlayer.put(accepter.getUniqueId(), session);
        session.openForBoth();
    }

    public void unregister(ClanTradeSessionMenu session, UUID clanAId, UUID clanBId, UUID playerAId, UUID playerBId) {
        byClanPair.remove(pairKey(clanAId, clanBId), session);
        byPlayer.remove(playerAId, session);
        byPlayer.remove(playerBId, session);
    }

    /** Called on PlayerQuitEvent - a live negotiation can't continue with one side gone. */
    public void handlePlayerQuit(Player player) {
        ClanTradeSessionMenu session = byPlayer.get(player.getUniqueId());
        if (session != null) {
            session.abort(player);
        }
    }
}
