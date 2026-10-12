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

    @Test
    void friendlyClanSeesWarmingButNoPressure() {
        Context friendly = ctx(70, false);
        assertTrue(DiplomacyActions.relevant(Action.GIFT, friendly, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.TRADE, friendly, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.NAP, friendly, -10, 10));
        assertTrue(DiplomacyActions.relevant(Action.TRADE_PACT, friendly, 20, 10));
        assertFalse(DiplomacyActions.relevant(Action.EMBARGO, friendly, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.BLOCKADE, friendly, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.CONFLICT, friendly, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.INSULT, friendly, 0, 10));
    }

    @Test
    void hostileClanSeesPressureButNoPactsOrGifts() {
        Context hostile = ctx(-80, false);
        assertFalse(DiplomacyActions.relevant(Action.GIFT, hostile, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.TRADE, hostile, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.NAP, hostile, -10, 10));
        assertFalse(DiplomacyActions.relevant(Action.ALLIANCE, hostile, 90, 10));
        assertTrue(DiplomacyActions.relevant(Action.EMBARGO, hostile, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.BLOCKADE, hostile, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.CONFLICT, hostile, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.INSULT, hostile, 0, 10));
        assertTrue(DiplomacyActions.relevant(Action.IMPROVE, hostile, 0, 10));
    }

    @Test
    void pactOfferStaysVisibleWithinHintMargin() {
        assertTrue(DiplomacyActions.relevant(Action.TRADE_PACT, ctx(12, false), 20, 10), "8 points short is a visible goal");
        assertFalse(DiplomacyActions.relevant(Action.TRADE_PACT, ctx(5, false), 20, 10));
        assertFalse(DiplomacyActions.relevant(Action.ALLIANCE, ctx(60, false), 90, 10));
    }

    @Test
    void allianceHidesImproveAndInsult() {
        Context allied = withPacts(95, false, false, true);
        assertFalse(DiplomacyActions.relevant(Action.IMPROVE, allied, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.INSULT, allied, 0, 10));
        assertFalse(DiplomacyActions.relevant(Action.CONFLICT, allied, 0, 10));
    }
}
