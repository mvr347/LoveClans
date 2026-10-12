package me.lovelace.loveclans.model.spirit;

import me.lovelace.loveclans.model.spirit.MasteryLedger.Caps;
import me.lovelace.loveclans.model.spirit.MasteryLedger.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryLedgerTest {
    private static final Caps CAPS = new Caps(15, 15);

    @Test
    void offenceRaisesAndDefenceLowers() {
        State s = MasteryLedger.apply(State.neutral(), 8, 100, CAPS);
        assertEquals(8, s.mastery());
        s = MasteryLedger.apply(s, -5, 100, CAPS);
        assertEquals(3, s.mastery());
    }

    @Test
    void dailyCapStopsGrinding() {
        State s = MasteryLedger.apply(State.neutral(), 40, 100, CAPS);
        assertEquals(15, s.mastery());
        s = MasteryLedger.apply(s, 5, 100, CAPS);
        assertEquals(15, s.mastery(), "cap already used up today");
    }

    @Test
    void newDayResetsCounters() {
        State s = MasteryLedger.apply(State.neutral(), 40, 100, CAPS);
        s = MasteryLedger.apply(s, 10, 101, CAPS);
        assertEquals(25, s.mastery());
        assertEquals(10, s.offenseToday());
    }

    @Test
    void sidesHaveSeparateCaps() {
        State s = MasteryLedger.apply(State.neutral(), 15, 100, CAPS);
        s = MasteryLedger.apply(s, -15, 100, CAPS);
        assertEquals(0, s.mastery());
    }

    @Test
    void clampsAtTheLimits() {
        State s = new State(98, 100, 0, 0);
        assertEquals(100, MasteryLedger.apply(s, 10, 100, new Caps(50, 50)).mastery());
        s = new State(-98, 100, 0, 0);
        assertEquals(-100, MasteryLedger.apply(s, -10, 100, new Caps(50, 50)).mastery());
    }

    @Test
    void driftApproachesZeroWithoutOvershoot() {
        assertEquals(5, MasteryLedger.driftToNeutral(10, 5));
        assertEquals(0, MasteryLedger.driftToNeutral(3, 5));
        assertEquals(-5, MasteryLedger.driftToNeutral(-10, 5));
        assertEquals(0, MasteryLedger.driftToNeutral(-3, 5));
    }
}
