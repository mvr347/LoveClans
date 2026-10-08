package me.lovelace.loveclans.activity;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanConflictResolvedEvent;
import me.lovelace.loveclans.api.events.ClanDiplomacyChangeEvent;
import me.lovelace.loveclans.api.events.ClanTreasuryDepositEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.history.ConflictKind;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into activity points. It never looks at the managers' state to work out who won: the result is
 * whatever the manager put in the event. Each award has an id (conflict + player + kind), so a repeated event or a
 * crash right after a conflict cannot pay twice.
 */
public final class ActivityListener implements Listener {
    private final LoveClansPlugin plugin;
    private final ActivityManager activity;
    /** Coins counted today per player, to cap what a deposit loop can earn. */
    private final Map<UUID, long[]> depositedToday = new HashMap<>();

    public ActivityListener(LoveClansPlugin plugin, ActivityManager activity) {
        this.plugin = plugin;
        this.activity = activity;
    }

    private int points(String path, int fallback) {
        return plugin.getConfig().getInt("activity.points." + path, fallback);
    }

    @EventHandler
    public void onConflictResolved(ClanConflictResolvedEvent event) {
        if (!plugin.getConfig().getBoolean("activity.enabled", true)) return;
        ActivityCategory category = switch (event.kind()) {
            case WAR -> ActivityCategory.WAR;
            case RAID -> ActivityCategory.RAID;
            case SIEGE -> ActivityCategory.SIEGE;
        };
        String key = event.kind().name().toLowerCase();
        int participation = points(key + ".participation", switch (event.kind()) { case WAR -> 25; case RAID -> 15; case SIEGE -> 30; });
        int victory = points(key + ".victory", switch (event.kind()) { case WAR -> 100; case RAID -> 60; case SIEGE -> 120; });
        UUID winner = event.winnerClanId();
        event.participants().forEach((playerId, clanId) -> {
            String base = event.kind() + ":" + event.conflictId() + ":" + playerId;
            activity.awardOnce(base + ":part", playerId, clanId, category, participation);
            if (winner != null && winner.equals(clanId)) {
                activity.awardOnce(base + ":win", playerId, clanId, category, victory);
            }
        });
    }

    @EventHandler
    public void onTreasuryDeposit(ClanTreasuryDepositEvent event) {
        if (!plugin.getConfig().getBoolean("activity.enabled", true) || event.actorId() == null) return;
        long perPoint = Math.max(1L, plugin.getConfig().getLong("activity.points.economy.per-coins", 100L));
        long dailyCap = plugin.getConfig().getLong("activity.points.economy.daily-cap", 200L);
        long today = System.currentTimeMillis() / 86_400_000L;
        long[] state = depositedToday.computeIfAbsent(event.actorId(), id -> new long[]{today, 0L});
        if (state[0] != today) {
            state[0] = today;
            state[1] = 0L;
        }
        long earned = Math.min(event.amount() / perPoint, Math.max(0L, dailyCap - state[1]));
        if (earned <= 0) return;
        state[1] += earned;
        activity.addPlayerActivity(event.actorId(), event.clan().id(), ActivityCategory.ECONOMY, (int) earned);
    }

    /** A new alliance pays the members who can sign it, once per pair of clans per day (no farming by toggling). */
    @EventHandler
    public void onDiplomacyChange(ClanDiplomacyChangeEvent event) {
        if (!plugin.getConfig().getBoolean("activity.enabled", true)) return;
        if (event.relation() != DiplomacyRelation.ALLY || event.oldRelation() == DiplomacyRelation.ALLY) return;
        int amount = points("diplomacy.agreement", 30);
        long day = System.currentTimeMillis() / 86_400_000L;
        UUID a = event.sourceClanId();
        UUID b = event.targetClanId();
        String pair = a.compareTo(b) < 0 ? a + "+" + b : b + "+" + a;
        for (UUID clanId : new UUID[]{a, b}) {
            Clan clan = plugin.getClanManager().getClanById(clanId).orElse(null);
            if (clan == null) continue;
            for (UUID memberId : clan.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && clan.hasPermission(memberId, ClanPermission.DIPLOMACY)) {
                    activity.awardOnce("DIPLO:" + pair + ":" + day + ":" + memberId, memberId, clanId, ActivityCategory.DIPLOMACY, amount);
                }
            }
        }
    }
}
