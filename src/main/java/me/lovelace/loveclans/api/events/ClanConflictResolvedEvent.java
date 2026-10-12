package me.lovelace.loveclans.api.events;

import me.lovelace.loveclans.model.history.ConflictKind;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;
import java.util.UUID;

/**
 * A war, raid or siege ended with a result (cancelled conflicts do not fire this). {@code participants} maps each
 * player that actually took part to the clan he fought for: online, not AFK, and doing something that counts.
 * {@code conflictId} is unique per conflict, so listeners can use it as an idempotency key.
 */
public final class ClanConflictResolvedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ConflictKind kind;
    private final UUID conflictId;
    private final UUID attackerClanId;
    private final UUID defenderClanId;
    private final ConflictOutcome outcome;
    private final Map<UUID, UUID> participants;
    private final double margin;

    public ClanConflictResolvedEvent(ConflictKind kind, UUID conflictId, UUID attackerClanId, UUID defenderClanId,
                                     ConflictOutcome outcome, Map<UUID, UUID> participants) {
        this(kind, conflictId, attackerClanId, defenderClanId, outcome, participants, -1.0);
    }

    public ClanConflictResolvedEvent(ConflictKind kind, UUID conflictId, UUID attackerClanId, UUID defenderClanId,
                                     ConflictOutcome outcome, Map<UUID, UUID> participants, double margin) {
        this.kind = kind;
        this.margin = margin;
        this.conflictId = conflictId;
        this.attackerClanId = attackerClanId;
        this.defenderClanId = defenderClanId;
        this.outcome = outcome;
        this.participants = Map.copyOf(participants);
    }

    public ConflictKind kind() { return kind; }
    public UUID conflictId() { return conflictId; }
    public UUID attackerClanId() { return attackerClanId; }
    public UUID defenderClanId() { return defenderClanId; }
    public ConflictOutcome outcome() { return outcome; }
    public Map<UUID, UUID> participants() { return participants; }

    /** How decisive the result was: 0 (neck and neck) .. 1 (crushing); negative when unknown. */
    public double margin() { return margin; }

    /** The winning clan, or null for a draw. */
    public UUID winnerClanId() {
        return switch (outcome) {
            case ATTACKER_WIN -> attackerClanId;
            case DEFENDER_WIN -> defenderClanId;
            case DRAW -> null;
        };
    }

    public UUID loserClanId() {
        return switch (outcome) {
            case ATTACKER_WIN -> defenderClanId;
            case DEFENDER_WIN -> attackerClanId;
            case DRAW -> null;
        };
    }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
