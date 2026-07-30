package me.lovelace.loveclans.model;

import java.util.UUID;

/**
 * Клановая территория. Геометрия здесь не хранится — источник истины один: приват LoveClaims,
 * на который указывает {@code advancedClaimId}. Раньше рамка (min/max) дублировалась и здесь, и
 * в LoveClaims, обе стороны считали её одной и той же формулой (центр знамени ± радиус) в двух
 * независимых местах — если между двумя вычислениями менялся конфиг радиуса, рамки расходились.
 * Теперь клановая территория без привата не существует: {@link
 * me.lovelace.loveclans.integration.AdvancedClaimsHook#createOrAttachClaim} обязателен для
 * захвата земли, а геометрию для войн/осад/защиты берут через
 * {@link me.lovelace.loveclans.integration.AdvancedClaimsHook#boundingBoxOf(ClanTerritory)}.
 */
public record ClanTerritory(
        UUID id,
        UUID clanId,
        String world,
        UUID advancedClaimId,
        UUID claimedBy,
        long claimedAt,
        Integer bannerX,
        Integer bannerY,
        Integer bannerZ,
        String name,
        boolean pvp,
        boolean capital
) {
    /** Территория без привязки к привату LoveClaims — до {@link #withAdvancedClaimId}. */
    public ClanTerritory(UUID clanId, String world, UUID claimedBy, long claimedAt) {
        this(UUID.randomUUID(), clanId, world, null, claimedBy, claimedAt, null, null, null, null, false, false);
    }

    /**
     * Чанк знамени — стабильный идентификатор территории для сравнений и поиска
     * (война, отказ от территории, GUI). Не претендует на то, чтобы покрывать всю площадь —
     * для этого нужна полная геометрия, см. {@code AdvancedClaimsHook.boundingBoxOf}.
     *
     * <p>Без координат знамени (не должно происходить через обычный поток захвата, но код в
     * нескольких местах уже защищается от этого случая) возвращает чанк (0,0) — вырожденное,
     * но не падающее значение: сравнения с реальным положением игрока просто не совпадут.</p>
     */
    public TerritoryKey key() {
        int x = bannerX != null ? bannerX : 0;
        int z = bannerZ != null ? bannerZ : 0;
        return new TerritoryKey(world, x >> 4, z >> 4);
    }

    public boolean isCapital() {
        return capital;
    }

    public ClanTerritory withAdvancedClaimId(UUID advancedClaimId) {
        return new ClanTerritory(id, clanId, world, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withBannerCoords(Integer x, Integer y, Integer z) {
        return new ClanTerritory(id, clanId, world, advancedClaimId, claimedBy, claimedAt, x, y, z, name, pvp, capital);
    }

    public ClanTerritory withName(String name) {
        return new ClanTerritory(id, clanId, world, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withPvp(boolean pvp) {
        return new ClanTerritory(id, clanId, world, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withCapital(boolean capital) {
        return new ClanTerritory(id, clanId, world, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }
}
