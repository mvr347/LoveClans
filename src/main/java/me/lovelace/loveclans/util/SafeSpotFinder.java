package me.lovelace.loveclans.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds a safe standing spot near a clan banner for the clan spawn: rings of growing radius (1..maxRadius)
 * around the banner column, feet and head open (passable, not liquid, not a hazard), solid safe ground below.
 * Block checks go through {@link BlockView} so the search itself is unit-testable without a server.
 */
public final class SafeSpotFinder {

    /** Read-only view of the world around the banner. Coordinates are absolute block coordinates. */
    public interface BlockView {
        /** Feet/head can be here: passable, not liquid, not fire/berry bush/powder snow and the like. */
        boolean isOpen(int x, int y, int z);

        /** Can be stood on: solid and not lava, magma, cactus, fire, campfire, powder snow, a banner. */
        boolean isSafeGround(int x, int y, int z);
    }

    public record Spot(int x, int y, int z) {}

    public static final int DEFAULT_RADIUS = 4;
    /** Vertical offsets tried for the feet, nearest to the banner's own height first. */
    private static final int[] DY = {0, 1, -1, 2, -2, 3, -3};

    private SafeSpotFinder() {}

    public static boolean isSafe(BlockView view, int x, int y, int z) {
        return view.isOpen(x, y, z) && view.isOpen(x, y + 1, z) && view.isSafeGround(x, y - 1, z);
    }

    public static Optional<Spot> find(BlockView view, int bannerX, int bannerY, int bannerZ, int maxRadius) {
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int[] offset : ring(radius)) {
                int x = bannerX + offset[0];
                int z = bannerZ + offset[1];
                for (int dy : DY) {
                    int y = bannerY + dy;
                    if (isSafe(view, x, y, z)) {
                        return Optional.of(new Spot(x, y, z));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Offsets on the square ring of the given radius, closest (by Euclidean distance) first, in a stable order. */
    static List<int[]> ring(int radius) {
        List<int[]> points = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    points.add(new int[]{dx, dz});
                }
            }
        }
        points.sort(Comparator.<int[]>comparingInt(p -> p[0] * p[0] + p[1] * p[1])
                .thenComparingInt(p -> p[0])
                .thenComparingInt(p -> p[1]));
        return points;
    }
}
