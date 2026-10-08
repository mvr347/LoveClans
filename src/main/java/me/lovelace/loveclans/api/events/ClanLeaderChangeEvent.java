package me.lovelace.loveclans.api.events;

import me.lovelace.loveclans.model.Clan;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Leadership moved to another member (handover or a succession vote). */
public final class ClanLeaderChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Clan clan;
    private final UUID oldLeaderId;
    private final UUID newLeaderId;

    public ClanLeaderChangeEvent(Clan clan, UUID oldLeaderId, UUID newLeaderId) {
        this.clan = clan;
        this.oldLeaderId = oldLeaderId;
        this.newLeaderId = newLeaderId;
    }

    public Clan clan() { return clan; }
    public UUID oldLeaderId() { return oldLeaderId; }
    public UUID newLeaderId() { return newLeaderId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
