package me.lovelace.loveclans.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

/**
 * Storage key of an activity period. Weeks follow ISO-8601 (Monday first, week-based year), so a week that
 * straddles New Year still has one key. UTC keeps the keys identical on every server and in tests.
 */
public final class PeriodKeys {
    public static final String LIFETIME = "L";

    private PeriodKeys() {
    }

    public static String of(ActivityPeriod period, long epochMillis) {
        LocalDate day = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
        return switch (period) {
            case LIFETIME -> LIFETIME;
            case WEEKLY -> String.format("W%d-%02d", day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> String.format("M%d-%02d", day.getYear(), day.getMonthValue());
        };
    }
}
