package me.lovelace.loveclans.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBindingsTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void normalizeTreatsNegativeBlankAndNonScalarAsUnbound() {
        assertNull(NpcBindings.normalize(null));
        assertNull(NpcBindings.normalize(-1));
        assertNull(NpcBindings.normalize("-1"));
        assertNull(NpcBindings.normalize(""));
        assertNull(NpcBindings.normalize("   "));
        assertNull(NpcBindings.normalize(true));
    }

    @Test
    void normalizeKeepsNumbersAsTextAndCanonicalisesNumericStrings() {
        assertEquals("12", NpcBindings.normalize(12));
        assertEquals("0", NpcBindings.normalize(0));
        assertEquals("12", NpcBindings.normalize(" 012 "));
        assertEquals("ext-1a2b3c4d", NpcBindings.normalize(" ext-1a2b3c4d "));
        String huge = "99999999999999999999";
        assertEquals(huge, NpcBindings.normalize(huge));
    }

    @Test
    void sameKeyComparesCanonicalForms() {
        assertTrue(NpcBindings.sameKey("5", "05"));
        assertTrue(NpcBindings.sameKey("ext-ab", "ext-ab"));
        assertFalse(NpcBindings.sameKey("5", "6"));
        assertFalse(NpcBindings.sameKey(null, null));
        assertFalse(NpcBindings.sameKey("-1", "-1"));
    }

    @Test
    void legacyNumericIdIsStillReadWithoutMigration() throws Exception {
        var config = yaml("clans:\n  guildmaster:\n    npc-id: 7\n  banner:\n    npc-id: -1\n");
        assertEquals("7", NpcBindings.read(config, NpcBindings.Slot.GUILDMASTER));
        assertNull(NpcBindings.read(config, NpcBindings.Slot.BANNER));
    }

    @Test
    void migrateMovesLegacyIdToStringKeyAndDropsOldKey() throws Exception {
        var config = yaml("clans:\n  guildmaster:\n    npc-id: 7\n    npc-bind-distance: 6.0\n  banner:\n    npc-id: -1\n    npc-key: ''\n");
        assertTrue(NpcBindings.migrate(config));
        assertEquals("7", config.get("clans.guildmaster.npc-key"));
        assertFalse(config.contains("clans.guildmaster.npc-id"));
        assertFalse(config.contains("clans.banner.npc-id"));
        assertNull(NpcBindings.read(config, NpcBindings.Slot.BANNER));
        // second run is a no-op, so the config is not rewritten on every start
        assertFalse(NpcBindings.migrate(config));
    }

    @Test
    void migrateKeepsAnExistingKeyOverTheLegacyValue() throws Exception {
        var config = yaml("clans:\n  banner:\n    npc-id: 3\n    npc-key: ext-0badf00d\n");
        assertTrue(NpcBindings.migrate(config));
        assertEquals("ext-0badf00d", config.get("clans.banner.npc-key"));
        assertFalse(config.contains("clans.banner.npc-id"));
    }

    @Test
    void writeAndClearRoundTrip() throws Exception {
        var config = yaml("clans:\n  guildmaster:\n    npc-id: 7\n");
        NpcBindings.write(config, NpcBindings.Slot.GUILDMASTER, "ext-11223344");
        assertEquals("ext-11223344", NpcBindings.read(config, NpcBindings.Slot.GUILDMASTER));
        assertFalse(config.contains("clans.guildmaster.npc-id"));
        NpcBindings.clear(config, NpcBindings.Slot.GUILDMASTER);
        assertNull(NpcBindings.read(config, NpcBindings.Slot.GUILDMASTER));
        assertTrue(NpcBindings.boundSlots(config).isEmpty());
    }

    @Test
    void slotByTypeIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(NpcBindings.Slot.BANNER, NpcBindings.Slot.byType("Banner"));
        assertNull(NpcBindings.Slot.byType("marshal"));
        assertNull(NpcBindings.Slot.byType(null));
    }

    @Test
    void bundledConfigDefaultsToCitizensAndUnboundKeys() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (var in = NpcBindingsTest.class.getResourceAsStream("/config.yml");
             var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        assertEquals("citizens", NpcService.providerId(bundled));
        assertTrue(NpcBindings.boundSlots(bundled).isEmpty());
        assertEquals("", bundled.getString("clans.banner.npc-key"));
        assertFalse(NpcBindings.migrate(bundled), "the bundled config must not carry legacy keys");
    }
}
