package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreBarTest {
    private static long cells(String bar) {
        return bar.chars().filter(c -> c == '█').count();
    }

    @Test
    void alwaysSixteenCells() {
        assertEquals(LoreBar.CELLS, cells(LoreBar.bar(0, 100)));
        assertEquals(LoreBar.CELLS, cells(LoreBar.bar(37, 100)));
        assertEquals(LoreBar.CELLS, cells(LoreBar.bar(100, 100)));
    }

    @Test
    void partialBarIsGoldWithPercent() {
        String bar = LoreBar.bar(50, 100);
        assertTrue(bar.contains("<gold>████████</gold>"), bar);
        assertTrue(bar.contains("50%"), bar);
    }

    @Test
    void completeBarIsGreen() {
        String bar = LoreBar.bar(5000, 5000);
        assertTrue(bar.contains("<green>" + "█".repeat(16) + "</green>"), bar);
        assertTrue(bar.contains("100%"), bar);
    }

    @Test
    void clampsAndSurvivesBadInput() {
        assertEquals(100, LoreBar.percent(900, 100));
        assertEquals(0, LoreBar.percent(-5, 100));
        assertEquals(0, LoreBar.percent(5, 0));
        assertTrue(LoreBar.bar(5, 0).contains("0%"));
    }

    @Test
    void explicitColorIsUsed() {
        assertTrue(LoreBar.bar(10, 20, "red").contains("<red>"));
    }
}
