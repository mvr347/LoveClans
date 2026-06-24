package me.lovelace.loveclans.api.events;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ClanMemberJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Clan clan;
    private final ClanMember member;

    public ClanMemberJoinEvent(Clan clan, ClanMember member) {
        this.clan = clan;
        this.member = member;
    }

    public Clan clan() {
        return clan;
    }

    public ClanMember member() {
        return member;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
