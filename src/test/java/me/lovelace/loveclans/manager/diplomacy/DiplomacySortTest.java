package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.manager.diplomacy.DiplomacySort.Mode;
import me.lovelace.loveclans.manager.diplomacy.DiplomacySort.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiplomacySortTest {
    private static final Row ALLY = new Row("BBB", 85, 1, 300, false);
    private static final Row NEUTRAL = new Row("CCC", 0, 5, 100, false);
    private static final Row ENEMY = new Row("AAA", -70, 3, 900, false);
    private static final Row AT_WAR = new Row("ZZZ", -90, 0, 10, true);

    private static List<String> tags(Mode mode) {
        List<Row> rows = new ArrayList<>(List.of(NEUTRAL, ENEMY, AT_WAR, ALLY));
        rows.sort(DiplomacySort.comparator(mode));
        return rows.stream().map(Row::tag).toList();
    }

    @Test
    void relationGoesFromAlliesToEnemies() {
        assertEquals(List.of("ZZZ", "BBB", "CCC", "AAA"), tags(Mode.RELATION));
    }

    @Test
    void conflictIsPinnedInEveryMode() {
        for (Mode mode : Mode.values()) {
            assertEquals("ZZZ", tags(mode).get(0), mode.name());
        }
    }

    @Test
    void otherModesOrderTheRest() {
        assertEquals(List.of("ZZZ", "AAA", "BBB", "CCC"), tags(Mode.TAG));
        assertEquals(List.of("ZZZ", "CCC", "AAA", "BBB"), tags(Mode.ONLINE));
        assertEquals(List.of("ZZZ", "AAA", "BBB", "CCC"), tags(Mode.INFLUENCE));
    }

    @Test
    void equalScoresFallBackToTag() {
        List<Row> rows = new ArrayList<>(List.of(new Row("B", 10, 0, 0, false), new Row("A", 10, 0, 0, false)));
        rows.sort(DiplomacySort.comparator(Mode.RELATION));
        assertEquals("A", rows.get(0).tag());
    }
}
