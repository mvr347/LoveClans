package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every literal {@code "gui...."} key used by a menu must exist in the shipped lang.yml - a missing key does not
 * fail, it silently shows the key name to players. lang.yml is also loaded with duplicate keys forbidden
 * (Bukkit's YAML silently lets the last duplicate win).
 */
class GuiLangKeysTest {
    private static final Pattern KEY = Pattern.compile("\"((?:gui|clan|chest|trade|recognition|dialog)\\.[a-z0-9_.-]*[a-z0-9_])\"");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadLang() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream in = GuiLangKeysTest.class.getResourceAsStream("/lang.yml")) {
            return new Yaml(options).load(in);
        }
    }

    private static boolean exists(Map<String, Object> root, String dotted) {
        Object current = root;
        for (String part : dotted.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) return false;
            current = map.get(part);
        }
        return true;
    }

    @Test
    void everyLiteralGuiKeyExistsInLangYml() throws Exception {
        Map<String, Object> lang = loadLang();
        List<String> missing = new ArrayList<>();
        for (String directory : List.of("gui", "dialog")) {
            try (Stream<Path> files = Files.walk(Path.of("src/main/java/me/lovelace/loveclans/" + directory))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher matcher = KEY.matcher(Files.readString(file));
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        if (!exists(lang, key)) missing.add(file.getFileName() + " -> " + key);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "keys missing from lang.yml: " + missing);
    }

    /** The physical items (factory banners, seals, casus belli, shield and the three compasses) read item.* / raid.compass.*. */
    private static final Pattern ITEM_KEY = Pattern.compile("\"((?:item\\.|raid\\.compass\\.)[a-z0-9_.-]*[a-z0-9_])\"");
    private static final List<String> ITEM_SOURCES = List.of("util/ClanItemFactory.java", "manager/WarManager.java",
            "manager/SiegeManager.java", "manager/RaidManager.java");

    @Test
    void everyLiteralItemKeyExistsInLangYml() throws Exception {
        Map<String, Object> lang = loadLang();
        List<String> missing = new ArrayList<>();
        int seen = 0;
        for (String source : ITEM_SOURCES) {
            Matcher matcher = ITEM_KEY.matcher(Files.readString(Path.of("src/main/java/me/lovelace/loveclans", source)));
            while (matcher.find()) {
                seen++;
                if (!exists(lang, matcher.group(1))) missing.add(source + " -> " + matcher.group(1));
            }
        }
        assertTrue(seen > 10, "the item key scan found only " + seen + " keys: the pattern or the file list drifted");
        assertTrue(missing.isEmpty(), "item keys missing from lang.yml: " + missing);
    }

    private static final String SYMBOLS = "💰📦🤝🏛✉✎→⇄🔒🚫📮⚔🎯🏹🕊🌾⛏✦✔✘·▸";

    /** Item names and menu titles carry no emoji/arrow symbols (owner request 2026-10-07). */
    @Test
    void guiNamesAndTitlesHaveNoSymbols() throws Exception {
        List<String> offenders = new ArrayList<>();
        collectSymbols("", loadLang(), offenders);
        assertTrue(offenders.isEmpty(), "names/titles with symbols: " + offenders);
    }

    private static void collectSymbols(String path, Object node, List<String> offenders) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collectSymbols(path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey(), entry.getValue(), offenders);
            }
            return;
        }
        if (!path.startsWith("gui.") || !(node instanceof String text)) return;
        String leaf = path.substring(path.lastIndexOf('.') + 1);
        if (!(leaf.contains("name") || leaf.contains("title")) || leaf.contains("prompt")) return;
        for (int i = 0; i < SYMBOLS.length(); ) {
            int cp = SYMBOLS.codePointAt(i);
            if (text.indexOf(new String(Character.toChars(cp))) >= 0) {
                offenders.add(path);
                return;
            }
            i += Character.charCount(cp);
        }
    }

    /**
     * The dialog builds its keys at runtime ("dialog.error." + problem, "clan." + problem), so the literal scan
     * cannot see them: every problem the validator can return needs its dialog text and its chat text.
     */
    @Test
    void everyValidatorProblemHasItsDialogAndChatText() throws Exception {
        Map<String, Object> lang = loadLang();
        List<String> missing = new ArrayList<>();
        for (String problem : List.of("invalid-name", "invalid-tag", "name-profane", "name-ad", "tag-profane", "tag-ad")) {
            if (!exists(lang, "dialog.error." + problem)) missing.add("dialog.error." + problem);
            if (!exists(lang, "clan." + problem)) missing.add("clan." + problem);
        }
        if (!exists(lang, "dialog.error.tag-exists")) missing.add("dialog.error.tag-exists");
        assertTrue(missing.isEmpty(), "keys missing from lang.yml: " + missing);
    }
}
