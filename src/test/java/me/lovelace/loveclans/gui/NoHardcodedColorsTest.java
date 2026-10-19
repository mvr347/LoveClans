package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menus and the item factory must not grow legacy colours again: all text and colour policy live in lang.yml. The check
 * covers every class of {@code gui/**} plus {@code ClanItemFactory}, looks at code and string literals only (a section
 * sign in a comment, such as the "§4.2" references to the design document, is fine) and has no per-file opt-in: a new
 * menu is checked from its first commit.
 */
class NoHardcodedColorsTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/me/lovelace/loveclans");
    /** Files outside gui/ that build item text; they follow the same policy. */
    private static final List<String> EXTRA_FILES = List.of("util/ClanItemFactory.java");
    /** Intentional exceptions: file (relative to the source root) -> reason. Empty on purpose. */
    private static final java.util.Map<String, String> ALLOWED = java.util.Map.of();

    private static final Pattern LEGACY_AMPERSAND = Pattern.compile("\"[^\"\\n]*&[0-9a-fk-or][^\"\\n]*\"");

    private static List<Path> files() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT.resolve("gui"))) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
        }
        for (String extra : EXTRA_FILES) files.add(SOURCE_ROOT.resolve(extra));
        return files;
    }

    /** Source with comments removed; string and char literals stay (that is where a §-code would hide). */
    static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append(source.charAt(i++));
                    }
                    out.append(source.charAt(i++));
                }
                if (i < n) out.append(source.charAt(i++));
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    void menuAndItemClassesHaveNoSectionSigns() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : files()) {
            String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
            if (ALLOWED.containsKey(relative)) continue;
            if (codeOnly(Files.readString(file)).contains("§")) offenders.add(relative);
        }
        assertTrue(offenders.isEmpty(), "hardcoded § in: " + offenders);
    }

    @Test
    void menuAndItemClassesHaveNoLegacyColorApis() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : files()) {
            String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
            if (ALLOWED.containsKey(relative)) continue;
            String code = codeOnly(Files.readString(file));
            if (code.contains("ChatColor") || code.contains("LegacyComponentSerializer") || LEGACY_AMPERSAND.matcher(code).find()) {
                offenders.add(relative);
            }
        }
        assertTrue(offenders.isEmpty(), "legacy colour codes or serializers in: " + offenders);
    }

    @Test
    void theScanReallyCoversTheMenus() throws Exception {
        List<String> names = files().stream().map(path -> path.getFileName().toString()).toList();
        for (String must : List.of("ClanModifiersMenu.java", "ClanMembersMenu.java", "ConflictLore.java", "CasusBelliMenu.java",
                "ClanItemFactory.java", "LoreBlocks.java")) {
            assertTrue(names.contains(must), "the colour scan does not reach " + must);
        }
    }

    @Test
    void theCommentStripperKeepsStringsAndDropsComments() {
        assertTrue(codeOnly("String s = \"§cred\"; // §4.2").contains("§c"));
        assertFalse(codeOnly("/** (§4.2) */ int x; // §1").contains("§"));
        assertTrue(codeOnly("String s = \"// §a\";").contains("§a"));
        assertFalse(codeOnly("/* a */ int x = 1; /* §b */").contains("§"));
    }
}
