package me.lovelace.loveclans.api.events;

import me.lovelace.loveclans.model.Clan;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A player put coins into the clan treasury. Fired after the balance changed, on the main thread. */
public final class ClanTreasuryDepositEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Clan clan;
    private final UUID actorId;
    private final long amount;

    public ClanTreasuryDepositEvent(Clan clan, UUID actorId, long amount) {
        this.clan = clan;
        this.actorId = actorId;
        this.amount = amount;
    }

    public Clan clan() { return clan; }
    public UUID actorId() { return actorId; }
    public long amount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
