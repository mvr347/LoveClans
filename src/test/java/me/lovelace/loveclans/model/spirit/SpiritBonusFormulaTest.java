package me.lovelace.loveclans.model.spirit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritBonusFormulaTest {
    @Test
    void stepNeedsBothPowerAndMastery() {
        assertEquals(0, SpiritBonusFormula.step(10, 19), "mastery too low");
        assertEquals(0, SpiritBonusFormula.step(0, 100), "no power");
        assertEquals(1, SpiritBonusFormula.step(1, 100));
        assertEquals(5, SpiritBonusFormula.step(10, 100));
        assertEquals(5, SpiritBonusFormula.step(10, -100));
        assertEquals(2, SpiritBonusFormula.step(10, 40));
        assertEquals(3, SpiritBonusFormula.step(5, 100), "power 5 caps at ceil(5/2) = 3");
    }

    @Test
    void sideFollowsTheSignOfMastery() {
        assertEquals(SpiritBonusFormula.Side.OFFENSE, SpiritBonusFormula.side(1));
        assertEquals(SpiritBonusFormula.Side.DEFENSE, SpiritBonusFormula.side(-1));
        assertEquals(SpiritBonusFormula.Side.NONE, SpiritBonusFormula.side(0));
    }

    @Test
    void damageBonusGrowsLinearlyFiveToTwentyFive() {
        double[] expected = {0, 5, 10, 15, 20, 25};
        for (int step = 0; step <= 5; step++) {
            assertEquals(expected[step], SpiritBonusFormula.value(1, 5, step), 1e-9);
        }
    }

    @Test
    void laterBonusesStartAtTheirUnlockStep() {
        assertEquals(0, SpiritBonusFormula.value(4, 10, 3), 1e-9);
        assertEquals(10, SpiritBonusFormula.value(4, 10, 4), 1e-9);
        assertEquals(20, SpiritBonusFormula.value(4, 10, 5), 1e-9);
    }

    @Test
    void overloadAtTheThreshold() {
        assertFalse(SpiritBonusFormula.overloaded(89, 90));
        assertTrue(SpiritBonusFormula.overloaded(90, 90));
        assertTrue(SpiritBonusFormula.overloaded(-95, 90));
    }
}
