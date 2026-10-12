package me.lovelace.loveclans.model.diplomacy;

import java.util.Objects;
import java.util.UUID;

/**
 * Запись активного пакта между двумя кланами (§5).
 */
public record ClanPact(
        UUID id,
        UUID firstClanId,
        UUID secondClanId,
        PactType type,
        long createdAt,
        long expiresAt
) {
    public ClanPact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(firstClanId, "firstClanId");
        Objects.requireNonNull(secondClanId, "secondClanId");
        Objects.requireNonNull(type, "type");
    }

    public static ClanPact create(UUID firstClanId, UUID secondClanId, PactType type, long expiresAt) {
        long now = System.currentTimeMillis();
        return new ClanPact(UUID.randomUUID(), firstClanId, secondClanId, type, now, expiresAt);
    }

    public static ClanPact createPermanent(UUID firstClanId, UUID secondClanId, PactType type) {
        return create(firstClanId, secondClanId, type, 0L);
    }

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && now >= expiresAt;
    }

    public boolean involves(UUID clanId) {
        return firstClanId.equals(clanId) || secondClanId.equals(clanId);
    }

    public boolean matchesPair(UUID a, UUID b) {
        return (firstClanId.equals(a) && secondClanId.equals(b)) || (firstClanId.equals(b) && secondClanId.equals(a));
    }

    public UUID otherClan(UUID clanId) {
        return firstClanId.equals(clanId) ? secondClanId : firstClanId;
    }

    public UUID otherClanId(UUID clanId) {
        return otherClan(clanId);
    }

    public long remainingMillis(long now) {
        return Math.max(0L, expiresAt - now);
    }
}
