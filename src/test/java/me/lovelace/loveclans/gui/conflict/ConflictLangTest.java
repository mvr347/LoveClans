package me.lovelace.loveclans.gui.conflict;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every checklist row the readiness builders emit needs both an ok and a fail line in lang, or the lore shows raw keys. */
class ConflictLangTest {
    @SuppressWarnings("unchecked")
    @Test
    void everyRequirementHasOkAndFailText() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<String, Object> root;
        try (InputStream in = ConflictLangTest.class.getResourceAsStream("/lang.yml")) {
            root = new Yaml(options).load(in);
        }
        Map<String, Object> req = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("gui")).get("conflict")).get("req");

        String source = Files.readString(Path.of("src/main/java/me/lovelace/loveclans/gui/ClanDiplomacyMenu.java"));
        Matcher m = Pattern.compile("\\.require\\(\"([a-z-]+)\"").matcher(source);
        TreeSet<String> ids = new TreeSet<>();
        while (m.find()) ids.add(m.group(1));
        assertFalse(ids.isEmpty());
        for (String id : ids) {
            Map<String, Object> entry = (Map<String, Object>) req.get(id);
            assertTrue(entry != null && entry.containsKey("ok") && entry.containsKey("fail"), "lang gui.conflict.req." + id + " needs ok and fail");
        }
    }
}
