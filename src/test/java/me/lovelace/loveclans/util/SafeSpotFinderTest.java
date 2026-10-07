package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeSpotFinderTest {

    /** Fake grid: a flat floor at y=63, air above, plus explicit solid/hazard/liquid overrides. */
    private static final class Grid implements SafeSpotFinder.BlockView {
        final Set<String> solid = new HashSet<>();
        final Set<String> hazard = new HashSet<>();
        final Set<String> liquid = new HashSet<>();
        boolean floor = true;

        static String k(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        boolean solidAt(int x, int y, int z) {
            return solid.contains(k(x, y, z)) || (floor && y == 63);
        }

        @Override
        public boolean isOpen(int x, int y, int z) {
            String key = k(x, y, z);
            return !solidAt(x, y, z) && !hazard.contains(key) && !liquid.contains(key);
        }

        @Override
        public boolean isSafeGround(int x, int y, int z) {
            return solidAt(x, y, z) && !hazard.contains(k(x, y, z));
        }
    }

    @Test
    void findsAdjacentSpotOnFlatGround() {
        Grid grid = new Grid();
        Optional<SafeSpotFinder.Spot> spot = SafeSpotFinder.find(grid, 0, 64, 0, 4);
        assertTrue(spot.isPresent());
        assertEquals(64, spot.get().y());
        int distance = Math.max(Math.abs(spot.get().x()), Math.abs(spot.get().z()));
        assertEquals(1, distance);
        assertEquals(1, Math.abs(spot.get().x()) + Math.abs(spot.get().z()), "orthogonal neighbour preferred");
    }

    @Test
    void neverReturnsBannerColumn() {
        Grid grid = new Grid();
        SafeSpotFinder.Spot spot = SafeSpotFinder.find(grid, 5, 64, 5, 4).orElseThrow();
        assertTrue(spot.x() != 5 || spot.z() != 5);
    }

    @Test
    void skipsLavaFloorAndWater() {
        Grid grid = new Grid();
        // ring 1 entirely hazardous ground or flooded
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if ((dx + dz) % 2 == 0) grid.hazard.add(Grid.k(dx, 63, dz));
                else grid.liquid.add(Grid.k(dx, 64, dz));
            }
        }
        SafeSpotFinder.Spot spot = SafeSpotFinder.find(grid, 0, 64, 0, 4).orElseThrow();
        assertEquals(2, Math.max(Math.abs(spot.x()), Math.abs(spot.z())));
        assertEquals(64, spot.y());
    }

    @Test
    void needsHeadroom() {
        Grid grid = new Grid();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                grid.solid.add(Grid.k(dx, 65, dz)); // low ceiling over ring 1
            }
        }
        SafeSpotFinder.Spot spot = SafeSpotFinder.find(grid, 0, 64, 0, 4).orElseThrow();
        boolean ringOne = Math.max(Math.abs(spot.x()), Math.abs(spot.z())) == 1;
        // Under the low ceiling there is no headroom; standing on top of it (y=66) is fine.
        assertTrue(!ringOne || spot.y() == 66, "spot " + spot);
    }

    @Test
    void climbsOntoStep() {
        Grid grid = new Grid();
        grid.floor = false;
        grid.solid.add(Grid.k(1, 64, 0)); // only standable block: one up from the banner base
        SafeSpotFinder.Spot spot = SafeSpotFinder.find(grid, 0, 64, 0, 4).orElseThrow();
        assertEquals(new SafeSpotFinder.Spot(1, 65, 0), spot);
    }

    @Test
    void emptyWhenNothingSafe() {
        Grid grid = new Grid();
        grid.floor = false;
        assertTrue(SafeSpotFinder.find(grid, 0, 64, 0, 4).isEmpty());
    }

    @Test
    void ringHasExpectedSize() {
        assertEquals(8, SafeSpotFinder.ring(1).size());
        assertEquals(16, SafeSpotFinder.ring(2).size());
    }
}
