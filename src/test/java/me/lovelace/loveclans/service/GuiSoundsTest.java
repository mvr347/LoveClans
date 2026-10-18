package me.lovelace.loveclans.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class GuiSoundsTest {
    private static final String BACK = "backhash";
    private static final String CLOSE = "closehash";
    private static final String INACTIVE = "inactivehash";

    private static GuiSounds.Kind classify(boolean passive, String texture, boolean backNamed, boolean closeNamed) {
        return GuiSounds.classify(passive, texture, BACK, CLOSE, INACTIVE, backNamed, closeNamed);
    }

    @Test
    void everyKindHasItsOwnConfigKey() {
        assertEquals("gui-click", GuiSounds.keyFor(GuiSounds.Kind.CLICK));
        assertEquals("gui-open", GuiSounds.keyFor(GuiSounds.Kind.OPEN));
        assertEquals("gui-back", GuiSounds.keyFor(GuiSounds.Kind.BACK));
        assertEquals("gui-close", GuiSounds.keyFor(GuiSounds.Kind.CLOSE));
        assertEquals("gui-toggle", GuiSounds.keyFor(GuiSounds.Kind.TOGGLE));
        assertEquals("gui-success", GuiSounds.keyFor(GuiSounds.Kind.SUCCESS));
        assertEquals("gui-denied", GuiSounds.keyFor(GuiSounds.Kind.DENIED));
        assertEquals("gui-error", GuiSounds.keyFor(GuiSounds.Kind.ERROR));
        assertEquals(8, GuiSounds.allKeys().size());
        assertEquals(8, List.copyOf(new java.util.HashSet<>(GuiSounds.allKeys())).size());
    }

    @Test
    void glassAndEmptySlotsStaySilent() {
        assertNull(classify(true, null, false, false));
        // Even a back-looking item is silent when the slot counts as passive
        assertNull(classify(true, BACK, true, false));
    }

    @Test
    void backCloseAndInactiveHeadsGetTheirOwnSounds() {
        assertEquals(GuiSounds.Kind.BACK, classify(false, BACK, true, false));
        assertEquals(GuiSounds.Kind.CLOSE, classify(false, CLOSE, false, true));
        assertEquals(GuiSounds.Kind.DENIED, classify(false, INACTIVE, false, false));
    }

    @Test
    void backTextureWithAnotherNameIsAnOrdinaryButton() {
        // The member screen reuses the back arrow for "demote": that must click, not turn a page back
        assertEquals(GuiSounds.Kind.CLICK, classify(false, BACK, false, false));
        assertEquals(GuiSounds.Kind.CLICK, classify(false, CLOSE, false, false));
    }

    @Test
    void anythingElseIsAPlainClick() {
        assertEquals(GuiSounds.Kind.CLICK, classify(false, "somethingelse", false, false));
        assertEquals(GuiSounds.Kind.CLICK, classify(false, null, false, false));
    }

    @Test
    void textureIdIgnoresSchemeAndTrailingSlash() {
        assertEquals("abc123", GuiSounds.textureId("http://textures.minecraft.net/texture/abc123"));
        assertEquals("abc123", GuiSounds.textureId("https://textures.minecraft.net/texture/abc123"));
        assertEquals("abc123", GuiSounds.textureId("https://textures.minecraft.net/texture/abc123/"));
        assertNull(GuiSounds.textureId(null));
        assertNull(GuiSounds.textureId(" "));
    }

    @Test
    void resultSoundReplacesTheClickOfTheSameTickOnly() {
        assertFalse(GuiSounds.suppressClick(null, 100));
        assertTrue(GuiSounds.suppressClick(100, 100));
        assertFalse(GuiSounds.suppressClick(99, 100));
        assertTrue(GuiSounds.isSpecific(GuiSounds.Kind.SUCCESS));
        assertTrue(GuiSounds.isSpecific(GuiSounds.Kind.TOGGLE));
        assertFalse(GuiSounds.isSpecific(GuiSounds.Kind.CLICK));
        assertFalse(GuiSounds.isSpecific(GuiSounds.Kind.BACK));
    }

    @Test
    void openSoundIsQuietRightAfterAnyGuiSound() {
        assertFalse(GuiSounds.suppressOpen(null, 500));
        assertTrue(GuiSounds.suppressOpen(500, 500));
        assertTrue(GuiSounds.suppressOpen(481, 500));
        assertFalse(GuiSounds.suppressOpen(480, 500));
    }

    /** Sound names need a bootstrapped server to resolve, so only the shape of every entry is checked here. */
    @Test
    void configHasEveryGuiSoundWithUsableValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = GuiSoundsTest.class.getResourceAsStream("/config.yml");
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        assertTrue(config.getBoolean("sounds.enabled"));
        Pattern name = Pattern.compile("[A-Z][A-Z0-9_]*");
        for (String key : GuiSounds.allKeys()) {
            String path = "sounds." + key;
            assertTrue(config.isConfigurationSection(path), "missing " + path);
            String sound = config.getString(path + ".sound", "");
            assertTrue(name.matcher(sound).matches(), path + ".sound is not an enum-style sound name: " + sound);
            double volume = config.getDouble(path + ".volume", -1);
            double pitch = config.getDouble(path + ".pitch", -1);
            assertTrue(volume > 0 && volume <= 1.0, path + ".volume out of range: " + volume);
            assertTrue(pitch >= 0.5 && pitch <= 2.0, path + ".pitch out of range: " + pitch);
        }
    }

    @Test
    void membersItemTextsCarryTheirPlaceholdersAndNeverAUuid() throws Exception {
        YamlConfiguration lang = new YamlConfiguration();
        try (InputStream in = GuiSoundsTest.class.getResourceAsStream("/lang.yml");
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            lang.load(reader);
        }
        String applicant = String.join("\n", lang.getStringList("gui.applications.applicant-item.lore"));
        for (String placeholder : List.of("<ago>", "<date>", "<status>")) {
            assertTrue(applicant.contains(placeholder), "applicant lore lacks " + placeholder);
        }
        String invite = String.join("\n", lang.getStringList("gui.applications.invite-item.lore"));
        for (String placeholder : List.of("<inviter>", "<time>", "<status>")) {
            assertTrue(invite.contains(placeholder), "invite lore lacks " + placeholder);
        }
        assertFalse(lang.getString("gui.members.unknown-player", "").isBlank());
        assertNull(lang.get("gui.applications.invite-item.status"), "the constant 'waiting' line must be gone");
    }
}
