package me.lovelace.loveclans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GiftRulesTest {
    @Test
    void allowedWhenEverythingFits() {
        assertNull(GiftRules.deny(true, 5_000, 100_000, 0, 10_000));
    }

    @Test
    void disabledAndCooldownBeatAmountChecks() {
        assertEquals("deny.gift.disabled", GiftRules.deny(false, 1, 100, 0, 1000));
        assertEquals("deny.gift.cooldown", GiftRules.deny(true, 1_000_000, 100, 30, 0));
    }

    @Test
    void tooLargeIsReportedBeforeMissingFunds() {
        assertEquals("deny.gift.too-large", GiftRules.deny(true, 200_000, 100_000, 0, 10));
        assertEquals("deny.gift.insufficient-funds", GiftRules.deny(true, 5_000, 100_000, 0, 10));
    }
}
