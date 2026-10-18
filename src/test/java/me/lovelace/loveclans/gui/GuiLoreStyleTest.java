package me.lovelace.loveclans.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the GUI lore style standard (RULE 10, docs/GUI_LORE_STYLE.md) over every {@code gui.*} text of lang.yml:
 * names without icons, one click-hint format, no old check marks, white coin values. Chat lines (those with
 * {@code <prefix>}) are not item text and are skipped.
 */
class GuiLoreStyleTest {
    /** Texts allowed to carry the "max level" check mark (guide section 5.4). */
    private static final List<String> MAX_LINES = List.of("gui.style.status.max", "gui.spirit.power.maximum");
    private static final String FORBIDDEN_IN_NAMES = "✦✔✘▸▶⛃·⚑⌛⚠";
    private static final Pattern CLICK_LINE = Pattern.compile("^<(green|gold|yellow)>(Shift\\+)?(ЛКМ|ПКМ) <dark_gray>— .*");
    private static final Pattern BLOCKED_LINE = Pattern.compile("^<dark_gray>.*\\(заблокировано\\)$");
    private static final Pattern MONEY_AFTER_COLOR =
            Pattern.compile("<(yellow|gold|gray|green|red|aqua)><(amount|cost|price|fee|have|balance|money)>");

    /** One text: the dotted key (list items carry {@code [i]}) and its value. */
    private record Text(String key, String value) {
        String leaf() {
            String base = key.contains("[") ? key.substring(0, key.indexOf('[')) : key;
            return base.substring(base.lastIndexOf('.') + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadLang() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream in = GuiLoreStyleTest.class.getResourceAsStream("/lang.yml")) {
            return new Yaml(options).load(in);
        }
    }

    private static void collect(String path, Object node, List<Text> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collect(path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey(), entry.getValue(), out);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) collect(path + "[" + i + "]", list.get(i), out);
        } else if (node instanceof String text && path.startsWith("gui.") && !text.contains("<prefix>")) {
            out.add(new Text(path, text));
        }
    }

    private static List<Text> guiTexts() throws Exception {
        List<Text> texts = new ArrayList<>();
        collect("", loadLang(), texts);
        return texts;
    }

    private static boolean isName(Text text) {
        String leaf = text.leaf();
        return leaf.equals("name") || leaf.equals("title") || leaf.endsWith("-name") || leaf.endsWith("-title")
                || leaf.startsWith("name-") || leaf.startsWith("title-") || leaf.equals("button")
                || text.key().equals("gui.create.create.ready") || text.key().equals("gui.create.create.not-ready")
                || text.key().equals("gui.banner-create.ready");
    }

    private static boolean isTitle(Text text) {
        String leaf = text.leaf();
        return leaf.equals("title") || leaf.endsWith("-title") || leaf.startsWith("title-");
    }

    /** Every tag that is not a MiniMessage built-in is a lang placeholder here: it resolves to nothing. */
    private static final TagResolver ANY_PLACEHOLDER = new TagResolver() {
        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) {
            return Tag.selfClosingInserting(net.kyori.adventure.text.Component.empty());
        }

        @Override
        public boolean has(@NotNull String name) {
            return !TagResolver.standard().has(name);
        }
    };

    @Test
    void everyValueParsesAsMiniMessage() throws Exception {
        List<String> broken = new ArrayList<>();
        for (Text text : guiTexts()) {
            try {
                MiniMessage.miniMessage().deserialize(text.value(), ANY_PLACEHOLDER);
            } catch (RuntimeException e) {
                broken.add(text.key() + " -> " + e.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "unparsable values: " + broken);
    }

    @Test
    void namesAndTitlesHaveNoIconsOrEmoji() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            if (!isName(text)) continue;
            boolean bad = text.value().codePoints().anyMatch(cp -> FORBIDDEN_IN_NAMES.indexOf(cp) >= 0 || cp >= 0x1F000);
            if (bad) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "names with icons: " + offenders);
    }

    @Test
    void noOldCheckMarksInLore() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            if (MAX_LINES.stream().anyMatch(max -> text.key().startsWith(max))) continue;
            if (text.value().contains("✔") || text.value().contains("✘") || text.value().contains("✗")) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "use the check mark and the cross of the standard: " + offenders);
    }

    @Test
    void everyClickLineUsesTheStandardFormat() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            if (isTitle(text)) continue;
            String value = text.value();
            if (!value.contains("ЛКМ") && !value.contains("ПКМ")) continue;
            if (!CLICK_LINE.matcher(value).matches() && !BLOCKED_LINE.matcher(value).matches()) offenders.add(text.key() + " = " + value);
        }
        assertTrue(offenders.isEmpty(), "non-standard click lines: " + offenders);
    }

    @Test
    void noOldHintStyles() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            String value = text.value();
            if (value.contains("ЛКМ -") || value.contains("ПКМ -") || value.contains("▸")) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "old hint styles: " + offenders);
    }

    @Test
    void moneyValuesAreWhite() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            if (MONEY_AFTER_COLOR.matcher(text.value()).find()) offenders.add(text.key() + " = " + text.value());
        }
        assertTrue(offenders.isEmpty(), "a coin value must follow <white>: " + offenders);
    }

    @Test
    void separatorMatchesTheSharedKey() throws Exception {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Text text : guiTexts()) byKey.put(text.key(), text.value());
        String separator = byKey.get("gui.style.separator");
        List<String> offenders = new ArrayList<>();
        for (Text text : guiTexts()) {
            if (text.value().contains("<st>") && !text.value().equals(separator)) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "separator lines must equal gui.style.separator: " + offenders);
    }
}
