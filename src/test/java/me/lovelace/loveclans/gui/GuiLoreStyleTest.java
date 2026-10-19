package me.lovelace.loveclans.gui;

import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the GUI lore style standard (RULE 10, docs/GUI_LORE_STYLE.md) over every item text of lang.yml: all of
 * {@code gui.*}, the physical items ({@code item.*}, {@code raid.compass}, the item keys of {@code clan.banner}),
 * the raid loot screen and {@code casus.*}. Chat lines (those with {@code <prefix>}) are not item text and are skipped.
 *
 * <p>Rules are written against the real file, not a fixture, so a reworded text that breaks the standard fails here.
 * Every intentional exception is an explicit list below with the reason next to it; there is no silent skipping.
 */
class GuiLoreStyleTest {
    /** Texts allowed to carry the "max level" check mark (guide section 5.4). */
    private static final List<String> MAX_LINES = List.of("gui.style.status.max", "gui.spirit.power.maximum");
    private static final String FORBIDDEN_IN_NAMES = "✦✔✘▸▶⛃·⚑⌛⚠";
    private static final Pattern MONEY_AFTER_COLOR =
            Pattern.compile("<(yellow|gold|gray|green|red|aqua)><(amount|cost|price|fee|have|balance|money)>");

    // ── Click lines: colour per click type, one action per line ──────────────────────────────────
    private static final Pattern CLICK_LEFT = Pattern.compile("^<green>ЛКМ <dark_gray>— <gray>.+");
    private static final Pattern CLICK_RIGHT = Pattern.compile("^<gold>ПКМ <dark_gray>— <gray>.+");
    private static final Pattern CLICK_SHIFT = Pattern.compile("^<yellow>Shift\\+(ЛКМ|ПКМ) <dark_gray>— <gray>.+");
    private static final Pattern BLOCKED_LINE = Pattern.compile("^<dark_gray>(Shift\\+)?(ЛКМ|ПКМ) — .+ \\(заблокировано\\)$");
    private static final Pattern CLICK_LABEL = Pattern.compile("(Shift\\+)?(ЛКМ|ПКМ)");

    /** Words of the old hint style ("Нажмите…", "Кликните…"); the click is always the {@code ЛКМ — …} line. */
    private static final Pattern CLICK_VERBS = Pattern.compile("(?iU)\\b(нажмите|кликните)\\b");

    // ── Structure ────────────────────────────────────────────────────────────────────────────────
    private static final String SEPARATOR_KEY = "gui.style.separator";
    /**
     * Leaves whose value starts an item's lore, so its first line is the dark-gray subtitle. Other list keys are
     * fragments that Java appends to something else (sections, "after the click" blocks) and start with whatever they like.
     */
    private static final Set<String> LORE_START_LEAVES = Set.of("lore", "description", "head", "profile", "info-lore", "hub-lore",
            "category-lore", "total-lore", "lore-active", "lore-open", "lore-closed", "active-lore", "ready-lore", "reward-lore",
            "entry-lore", "no-lore", "pending", "chosen");
    /** Lore lists named after their state instead of "lore" (trade window buttons): complete lores that start with a subtitle. */
    private static final Set<String> LORE_START_KEYS = Set.of(
            "gui.trade-session.side.ready", "gui.trade-session.side.not-ready",
            "gui.trade-session.confirm.ready", "gui.trade-session.confirm.not-ready",
            "gui.conflict.war.desc", "gui.conflict.siege.desc", "gui.conflict.raid.desc",
            "gui.diplomacy.gift.preset-lore", "gui.diplomacy.gift.custom-lore");
    private static final Pattern SECTION_HEADER = Pattern.compile("^<dark_gray>▪ .*");
    private static final Pattern STATUS_LINE = Pattern.compile("^<(red|yellow)>(✕|⌛) .*");
    private static final Pattern PRICE_LINE = Pattern.compile(".*Цена.*");

