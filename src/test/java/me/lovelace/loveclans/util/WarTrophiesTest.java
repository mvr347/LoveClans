package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarTrophiesTest {
    @Test
    void percentOfBalance() {
        assertEquals(100L, WarTrophies.amount(1000, 10, 0));
    }

    @Test
    void capLimitsPrize() {
        assertEquals(50L, WarTrophies.amount(1000, 10, 50));
    }

    @Test
    void emptyOrDisabledGivesNothing() {
        assertEquals(0L, WarTrophies.amount(0, 10, 0));
        assertEquals(0L, WarTrophies.amount(1000, 0, 0));
    }

    @Test
    void percentOver100IsClamped() {
        assertEquals(1000L, WarTrophies.amount(1000, 500, 0));
    }
}
