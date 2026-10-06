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
    private static final Pattern KEY = Pattern.compile("\"((?:gui|clan|chest|trade|recognition)\\.[a-z0-9_.-]*[a-z0-9_])\"");

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
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/me/lovelace/loveclans/gui"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!exists(lang, key)) missing.add(file.getFileName() + " -> " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "keys missing from lang.yml: " + missing);
    }
}
