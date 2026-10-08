package me.lovelace.loveclans.history;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanConflictDeclaredEvent;
import me.lovelace.loveclans.api.events.ClanConflictResolvedEvent;
import me.lovelace.loveclans.api.events.ClanDiplomacyChangeEvent;
import me.lovelace.loveclans.api.events.ClanLeaderChangeEvent;
import me.lovelace.loveclans.api.events.ClanLevelUpEvent;
import me.lovelace.loveclans.api.events.ClanMemberJoinEvent;
import me.lovelace.loveclans.api.events.ClanMemberLeaveEvent;
import me.lovelace.loveclans.api.events.ClanRankChangeEvent;
import me.lovelace.loveclans.api.events.ConflictOutcome;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.history.ConflictKind;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the chronicle from domain events. Entries carry ids, not text: the GUI resolves names (a clan may be renamed)
 * and the wording from lang.yml when it shows them.
 */
public final class HistoryListener implements Listener {
    private final LoveClansPlugin plugin;
    private final ClanHistoryService history;

    public HistoryListener(LoveClansPlugin plugin, ClanHistoryService history) {
        this.plugin = plugin;
        this.history = history;
    }

    private static Map<String, String> meta(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @EventHandler
    public void onLevelUp(ClanLevelUpEvent event) {
        history.add(event.clan().id(), HistoryType.CLAN_LEVEL_UP, null, meta("level", String.valueOf(event.newLevel())));
    }

    @EventHandler
    public void onJoin(ClanMemberJoinEvent event) {
        history.add(event.clan().id(), HistoryType.MEMBER_JOINED, event.member().playerId(), Map.of());
    }

    @EventHandler
    public void onLeave(ClanMemberLeaveEvent event) {
        history.add(event.clan().id(), event.kicked() ? HistoryType.MEMBER_KICKED : HistoryType.MEMBER_LEFT, event.playerId(), Map.of());
    }

    @EventHandler
    public void onRank(ClanRankChangeEvent event) {
        // A promotion to leader is recorded once, as LEADER_CHANGED
        if (event.newRank() == ClanRank.LEADER) return;
        history.add(event.clan().id(), HistoryType.RANK_CHANGED, event.playerId(),
                meta("old", event.oldRank().name(), "new", event.newRank().name()));
    }

    @EventHandler
    public void onLeader(ClanLeaderChangeEvent event) {
        history.add(event.clan().id(), HistoryType.LEADER_CHANGED, event.newLeaderId(),
                meta("oldLeader", event.oldLeaderId() == null ? null : event.oldLeaderId().toString()));
    }

    @EventHandler
    public void onDeclared(ClanConflictDeclaredEvent event) {
        HistoryType type = switch (event.kind()) {
            case WAR -> HistoryType.WAR_DECLARED;
            case RAID -> HistoryType.RAID_STARTED;
            case SIEGE -> HistoryType.SIEGE_DECLARED;
        };
        String id = event.conflictId().toString();
        history.add(event.attackerClanId(), type, null,
                meta("opponentClanId", event.defenderClanId().toString(), "conflictId", id, "role", "attacker"));
        history.add(event.defenderClanId(), type, null,
                meta("opponentClanId", event.attackerClanId().toString(), "conflictId", id, "role", "defender"));
    }

    @EventHandler
    public void onResolved(ClanConflictResolvedEvent event) {
        if (event.outcome() == ConflictOutcome.DRAW) return;
        boolean attackerWon = event.outcome() == ConflictOutcome.ATTACKER_WIN;
        ConflictKind kind = event.kind();
        HistoryType won = switch (kind) {
            case WAR -> HistoryType.WAR_WON;
            case RAID -> HistoryType.RAID_WON;
            case SIEGE -> HistoryType.SIEGE_WON;
        };
        HistoryType lost = switch (kind) {
            case WAR -> HistoryType.WAR_LOST;
            case RAID -> HistoryType.RAID_LOST;
            case SIEGE -> HistoryType.SIEGE_LOST;
        };
        String id = event.conflictId().toString();
        UUID attacker = event.attackerClanId();
        UUID defender = event.defenderClanId();
        // A defender that held out a siege or a raid is "defended", not simply "won"
        HistoryType defenderWon = kind == ConflictKind.SIEGE ? HistoryType.SIEGE_DEFENDED : won;
        history.add(attacker, attackerWon ? won : lost, null, meta("opponentClanId", defender.toString(), "conflictId", id, "role", "attacker"));
        history.add(defender, attackerWon ? lost : defenderWon, null, meta("opponentClanId", attacker.toString(), "conflictId", id, "role", "defender"));
    }

    @EventHandler
    public void onDiplomacy(ClanDiplomacyChangeEvent event) {
        DiplomacyRelation oldRelation = event.oldRelation();
        HistoryType type;
        if (event.relation() == DiplomacyRelation.ALLY && oldRelation != DiplomacyRelation.ALLY) {
            type = HistoryType.DIPLOMACY_CREATED;
        } else if (oldRelation == DiplomacyRelation.ALLY && event.relation() != DiplomacyRelation.ALLY) {
            type = HistoryType.DIPLOMACY_ENDED;
        } else {
            return;
        }
        history.add(event.sourceClanId(), type, null, meta("opponentClanId", event.targetClanId().toString(), "relation", event.relation().name()));
        history.add(event.targetClanId(), type, null, meta("opponentClanId", event.sourceClanId().toString(), "relation", event.relation().name()));
    }
}
