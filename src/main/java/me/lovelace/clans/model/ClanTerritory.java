package me.lovelace.clans.model;

import org.bukkit.util.BoundingBox;

import java.util.UUID;

public record ClanTerritory(
        UUID id,
        UUID clanId,
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
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
    public ClanTerritory(UUID clanId, String world, BoundingBox box, UUID claimedBy, long claimedAt) {
        this(UUID.randomUUID(), clanId, world,
                (int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ(),
                (int) box.getMaxX(), (int) box.getMaxY(), (int) box.getMaxZ(),
                null, claimedBy, claimedAt, null, null, null, null, false, false);
    }

    public TerritoryKey key() {
        return new TerritoryKey(world, minX >> 4, minZ >> 4);
    }

    public BoundingBox boundingBox() {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isCapital() {
        return capital;
    }

    public ClanTerritory withAdvancedClaimId(UUID advancedClaimId) {
        return new ClanTerritory(id, clanId, world, minX, minY, minZ, maxX, maxY, maxZ, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withBannerCoords(Integer x, Integer y, Integer z) {
        return new ClanTerritory(id, clanId, world, minX, minY, minZ, maxX, maxY, maxZ, advancedClaimId, claimedBy, claimedAt, x, y, z, name, pvp, capital);
    }

    public ClanTerritory withName(String name) {
        return new ClanTerritory(id, clanId, world, minX, minY, minZ, maxX, maxY, maxZ, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withPvp(boolean pvp) {
        return new ClanTerritory(id, clanId, world, minX, minY, minZ, maxX, maxY, maxZ, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }

    public ClanTerritory withCapital(boolean capital) {
        return new ClanTerritory(id, clanId, world, minX, minY, minZ, maxX, maxY, maxZ, advancedClaimId, claimedBy, claimedAt, bannerX, bannerY, bannerZ, name, pvp, capital);
    }
}