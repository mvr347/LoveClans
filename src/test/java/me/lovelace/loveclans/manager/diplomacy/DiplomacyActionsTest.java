package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions.Action;
import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions.Context;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiplomacyActionsTest {
    private static Context ctx(int score, boolean alliance) {
        return new Context(DiplomacyTier.fromScore(score, alliance), score, false, false, alliance);
    }

    private static Context withPacts(int score, boolean nap, boolean trade, boolean alliance) {
        return new Context(DiplomacyTier.fromScore(score, alliance), score, nap, trade, alliance);
    }

    @Test
    void hostileTierBlocksTradeAndGiftsButAllowsPressure() {
        Context hostile = ctx(-80, false);
        assertNotNull(DiplomacyActions.denyKey(Action.TRADE, hostile, 0));
        assertNotNull(DiplomacyActions.denyKey(Action.GIFT, hostile, 0));
        assertTrue(DiplomacyActions.allowed(Action.EMBARGO, hostile, 0));
        assertTrue(DiplomacyActions.allowed(Action.BLOCKADE, hostile, 0));
        assertTrue(DiplomacyActions.allowed(Action.CONFLICT, hostile, 0));
        assertTrue(DiplomacyActions.allowed(Action.IMPROVE, hostile, 0), "hostile relations can be improved");
    }

    @Test
    void blockadeNeedsHostileButEmbargoOnlyUnfriendly() {
        Context unfriendly = ctx(-30, false);
        assertTrue(DiplomacyActions.allowed(Action.EMBARGO, unfriendly, 0));
        assertFalse(DiplomacyActions.allowed(Action.BLOCKADE, unfriendly, 0));
        assertFalse(DiplomacyActions.allowed(Action.EMBARGO, ctx(0, false), 0));
    }

    @Test
    void goodRelationsDisableConflictsAndInsults() {
        for (int score : new int[]{20, 70}) {
            Context good = ctx(score, false);
            assertFalse(DiplomacyActions.allowed(Action.CONFLICT, good, 0), "conflict at " + score);
            assertFalse(DiplomacyActions.allowed(Action.EMBARGO, good, 0));
        }
        assertFalse(DiplomacyActions.allowed(Action.INSULT, ctx(70, false), 0));
        assertTrue(DiplomacyActions.allowed(Action.INSULT, ctx(30, false), 0));
        assertTrue(DiplomacyActions.allowed(Action.CONFLICT, ctx(0, false), 0));
    }

    @Test
    void napAndAllianceForbidConflictsAndSanctions() {
        Context nap = withPacts(0, true, false, false);
        assertEquals("diplomacy.deny.conflict-pact", DiplomacyActions.denyKey(Action.CONFLICT, nap, 0));
        assertEquals("diplomacy.deny.sanction-pact", DiplomacyActions.denyKey(Action.EMBARGO, withPacts(-40, true, false, false), 0));
        assertNotNull(DiplomacyActions.denyKey(Action.CONFLICT, withPacts(95, false, false, true), 0));
        assertNotNull(DiplomacyActions.denyKey(Action.INSULT, nap, 0));
    }

    @Test
    void pactsNeedTheirMinimumScore() {
        assertNotNull(DiplomacyActions.denyKey(Action.NAP, ctx(-30, false), -10));
        assertNull(DiplomacyActions.denyKey(Action.NAP, ctx(-10, false), -10));
        assertNotNull(DiplomacyActions.denyKey(Action.TRADE_PACT, ctx(19, false), 20));
        assertNull(DiplomacyActions.denyKey(Action.TRADE_PACT, ctx(20, false), 20));
        assertNotNull(DiplomacyActions.denyKey(Action.ALLIANCE, ctx(89, false), 90));
        assertNull(DiplomacyActions.denyKey(Action.ALLIANCE, ctx(90, false), 90));
    }

    @Test
    void alliedClansNeedNoMissions() {
        assertNotNull(DiplomacyActions.denyKey(Action.IMPROVE, ctx(95, true), 0));
        assertNull(DiplomacyActions.denyKey(Action.IMPROVE, ctx(95, false), 0));
    }
}
