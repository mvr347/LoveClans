package me.lovelace.loveclans.util;

import java.util.Locale;

/**
 * MiniMessage snippets for "better / worse / same" markers used in lore: green up arrow for a gain, red down arrow for
 * a loss, gray square for no change. Pure strings so they can be unit-tested and dropped into lang placeholders.
 */
public final class TrendFormat {
    private static final String UP = "<green>▲</green>";
    private static final String DOWN = "<red>▼</red>";
    private static final String SAME = "<gray>■</gray>";

    private TrendFormat() {
    }

    /** Arrow for "ours versus theirs": up when we are ahead, down when behind, a square when equal. */
    public static String compare(long ours, long theirs) {
        if (ours > theirs) return UP;
        if (ours < theirs) return DOWN;
        return SAME;
    }

    /** Signed change, e.g. {@code ▲ +12} in green or {@code ▼ −5} in red. */
    public static String delta(long change) {
        if (change > 0) return "<green>▲ +" + change + "</green>";
        if (change < 0) return "<red>▼ −" + Math.abs(change) + "</red>";
        return "<gray>■ 0</gray>";
    }

    /** "ours / theirs" as a multiplier with one decimal, e.g. {@code ×1.4}; a dash when the other side is zero. */
    public static String ratio(long ours, long theirs) {
        if (theirs <= 0) return ours <= 0 ? "×1.0" : "—";
        return "×" + String.format(Locale.ROOT, "%.1f", ours / (double) theirs);
    }
}
