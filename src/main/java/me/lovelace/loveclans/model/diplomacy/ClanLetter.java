package me.lovelace.loveclans.model.diplomacy;

import java.util.UUID;

/** A letter between two clans (§5.4) — no restrictions on when it can be sent, unlike trade/embargo/blockade actions. */
public record ClanLetter(UUID id, UUID fromClanId, UUID toClanId, String message, boolean read, long createdAt) {

    public ClanLetter markRead() {
        return new ClanLetter(id, fromClanId, toClanId, message, true, createdAt);
    }
}
