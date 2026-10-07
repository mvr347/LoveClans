package me.lovelace.loveclans.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** {@link SafeSpotFinder.BlockView} over a live world; main thread only. */
public final class BukkitBlockView implements SafeSpotFinder.BlockView {
    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE, Material.COBWEB, Material.WITHER_ROSE, Material.NETHER_PORTAL,
            Material.END_PORTAL);

    private final World world;

    public BukkitBlockView(World world) {
        this.world = world;
    }

    private boolean inHeight(int y) {
        return y >= world.getMinHeight() && y < world.getMaxHeight();
    }

    @Override
    public boolean isOpen(int x, int y, int z) {
        if (!inHeight(y)) return false;
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(type) && !type.name().endsWith("_BANNER");
    }

    @Override
    public boolean isSafeGround(int x, int y, int z) {
        if (!inHeight(y)) return false;
        Material type = world.getBlockAt(x, y, z).getType();
        return type.isSolid() && !HAZARDS.contains(type) && !type.name().endsWith("_BANNER");
    }

    /** A safe spawn next to the banner, facing it, or empty if the surroundings have none. */
    public static Optional<Location> spawnNear(World world, int bannerX, int bannerY, int bannerZ) {
        return SafeSpotFinder.find(new BukkitBlockView(world), bannerX, bannerY, bannerZ, SafeSpotFinder.DEFAULT_RADIUS)
                .map(spot -> facing(new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5), bannerX, bannerY, bannerZ));
    }

    /** Whether a stored spawn is still safe to land on. */
    public static boolean isStillSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return SafeSpotFinder.isSafe(new BukkitBlockView(location.getWorld()),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Last resort: top of the column next to the banner. */
    public static Location highestNear(World world, int bannerX, int bannerY, int bannerZ) {
        int x = bannerX + 1;
        int y = world.getHighestBlockYAt(x, bannerZ) + 1;
        return facing(new Location(world, x + 0.5, y, bannerZ + 0.5), bannerX, bannerY, bannerZ);
    }

    private static Location facing(Location from, int bannerX, int bannerY, int bannerZ) {
        Vector direction = new Vector(bannerX + 0.5 - from.getX(), 0, bannerZ + 0.5 - from.getZ());
        if (direction.lengthSquared() > 1.0E-6) {
            from.setDirection(direction);
        }
        return from;
    }
}
