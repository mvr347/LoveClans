package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.manager.diplomacy.PublicModifiers.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicModifiersTest {
    @Test
    void quietRecognizedClanHasNothing() {
        assertTrue(PublicModifiers.of(new PublicModifiers.Snapshot(false, false, false, 0, 0, false, false, false, false, true)).isEmpty());
    }

    @Test
    void entriesComeMostUrgentFirstWithTimers() {
        var entries = PublicModifiers.of(new PublicModifiers.Snapshot(true, false, false, 3600, 120, true, false, false, true, false));
        assertEquals(List.of(Kind.WAR, Kind.GUILD_SHIELD, Kind.POST_RAID_SHIELD, Kind.BLOCKADE_ON_US, Kind.EMBARGO_BY_US, Kind.UNRECOGNIZED),
                entries.stream().map(PublicModifiers.Entry::kind).toList());
        assertEquals(3600, entries.get(1).seconds());
    }

    @Test
    void langKeysAreKebabCase() {
        assertEquals("gui.diplomacy.public.post-raid-shield", Kind.POST_RAID_SHIELD.langKey());
    }
}
