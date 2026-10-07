package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CycleButtonTest {
    private enum Mode { A, B, C, D }

    @Test
    void stepsForwardAndBackOnARing() {
        assertEquals(Mode.B, CycleButton.step(Mode.A, Mode.values(), true));
        assertEquals(Mode.A, CycleButton.step(Mode.D, Mode.values(), true));
        assertEquals(Mode.D, CycleButton.step(Mode.A, Mode.values(), false));
        assertEquals(Mode.B, CycleButton.step(Mode.C, Mode.values(), false));
    }

    @Test
    void skipsOptionsThatAreNotAllowed() {
        assertEquals(Mode.D, CycleButton.step(Mode.B, Mode.values(), true, m -> m != Mode.C));
        assertEquals(Mode.A, CycleButton.step(Mode.D, Mode.values(), false, m -> m != Mode.B && m != Mode.C));
        assertEquals(Mode.A, CycleButton.step(Mode.A, Mode.values(), true, m -> m == Mode.A));
    }

    @Test
    void stepIndexWraps() {
        assertEquals(0, CycleButton.stepIndex(2, 3, true));
        assertEquals(2, CycleButton.stepIndex(0, 3, false));
    }
}
