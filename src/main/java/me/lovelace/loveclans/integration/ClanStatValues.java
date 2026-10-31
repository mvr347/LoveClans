package me.lovelace.loveclans.integration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formatting of clan values handed to other plugins through PlaceholderAPI; no server types. */
final class ClanStatValues {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ClanStatValues() {}

    /** A join time in epoch milliseconds as a date; empty for an unset (zero or negative) time. */
    static String date(long epochMillis) {
        return date(epochMillis, ZoneId.systemDefault());
    }

    static String date(long epochMillis, ZoneId zone) {
        if (epochMillis <= 0) return "";
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
