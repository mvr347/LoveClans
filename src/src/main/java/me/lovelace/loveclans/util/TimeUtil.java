package me.lovelace.loveclans.util;

import java.time.Duration;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    /** Same as {@link #formatDuration}, but at second granularity - for short (sub-hour) countdowns. */
    public static String formatDurationSeconds(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
