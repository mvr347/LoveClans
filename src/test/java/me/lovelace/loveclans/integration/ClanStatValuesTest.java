package me.lovelace.loveclans.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClanStatValuesTest {
    @Test
    void joinTimeInMillisecondsBecomesADate() {
        // 2026-10-30 12:00:00 UTC
        assertEquals("30.10.2026", ClanStatValues.date(1793361600000L, ZoneId.of("UTC")));
    }

    @Test
    void anUnsetTimeIsEmptyNotTheEpoch() {
        assertEquals("", ClanStatValues.date(0, ZoneId.of("UTC")));
        assertEquals("", ClanStatValues.date(-5, ZoneId.of("UTC")));
    }
}
