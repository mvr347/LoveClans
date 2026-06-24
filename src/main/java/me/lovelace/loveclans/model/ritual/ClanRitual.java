package me.lovelace.loveclans.model.ritual;

import java.util.UUID;

public sealed interface ClanRitual permits HarvestRitual, WarDrumRitual, GuardianRitual {
    UUID clanId();

    RitualType type();

    long startedAt();

    long endsAt();

    default boolean active(long now) {
        return startedAt() <= now && endsAt() > now;
    }
}
