package me.lovelace.loveclans.api.events;

import me.lovelace.loveclans.model.history.ConflictKind;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A war, raid or siege was registered (it may still be in its preparation phase). Fired on the main thread. */
public final class ClanConflictDeclaredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ConflictKind kind;
    private final UUID conflictId;
    private final UUID attackerClanId;
    private final UUID defenderClanId;

    public ClanConflictDeclaredEvent(ConflictKind kind, UUID conflictId, UUID attackerClanId, UUID defenderClanId) {
        this.kind = kind;
        this.conflictId = conflictId;
        this.attackerClanId = attackerClanId;
        this.defenderClanId = defenderClanId;
    }

    public ConflictKind kind() { return kind; }
    public UUID conflictId() { return conflictId; }
    public UUID attackerClanId() { return attackerClanId; }
    public UUID defenderClanId() { return defenderClanId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