    // ── Progress ─────────────────────────────────────────────────────────────────────────────────
    /** MiniMessage styling tags; everything else in angle brackets is a lang placeholder. */
    private static final Set<String> STYLE_TAGS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "bold", "italic",
            "underlined", "strikethrough", "obfuscated", "st", "b", "i", "u", "reset");
    private static final Pattern ANY_TAG = Pattern.compile("</?([a-z_#0-9:]+)>");
    private static final Pattern RATIO = Pattern.compile("<[a-z_0-9]+>\\s*/\\s*<[a-z_0-9]+>");
    /**
     * Values that show an {@code x/y} of two placeholders without a bar on purpose: the pair is a capacity or a cap, not
     * progress toward a goal. Anything else with a ratio must carry the 16-cell {@code <bar>} (LoreBar).
     */
    private static final Map<String, String> RATIO_WITHOUT_BAR = Map.ofEntries(
            Map.entry("gui.members.info.lore", "members of the clan against the member limit (capacity)"),
            Map.entry("gui.main.info.lore", "members of the clan against the member limit (capacity)"),
            Map.entry("gui.info.lore", "members of the clan against the member limit (capacity)"),
            Map.entry("gui.clan-list.clan-item.stats", "members of the clan against the member limit (capacity)"),
            Map.entry("gui.spirit.mastery.lore", "today's mastery gain against its daily cap (cap, not goal)"),
            Map.entry("gui.conflict.compare.lore", "our value against theirs: a comparison of two clans, not progress"));

    // ── Names, titles and exceptions ─────────────────────────────────────────────────────────────
    /** Buttons with a name only, by design: back/close/pages carry no lore (the standard's navigation exception). */
    private static final Set<String> NAVIGATION_NAMES = Set.of("gui.back", "gui.close", "gui.next-page", "gui.previous-page",
            "gui.casus-belli.back-guildmaster", "gui.casus-belli.back-target", "gui.diplomacy.select-other.name");
    /** Names that do not end in {@code name}/{@code title} but are item names all the same. */
    private static final Set<String> EXPLICIT_NAMES = Set.of("gui.confirm.yes", "gui.confirm.no", "gui.back", "gui.close",
            "gui.next-page", "gui.previous-page", "gui.casus-belli.back-guildmaster", "gui.casus-belli.back-target",
            "gui.create.create.ready", "gui.create.create.not-ready", "gui.banner-create.ready");
    /** On-screen titles shown through the Title API with a bold style; they are not item names. */
    private static final Set<String> SCREEN_TITLES = Set.of("raid.loot.hacked-title", "raid.loot.hacked-subtitle",
            "raid.loot.defender-breached-title", "raid.loot.defender-breached-subtitle");
    /** Colourless values injected into already coloured lines ({@code <white><player>}): a tag inside would bleed into the line. */
    private static final Set<String> PLAIN_VALUES = Set.of("gui.members.unknown-player", "gui.style.action.open",
            "gui.cycle.next", "gui.cycle.previous", "gui.confirm.yes-action", "gui.confirm.no-action",
            "gui.trade.clan.sort.name", "gui.members-hub.sort.name");
    /** Plain titles of casus belli reasons: they are placeholders of the reason-item names, never names themselves. */
    private static final String PLAIN_REASON_TITLES = "gui.casus-belli.reason.";

    /** Item keys outside the scanned roots: the foundation-banner purchase screen lives in {@code clan.banner}. */
    private static final Set<String> EXTRA_KEYS = Set.of("clan.banner.confirm-item-name", "clan.banner.confirm-item-lore",
            "clan.banner.replacement-item-name", "clan.banner.replacement-lore");

    /** One text: the dotted key (list items carry {@code [i]}) and its value. */
    private record Text(String key, String value) {
        String base() {
            return key.contains("[") ? key.substring(0, key.indexOf('[')) : key;
        }

        String leaf() {
            String base = base();
            return base.substring(base.lastIndexOf('.') + 1);
        }

        boolean inList() {
            return key.contains("[");
        }
    }

    /** One lang value as the player sees it: a single string or all lines of a list. */
    private record Block(String key, List<String> lines) {
        String leaf() {
            return key.substring(key.lastIndexOf('.') + 1);
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

    private static boolean inScope(String path) {
        return path.startsWith("gui.") || path.startsWith("item.") || path.startsWith("raid.compass")
                || path.startsWith("raid.loot") || path.startsWith("casus.") || EXTRA_KEYS.contains(path);
    }

    private static void collect(String path, Object node, List<Text> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collect(path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey(), entry.getValue(), out);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) collect(path + "[" + i + "]", list.get(i), out);
        } else if (node instanceof String text && inScope(path.replaceAll("\\[\\d+]$", "")) && !text.contains("<prefix>")) {
            out.add(new Text(path, text));
        }
    }

    private static List<Text> texts() throws Exception {
        List<Text> texts = new ArrayList<>();
        collect("", loadLang(), texts);
        return texts;
    }

    private static List<Block> blocks() throws Exception {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Text text : texts()) grouped.computeIfAbsent(text.base(), k -> new ArrayList<>()).add(text.value());
        List<Block> blocks = new ArrayList<>();
        grouped.forEach((key, lines) -> blocks.add(new Block(key, lines)));
        return blocks;
    }

    private static boolean isName(Text text) {
        String leaf = text.leaf();
        return leaf.equals("name") || leaf.equals("title") || leaf.endsWith("-name") || leaf.endsWith("-title")
                || leaf.startsWith("name-") || leaf.startsWith("title-") || leaf.equals("button")
                || EXPLICIT_NAMES.contains(text.key());
    }

    private static boolean isTitle(Text text) {
        String leaf = text.leaf();
        return leaf.equals("title") || leaf.endsWith("-title") || leaf.startsWith("title-");
    }

    private static boolean isCoreName(Text text) {
        return isName(text) && !SCREEN_TITLES.contains(text.key());
    }

    /** Option labels and reason titles are values of other templates ({@link #PLAIN_VALUES}), not item names. */
    private static boolean isPlainLabel(Text text) {
        return PLAIN_VALUES.contains(text.key()) || text.key().startsWith(PLAIN_REASON_TITLES);
    }

    private static boolean isLoreStart(Block block) {
        return LORE_START_LEAVES.contains(block.leaf()) || LORE_START_KEYS.contains(block.key());
    }

    private static boolean isSeparator(String line, String separator) {
        return line.equals(separator);
    }

    private static boolean isClickLine(String line) {
        return CLICK_LEFT.matcher(line).matches() || CLICK_RIGHT.matcher(line).matches() || CLICK_SHIFT.matcher(line).matches();
    }

    private static boolean isChecklist(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("<green>✓") || trimmed.startsWith("<red>✕") || trimmed.startsWith("<yellow>⌛");
    }

    private static String separator() throws Exception {
        for (Text text : texts()) if (text.key().equals(SEPARATOR_KEY)) return text.value();
        throw new AssertionError(SEPARATOR_KEY + " is missing");
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

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Syntax and symbols
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void everyValueParsesAsMiniMessage() throws Exception {
        List<String> broken = new ArrayList<>();
        for (Text text : texts()) {
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
        for (Text text : texts()) {
            if (!isName(text)) continue;
            boolean bad = text.value().codePoints().anyMatch(cp -> FORBIDDEN_IN_NAMES.indexOf(cp) >= 0 || cp >= 0x1F000);
            if (bad) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "names with icons: " + offenders);
    }

    @Test
    void namesAreNotBold() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (!isCoreName(text)) continue;
            if (text.value().contains("<bold>") || text.value().contains("<b>")) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "item names and menu titles are not bold: " + offenders);
    }

    @Test
    void noOldCheckMarksInLore() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (MAX_LINES.stream().anyMatch(max -> text.key().startsWith(max))) continue;
            if (text.value().contains("✔") || text.value().contains("✘") || text.value().contains("✗")) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "use the check mark and the cross of the standard: " + offenders);
    }

    @Test
    void noOldHintStyles() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            String value = text.value();
            if (value.contains("ЛКМ -") || value.contains("ПКМ -") || value.contains("▸") || value.contains("›")) offenders.add(text.key());
            if (value.contains("▶") && !text.key().equals("gui.cycle.option-current")) offenders.add(text.key());
            if (CLICK_VERBS.matcher(value).find() && !isTitle(text)) offenders.add(text.key() + " (click verb)");
        }
        assertTrue(offenders.isEmpty(), "old hint styles: " + offenders);
    }

    @Test
    void moneyValuesAreWhite() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (MONEY_AFTER_COLOR.matcher(text.value()).find()) offenders.add(text.key() + " = " + text.value());
        }
        assertTrue(offenders.isEmpty(), "a coin value must follow <white>: " + offenders);
    }

    @Test
    void separatorMatchesTheSharedKey() throws Exception {
        String separator = separator();
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (text.value().contains("<st>") && !text.value().equals(separator)) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "separator lines must equal gui.style.separator: " + offenders);
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Clicks
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void everyClickLineUsesTheStrictFormat() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (isTitle(text)) continue;
            String value = text.value();
            if (!value.contains("ЛКМ") && !value.contains("ПКМ")) continue;
            if (BLOCKED_LINE.matcher(value).matches()) continue;
            boolean strict = CLICK_LEFT.matcher(value).matches() && value.startsWith("<green>ЛКМ")
                    || CLICK_RIGHT.matcher(value).matches() && value.startsWith("<gold>ПКМ")
                    || CLICK_SHIFT.matcher(value).matches();
            if (!strict) offenders.add(text.key() + " = " + value);
        }
        assertTrue(offenders.isEmpty(), "non-standard click lines (ЛКМ green, ПКМ gold, Shift+ yellow): " + offenders);
    }

    @Test
    void aClickLineHoldsOneAction() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (isTitle(text) || (!isClickLine(text.value()) && !BLOCKED_LINE.matcher(text.value()).matches())) continue;
            Matcher labels = CLICK_LABEL.matcher(text.value());
            int count = 0;
            while (labels.find()) count++;
            if (count != 1 || text.value().contains(" · ") || text.value().contains(" / ")) offenders.add(text.key() + " = " + text.value());
        }
        assertTrue(offenders.isEmpty(), "one action per click line: " + offenders);
    }

    @Test
    void theSeparatorStandsRightBeforeTheFirstClick() throws Exception {
        String separator = separator();
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            List<String> lines = block.lines();
            if (block.key().startsWith("gui.style.")) continue; // the click templates themselves, filled in by LoreBlocks
            for (int i = 0; i < lines.size(); i++) {
                if (!isClickLine(lines.get(i))) continue;
                boolean previousIsClick = i > 0 && isClickLine(lines.get(i - 1));
                if (!previousIsClick && (i == 0 || !isSeparator(lines.get(i - 1), separator))) {
                    offenders.add(block.key() + "[" + i + "] = " + lines.get(i));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "the first click line must directly follow the separator: " + offenders);
    }

    @Test
    void blockedBlocksKeepTheirOrder() throws Exception {
        String separator = separator();
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            List<String> lines = block.lines();
            if (block.key().startsWith("gui.style.")) continue; // gui.style.reason is the "Причина:" template itself
            int reason = -1;
            int blocked = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (reason < 0 && lines.get(i).startsWith("<red>Причина:")) reason = i;
                if (BLOCKED_LINE.matcher(lines.get(i)).matches()) blocked = i;
            }
            if (reason < 0 && blocked < 0) continue;
            // separator -> status (✕ / ⌛) -> Причина: -> (заблокировано)
            int anchor = reason >= 0 ? reason : blocked;
            if (anchor < 2 || !STATUS_LINE.matcher(lines.get(anchor - 1)).matches() || !isSeparator(lines.get(anchor - 2), separator)) {
                offenders.add(block.key() + ": the status line and the separator must precede the reason");
            }
            if (reason >= 0 && blocked >= 0 && blocked < reason) offenders.add(block.key() + ": the blocked click comes after the reason");
        }
        assertTrue(offenders.isEmpty(), "disabled blocks are separator, status, reason, blocked click: " + offenders);
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Structure of a lore
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void loreStartsWithTheDarkGraySubtitle() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            if (!isLoreStart(block)) continue;
            String first = block.lines().get(0);
            // a colourless sentence is a reason/action text that Java wraps, not a lore of its own
            if (first.isEmpty() || !first.startsWith("<")) continue;
            if (!first.startsWith("<dark_gray>") || SECTION_HEADER.matcher(first).matches() || isClickLine(first)) {
                offenders.add(block.key() + " starts with: " + first);
            }
        }
        assertTrue(offenders.isEmpty(), "a lore opens with a dark_gray subtitle (not a section, not a click): " + offenders);
    }

    @Test
    void sectionLinesAreIndentedByTwoSpaces() throws Exception {
        String separator = separator();
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            List<String> lines = block.lines();
            for (int i = 0; i < lines.size(); i++) {
                if (!SECTION_HEADER.matcher(lines.get(i)).matches()) continue;
                for (int j = i + 1; j < lines.size(); j++) {
                    String line = lines.get(j);
                    if (line.isEmpty() || isSeparator(line, separator) || SECTION_HEADER.matcher(line).matches()
                            || isClickLine(line) || line.startsWith("<dark_gray>") && !line.startsWith("<dark_gray>▪")
                            && !line.startsWith("  ")) {
                        break;
                    }
                    if (!line.startsWith("  ")) {
                        offenders.add(block.key() + "[" + j + "] = " + line);
                        break;
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "the lines of a `▪` section start with two spaces: " + offenders);
    }

    @Test
    void loreLinesSetTheirOwnColour() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            if (!isLoreStart(block) || !block.lines().get(0).startsWith("<")) continue;
            for (String line : block.lines()) {
                if (!line.isEmpty() && !line.stripLeading().startsWith("<")) offenders.add(block.key() + " = " + line);
            }
        }
        assertTrue(offenders.isEmpty(), "a lore line without a colour tag renders in the client default: " + offenders);
    }

    @Test
    void namesSetTheirOwnColour() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (!isCoreName(text) || isPlainLabel(text) || text.value().startsWith("<")) continue;
            offenders.add(text.key() + " = " + text.value());
        }
        assertTrue(offenders.isEmpty(), "names start with a colour tag: " + offenders);
    }

    @Test
    void plainValuesCarryNoTags() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            if (isPlainLabel(text) && (text.value().contains("<") || text.value().contains(">"))) offenders.add(text.key());
        }
        assertTrue(offenders.isEmpty(), "values injected into coloured lines stay plain text: " + offenders);
    }

    @Test
    void navigationButtonsStayLoreLess() throws Exception {
        Map<String, Object> lang = loadLang();
        List<String> offenders = new ArrayList<>();
        for (String key : NAVIGATION_NAMES) {
            Object value = lang;
            for (String part : key.split("\\.")) {
                value = value instanceof Map<?, ?> map ? map.get(part) : null;
            }
            if (!(value instanceof String)) offenders.add(key + " is not a plain name");
        }
        assertTrue(offenders.isEmpty(), "back/close/pages are names without lore: " + offenders);
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Progress, prices and the physical items
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void aRatioOfPlaceholdersShowsABar() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            if (block.leaf().equals("name") || block.leaf().endsWith("-name") || block.leaf().startsWith("name-")
                    || block.leaf().equals("title") || block.leaf().endsWith("-title")) continue;
            boolean hasBar = block.lines().stream().anyMatch(line -> line.contains("<bar"));
            for (String line : block.lines()) {
                if (isChecklist(line)) continue;
                if (!RATIO.matcher(withoutStyleTags(line)).find()) continue;
                if (!hasBar && !RATIO_WITHOUT_BAR.containsKey(block.key())) offenders.add(block.key() + " = " + line);
            }
        }
        assertTrue(offenders.isEmpty(), "x/y of placeholders is progress: show the 16-cell <bar> or list the key in RATIO_WITHOUT_BAR: "
                + offenders);
    }

    @Test
    void conflictStakesShowTheLootShareAsBars() throws Exception {
        Map<String, Block> byKey = new LinkedHashMap<>();
        for (Block block : blocks()) byKey.put(block.key(), block);
        for (String type : List.of("siege", "raid")) {
            Block stakes = byKey.get("gui.conflict." + type + ".stakes");
            assertTrue(stakes != null && stakes.lines().stream().anyMatch(line -> line.contains("<bar_")),
                    "gui.conflict." + type + ".stakes must show the loot share with a bar");
        }
    }

    @Test
    void fundsLinesExistForEveryPrice() throws Exception {
        // LoreBlocks.funds builds the ✓/✕ line that follows a price; lang only has to provide both variants.
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Text text : texts()) byKey.put(text.key(), text.value());
        assertTrue(byKey.containsKey("gui.style.funds.ok") && byKey.containsKey("gui.style.funds.missing"),
                "gui.style.funds.ok / gui.style.funds.missing must exist");
        assertTrue(byKey.get("gui.style.funds.ok").contains("✓") && byKey.get("gui.style.funds.missing").contains("✕"));
        assertTrue(byKey.get("gui.style.funds.missing").contains("<white><missing>"), "the missing amount follows <white>");
    }

    @Test
    void aPriceValueFollowsWhite() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Block block : blocks()) {
            for (String line : block.lines()) {
                if (PRICE_LINE.matcher(line).matches() && line.contains("<cost>") && !line.contains("<white><cost>")) {
                    offenders.add(block.key() + " = " + line);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "the price value must follow <white>: " + offenders);
    }

    @Test
    void physicalItemsAreNeitherBoldNorYellow() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Text text : texts()) {
            boolean physical = text.key().startsWith("item.") || text.key().startsWith("raid.compass");
            if (!physical) continue;
            String value = text.value();
            if (value.contains("<bold>") || value.contains("<b>")) offenders.add(text.key() + " is bold");
            if (value.startsWith("<yellow>") && !text.leaf().equals("name-frivolous")) offenders.add(text.key() + " is a yellow instruction");
        }
        assertTrue(offenders.isEmpty(), "seals, banners and compasses: no bold, no yellow body text: " + offenders);
    }

    private static String withoutStyleTags(String line) {
        Matcher matcher = ANY_TAG.matcher(line);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, STYLE_TAGS.contains(matcher.group(1)) ? "" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Test
    void theScannerSeesTheWholeStandardSurface() throws Exception {
        List<String> keys = texts().stream().map(Text::base).distinct().toList();
        for (String must : List.of("gui.style.click.left", "item.relocation-seal.lore", "item.casus-belli.lore", "item.guild-shield.lore",
                "raid.compass.lore", "gui.modifiers.entry.embargo.lore", "gui.contracts.item.name-progress")) {
            assertTrue(keys.contains(must), "the style test no longer reads " + must);
        }
        assertEquals(0, texts().stream().filter(t -> t.value().contains("<prefix>")).count(), "chat lines are not item text");
    }
}
