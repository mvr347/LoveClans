package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendFormatTest {
    @Test
    void comparePicksArrowBySide() {
        assertTrue(TrendFormat.compare(10, 5).contains("▲"));
        assertTrue(TrendFormat.compare(10, 5).contains("green"));
        assertTrue(TrendFormat.compare(5, 10).contains("▼"));
        assertTrue(TrendFormat.compare(5, 10).contains("red"));
        assertTrue(TrendFormat.compare(7, 7).contains("■"));
    }

    @Test
    void deltaIsSignedAndColoured() {
        assertEquals("<green>▲ +12</green>", TrendFormat.delta(12));
        assertEquals("<red>▼ −5</red>", TrendFormat.delta(-5));
        assertEquals("<gray>■ 0</gray>", TrendFormat.delta(0));
    }

    @Test
    void ratioHandlesZeroOpponent() {
        assertEquals("×1.4", TrendFormat.ratio(14, 10));
        assertEquals("×0.5", TrendFormat.ratio(5, 10));
        assertEquals("—", TrendFormat.ratio(5, 0));
        assertEquals("×1.0", TrendFormat.ratio(0, 0));
    }
}
