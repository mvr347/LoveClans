package me.lovelace.loveclans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerkManagerTest {
    @Test
    void siegeReductionShortensDuration() {
        assertEquals(16_500L, PerkManager.reduceByPercent(22_000L, 25));
        assertEquals(22_000L, PerkManager.reduceByPercent(22_000L, 0));
    }

    @Test
    void reductionIsClampedSoSiegeNeverCollapses() {
        assertEquals(2_200L, PerkManager.reduceByPercent(22_000L, 100));
        assertEquals(22_000L, PerkManager.reduceByPercent(22_000L, -50));
    }
}
