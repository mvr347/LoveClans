package me.lovelace.loveclans.manager.diplomacy;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Ordering of the clan browser. Whatever the mode, clans in an open conflict with the viewer's clan come first, because
 * that is the relationship that needs attention; the tag breaks every remaining tie so the list never jumps around.
 */
public final class DiplomacySort {
    private DiplomacySort() {
    }

    public enum Mode { RELATION, TAG, ONLINE, INFLUENCE }

    /** What the sort needs to know about one clan, already resolved against the viewer's clan. */
    public record Row(String tag, int score, int online, long influence, boolean inConflict) {
    }

    public static Comparator<Row> comparator(Mode mode) {
        Collator collator = Collator.getInstance(Locale.ROOT);
        Comparator<Row> byTag = (a, b) -> collator.compare(a.tag(), b.tag());
        Comparator<Row> primary = switch (mode) {
            case RELATION -> Comparator.comparingInt(Row::score).reversed();
            case TAG -> byTag;
            case ONLINE -> Comparator.comparingInt(Row::online).reversed();
            case INFLUENCE -> Comparator.comparingLong(Row::influence).reversed();
        };
        Comparator<Row> conflictFirst = Comparator.comparing((Row r) -> !r.inConflict());
        return conflictFirst.thenComparing(primary).thenComparing(byTag);
    }
}
