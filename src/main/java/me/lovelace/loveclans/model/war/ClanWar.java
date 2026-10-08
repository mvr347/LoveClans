package me.lovelace.loveclans.model.war;

import me.lovelace.loveclans.model.TerritoryKey;

import java.util.UUID;

/**
 * Модель войны по новому дизайну (newwars-1.md):
 * - Счёт за киллы и доминирование на оспариваемой территории
 * - Подавление знамени (8 ударов, +25 очков, visual damaged, без disband!)
 * - Починка знамени защитниками за 60 секунд
 * - Репарации (дань) на 5 дней победителю
 */
public record ClanWar(
        UUID id,
        UUID attackerClanId,
        UUID defenderClanId,
        TerritoryKey contestedTerritory,
        long startedAt,
        long endsAt,
        WarState state,
        int attackerScore,
        int defenderScore,
        boolean bannerSuppressed,
        long bannerSuppressedAt,
        double bannerRepairProgress
) {
    public ClanWar(UUID id, UUID attackerClanId, UUID defenderClanId, TerritoryKey contestedTerritory, long startedAt, long endsAt, WarState state, int attackerScore, int defenderScore) {
        this(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore, false, 0L, 0.0);
    }

    public UUID capturedBannerBy() {
        return null;
    }

    public boolean isBannerSuppressed() {
        return bannerSuppressed;
    }

    public boolean involves(UUID clanId) {
        return attackerClanId.equals(clanId) || defenderClanId.equals(clanId);
    }

    public boolean between(UUID first, UUID second) {
        return (attackerClanId.equals(first) && defenderClanId.equals(second))
                || (attackerClanId.equals(second) && defenderClanId.equals(first));
    }

    public ClanWar withState(WarState state) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore, bannerSuppressed, bannerSuppressedAt, bannerRepairProgress);
    }

    public ClanWar addAttackerScore(int amount) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore + amount, defenderScore, bannerSuppressed, bannerSuppressedAt, bannerRepairProgress);
    }

    public ClanWar addDefenderScore(int amount) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore + amount, bannerSuppressed, bannerSuppressedAt, bannerRepairProgress);
    }

    public ClanWar withBannerSuppressed(boolean suppressed, long at) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore, suppressed, at, 0.0);
    }

    public ClanWar withRepairProgress(double progress) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore, bannerSuppressed, bannerSuppressedAt, progress);
    }

    public ClanWar withBannerRestored() {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, attackerScore, defenderScore, false, 0L, 0.0);
    }

    public ClanWar activate(long newEndsAt) {
        return new ClanWar(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, newEndsAt, WarState.ACTIVE, attackerScore, defenderScore, bannerSuppressed, bannerSuppressedAt, bannerRepairProgress);
    }
}