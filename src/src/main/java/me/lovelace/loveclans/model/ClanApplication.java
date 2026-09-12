package me.lovelace.loveclans.model;

import java.util.UUID;

public record ClanApplication(UUID clanId, UUID applicantId, long appliedAt) {
    public boolean expired(long now, long expireTimeMillis) {
        return now > appliedAt + expireTimeMillis;
    }
}