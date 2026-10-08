package me.lovelace.loveclans.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LangMigratorTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    @Test
    void oldFileGetsRewordedSectionsAndVersion() {
        var server = yaml("gui:\n  f:\n    all: 'Сейчас: Все'\n  gone: x\nkeep: mine\n");
        var bundled = yaml("lang-version: 2\ngui:\n  f:\n    all: 'Все'\nkeep: jar\n");
        assertTrue(LangMigrator.migrate(server, bundled));
        assertEquals("Все", server.getString("gui.f.all"));
        assertNull(server.get("gui.gone"));
        assertEquals("mine", server.getString("keep"));
        assertEquals(2, server.getInt("lang-version"));
    }

    @Test
    void secondRunIsNoop() {
        var server = yaml("lang-version: 2\ngui:\n  a: custom\n");
        var bundled = yaml("lang-version: 2\ngui:\n  a: jar\n");
        assertFalse(LangMigrator.migrate(server, bundled));
        assertEquals("custom", server.getString("gui.a"));
    }

    @Test
    void version2FileGetsVersion3Sections() {
        var server = yaml("lang-version: 2\nraid:\n  old: x\nkeep: mine\n");
        var bundled = yaml("lang-version: 3\nraid:\n  new: y\nkeep: jar\n");
        assertTrue(LangMigrator.migrate(server, bundled));
        assertEquals("y", server.getString("raid.new"));
        assertNull(server.get("raid.old"));
        assertEquals("mine", server.getString("keep"));
        assertEquals(3, server.getInt("lang-version"));
    }
}
