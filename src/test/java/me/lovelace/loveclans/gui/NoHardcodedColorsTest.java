package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Menus that were moved to lang must not grow section-sign colours again: all text and colour policy live in lang.yml. */
class NoHardcodedColorsTest {
    private static final List<String> CLEAN_FILES = List.of(
            "gui/ClanModifiersMenu.java", "gui/ClanCapitalManagementMenu.java", "gui/ClanSpiritMenu.java",
            "gui/ClanConflictMenu.java", "gui/conflict/ConflictLore.java", "gui/ClanGiftMenu.java");

    @Test
    void convertedMenusHaveNoSectionSigns() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String file : CLEAN_FILES) {
            String source = Files.readString(Path.of("src/main/java/me/lovelace/loveclans", file));
            if (source.contains("§")) offenders.add(file);
        }
        assertTrue(offenders.isEmpty(), "hardcoded § in: " + offenders);
    }
}
