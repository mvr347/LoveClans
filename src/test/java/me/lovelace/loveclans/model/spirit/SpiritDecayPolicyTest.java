package me.lovelace.loveclans.model.spirit;

import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy.Config;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy.Result;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy.State;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy.Tier;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy.TierConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritDecayPolicyTest {
    private static final Config CFG = new Config(
            new TierConfig(50, 1, 0),
            new TierConfig(15, 6, 0),
            new TierConfig(1, 12, 0.15),
            new TierConfig(0, 20, 0.4),
            0, 5);

    private static long exp(int level) {
        return level * 3000L;
    }

    @Test
    void tiersFollowTheScore() {
        assertEquals(Tier.HIGH, SpiritDecayPolicy.tier(80, false, CFG));
        assertEquals(Tier.MEDIUM, SpiritDecayPolicy.tier(20, false, CFG));
        assertEquals(Tier.LOW, SpiritDecayPolicy.tier(3, false, CFG));
        assertEquals(Tier.NONE, SpiritDecayPolicy.tier(0.2, false, CFG));
        assertEquals(Tier.NONE, SpiritDecayPolicy.tier(500, true, CFG), "nobody around beats any score");
    }

    @Test
    void idleClansLoseEnergyFasterThanActiveOnes() {
        State start = new State(5, 10_000, 0, 0);
        long active = SpiritDecayPolicy.apply(start, Tier.HIGH, 24, false, CFG, SpiritDecayPolicyTest::exp).state().energy();
        long lazy = SpiritDecayPolicy.apply(start, Tier.LOW, 24, false, CFG, SpiritDecayPolicyTest::exp).state().energy();
        long dead = SpiritDecayPolicy.apply(start, Tier.NONE, 24, false, CFG, SpiritDecayPolicyTest::exp).state().energy();
        assertTrue(active > lazy && lazy > dead, active + " > " + lazy + " > " + dead);
        assertEquals(9_900, active, "1% of 10000 per day");
        assertEquals(8_000, dead, "20% per day");
    }

    @Test
    void ratesAreScaledToTheInterval() {
        State start = new State(5, 10_000, 0, 0);
        long hourly = SpiritDecayPolicy.apply(start, Tier.NONE, 1, false, CFG, SpiritDecayPolicyTest::exp).state().energy();
        assertEquals(10_000 - Math.round(10_000 * 0.20 / 24), hourly);
    }

    @Test
    void powerOnlyErodesOnceEnergyIsGone() {
        State wet = new State(5, 100, 0.9, 0);
        Result r = SpiritDecayPolicy.apply(wet, Tier.NONE, 24, false, CFG, SpiritDecayPolicyTest::exp);
        assertEquals(5, r.state().level());
        assertFalse(r.levelLost());

        State dry = new State(5, 0, 0.8, 0);
        r = SpiritDecayPolicy.apply(dry, Tier.NONE, 12, false, CFG, SpiritDecayPolicyTest::exp);
        assertTrue(r.levelLost(), "0.8 + 0.4 * 0.5 crosses 1.0");
        assertEquals(4, r.state().level());
        assertEquals(0.0, r.state().lossProgress(), 1e-9);
    }

    @Test
    void activeClanNeverLosesPowerAndRespectsTheFloor() {
        State dry = new State(5, 0, 0.99, 0);
        assertEquals(5, SpiritDecayPolicy.apply(dry, Tier.HIGH, 240, false, CFG, SpiritDecayPolicyTest::exp).state().level());
        State floor = new State(0, 0, 0, 0);
        assertEquals(0, SpiritDecayPolicy.apply(floor, Tier.NONE, 240, false, CFG, SpiritDecayPolicyTest::exp).state().level());
    }

    @Test
    void masteryDriftsOnlyWhenIdleAndOnANewDay() {
        State s = new State(5, 1000, 0, 40);
        assertEquals(35, SpiritDecayPolicy.apply(s, Tier.LOW, 1, true, CFG, SpiritDecayPolicyTest::exp).state().mastery());
        assertEquals(40, SpiritDecayPolicy.apply(s, Tier.LOW, 1, false, CFG, SpiritDecayPolicyTest::exp).state().mastery());
        assertEquals(40, SpiritDecayPolicy.apply(s, Tier.HIGH, 1, true, CFG, SpiritDecayPolicyTest::exp).state().mastery());
    }

    @Test
    void scoreHalvesEveryHalfLife() {
        double after = SpiritDecayPolicy.decayedScore(80, 0, 24L * 3_600_000L, 24);
        assertEquals(40, after, 1e-6);
        assertEquals(80, SpiritDecayPolicy.decayedScore(80, 5, 5, 24), 1e-9);
    }
}
