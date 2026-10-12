package me.lovelace.loveclans.model.spirit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpiritBuffLevelTest {
    @Test
    void blessingsAreCumulative() {
        assertEquals(3, SpiritBuffLevel.activeUpTo(3).size(), "level 3 has blessings 1, 2 and 3 active");
        assertEquals(SpiritBuffLevel.LEVEL_3, SpiritBuffLevel.activeUpTo(3).get(2));
        assertEquals(10, SpiritBuffLevel.activeUpTo(10).size());
        assertTrue(SpiritBuffLevel.activeUpTo(0).isEmpty());
    }

    @Test
    void nextBlessingIsTheFirstLockedOne() {
        assertEquals(SpiritBuffLevel.LEVEL_4, SpiritBuffLevel.nextAfter(3));
        assertNull(SpiritBuffLevel.nextAfter(10));
    }

    @Test
    void regenerationDescriptionUsesConfiguredPeriod() {
        assertTrue(SpiritBuffLevel.LEVEL_6.getDescription(15).contains("15"));
        assertFalse(SpiritBuffLevel.LEVEL_6.getDescription(15).contains("%tick%"));
        assertEquals(SpiritBuffLevel.LEVEL_1.getDescription(), SpiritBuffLevel.LEVEL_1.getDescription(15));
    }
}
