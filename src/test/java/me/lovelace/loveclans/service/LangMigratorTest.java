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

    @Test
    void version11FileGetsMembersScreenSectionsOnly() {
        var server = yaml("lang-version: 11\ngui:\n  applications:\n    old: x\n  members:\n    old: y\n  other:\n    kept: mine\nraid:\n  a: mine\n");
        var bundled = yaml("lang-version: 12\ngui:\n  applications:\n    new: a\n  members:\n    unknown-player: b\n  other:\n    kept: jar\nraid:\n  a: jar\n");
        assertTrue(LangMigrator.migrate(server, bundled));
        assertEquals("a", server.getString("gui.applications.new"));
        assertNull(server.get("gui.applications.old"));
        assertEquals("b", server.getString("gui.members.unknown-player"));
        assertNull(server.get("gui.members.old"));
        // Roots outside gui.applications / gui.members keep the admin's text
        assertEquals("mine", server.getString("gui.other.kept"));
        assertEquals("mine", server.getString("raid.a"));
        assertEquals(12, server.getInt("lang-version"));
    }

    @Test
    void version13FileGetsTheStandardRootsOnly() {
        var server = yaml("lang-version: 13\ngui:\n  old: x\nitem:\n  seal:\n    old: y\nraid:\n  compass:\n    lore: old\n  loot:\n    prompt: mine\n"
                + "clan:\n  banner:\n    old: z\n  help: mine\nwar:\n  keep: mine\n");
        var bundled = yaml("lang-version: 14\ngui:\n  fresh: a\nitem:\n  seal:\n    fresh: b\nraid:\n  compass:\n    lore: fresh\n  loot:\n    prompt: jar\n"
                + "clan:\n  banner:\n    fresh: c\n  help: jar\nwar:\n  keep: jar\n");
        assertTrue(LangMigrator.migrate(server, bundled));
        assertEquals("a", server.getString("gui.fresh"));
        assertNull(server.get("gui.old"));
        assertEquals("b", server.getString("item.seal.fresh"));
        assertNull(server.get("item.seal.old"));
        assertEquals("fresh", server.getString("raid.compass.lore"));
        assertEquals("c", server.getString("clan.banner.fresh"));
        assertNull(server.get("clan.banner.old"));
        // Texts outside the migrated roots keep the admin's version
        assertEquals("mine", server.getString("raid.loot.prompt"));
        assertEquals("mine", server.getString("clan.help"));
        assertEquals("mine", server.getString("war.keep"));
        assertEquals(14, server.getInt("lang-version"));
    }

    @Test
    void latestVersionMatchesBundledLang() throws Exception {
        var bundled = new YamlConfiguration();
        try (var in = LangMigratorTest.class.getResourceAsStream("/lang.yml");
             var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        assertEquals(LangMigrator.latestVersion(), bundled.getInt(LangMigrator.VERSION_KEY));
        assertEquals(14, LangMigrator.latestVersion());
    }
}
