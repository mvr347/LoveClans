package me.lovelace.loveclans.manager.upgrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceMathTest {

    // Index = target level; the table from the spec (1.3 .. 3.2), 3.5 afterwards.
    private static final double[] FACTORS = {0, 0, 1.3, 1.4, 1.5, 1.7, 1.9, 2.2, 2.2, 2.5, 2.5, 2.8, 2.8, 3.2, 3.2, 3.2};

    private static long t(int level) {
        return ExperienceMath.thresholdFor(level, 200, 1.83, FACTORS, 3.5);
    }

    @Test
    void levelOneIsFree() {
        assertEquals(0L, t(1));
        assertEquals(0L, t(0));
    }

    @Test
    void firstStepIsOldPriceTimesFactor() {
        assertEquals(Math.round(200 * 1.3), t(2));
    }

    @Test
    void nextStepIsOldStepTimesItsFactor() {
        double oldStep = 200 * 1.83 - 200;
        assertEquals(Math.round(200 * 1.3 + oldStep * 1.4), t(3));
    }

    @Test
    void curveIsStrictlyIncreasing() {
        for (int level = 2; level <= 25; level++) {
            assertTrue(t(level) > t(level - 1), "level " + level);
        }
    }

    @Test
    void levelsPastTheTableUseTheTailFactor() {
        double old = 200 * Math.pow(1.83, 16 - 2);
        double previous = 200 * Math.pow(1.83, 15 - 2);
        assertEquals(t(15) + Math.round((old - previous) * 3.5), t(16), 1);
    }

    @Test
    void newCurveIsHarsherThanTheOldOne() {
        for (int level = 2; level <= 20; level++) {
            assertTrue(t(level) > Math.round(200 * Math.pow(1.83, level - 2)), "level " + level);
        }
    }

    @Test
    void taxIsFreeUpToTheThreshold() {
        assertEquals(1.0, ExperienceMath.taxFactor(1, 5, 0.03, 0.4));
        assertEquals(1.0, ExperienceMath.taxFactor(5, 5, 0.03, 0.4));
    }

    @Test
    void taxGrowsPerMemberAndStopsAtTheFloor() {
        assertEquals(0.85, ExperienceMath.taxFactor(10, 5, 0.03, 0.4), 1e-9);
        assertEquals(0.4, ExperienceMath.taxFactor(80, 5, 0.03, 0.4), 1e-9);
    }

    @Test
    void bonusesAddUpThenTaxApplies() {
        // 1000 * (1 + 0.07) * 0.85
        assertEquals(Math.round(1000 * 1.07 * 0.85), ExperienceMath.apply(1000, 7, 0.85));
        assertEquals(0L, ExperienceMath.apply(-5, 7, 0.85));
    }
}
