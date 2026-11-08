package me.lovelace.loveclans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TributeMathTest {
    private static YamlConfiguration cfg() {
        var c = new YamlConfiguration();
        c.set("daily-amount", 500);
        c.set("daily-per-defender-level", 40);
        c.set("daily-per-member", 15);
        c.set("daily-per-influence", 0.2);
        c.set("min-daily", 100);
        c.set("max-daily", 5000);
        return c;
    }

    @Test
    void tributeGrowsWithLevelMembersAndInfluence() {
        long small = TributeMath.daily(cfg(), 5, 3, 100);
        long big = TributeMath.daily(cfg(), 20, 15, 1500);
        assertTrue(big > small);
        assertEquals(500 + 20 * 40 + 15 * 15 + 300, big);
    }

    @Test
    void tributeIsClamped() {
        assertEquals(5000, TributeMath.daily(cfg(), 100, 100, 100000));
        var c = cfg();
        c.set("daily-amount", 0);
        assertEquals(100, TributeMath.daily(c, 0, 0, 0));
    }
}
