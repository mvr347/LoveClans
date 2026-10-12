package me.lovelace.loveclans.model.spirit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefeatPowerLossTest {
    private static final DefeatPowerLoss.Config CFG = new DefeatPowerLoss.Config(2, 1, 8, 0.2);

    @Test
    void closeLossOfAStrongSpiritIsSoft() {
        assertEquals(1, DefeatPowerLoss.compute(9, 0.1, CFG));
    }

    @Test
    void weakOrCrushedPaysBase() {
        assertEquals(2, DefeatPowerLoss.compute(5, 0.1, CFG), "power below the soft threshold");
        assertEquals(2, DefeatPowerLoss.compute(9, 0.6, CFG), "not close");
        assertEquals(2, DefeatPowerLoss.compute(9, -1, CFG), "unknown margin");
    }

    @Test
    void neverBelowZero() {
        assertEquals(1, DefeatPowerLoss.compute(1, 0.9, CFG));
        assertEquals(0, DefeatPowerLoss.compute(0, 0.9, CFG));
    }
}
