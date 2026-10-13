package me.lovelace.loveclans.manager.upgrade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferGeneratorTest {

    @Test
    void pairHasTwoDifferentOptions() {
        for (long seed = 0; seed < 200; seed++) {
            List<UpgradeOption> pair = OfferGenerator.pair(o -> true, new Random(seed));
            assertEquals(2, pair.size());
            assertFalse(pair.get(0) == pair.get(1));
        }
    }

    @Test
    void unavailableOptionsNeverAppear() {
        Set<UpgradeOption> banned = Set.of(UpgradeOption.MEMBERS, UpgradeOption.CHEST);
        for (long seed = 0; seed < 200; seed++) {
            List<UpgradeOption> pair = OfferGenerator.pair(o -> !banned.contains(o), new Random(seed));
            assertTrue(pair.stream().noneMatch(banned::contains));
        }
    }

    @Test
    void singleAvailableOptionGivesSingleChoice() {
        List<UpgradeOption> pair = OfferGenerator.pair(o -> o == UpgradeOption.TAX, new Random(1));
        assertEquals(List.of(UpgradeOption.TAX), pair);
    }

    @Test
    void nothingAvailableGivesNoOffer() {
        assertTrue(OfferGenerator.pair(o -> false, new Random(1)).isEmpty());
    }

    @Test
    void everyOptionCanBeDrawn() {
        Set<UpgradeOption> seen = new java.util.HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            seen.addAll(OfferGenerator.pair(o -> true, new Random(seed)));
        }
        assertEquals(Set.of(UpgradeOption.values()), seen);
    }
}
