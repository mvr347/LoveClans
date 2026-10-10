package me.lovelace.loveclans.util;

public final class DurationFormat {
    private DurationFormat() {}

    /** Человекочитаемая длительность для lang placeholder <time>. */
    public static String format(long millis) {
        long sec = Math.max(1L, (millis + 999L) / 1000L); // ceil, минимум 1 сек
        long days = sec / 86400L;
        long hours = (sec % 86400L) / 3600L;
        long mins = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (days > 0L) {
            return days + " д. " + hours + " ч.";
        }
        if (hours > 0L) {
            return hours + " ч. " + mins + " мин.";
        }
        if (mins > 0L) {
            return mins + " мин. " + s + " сек.";
        }
        return s + " сек.";
    }
}
