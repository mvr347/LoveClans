package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lore building blocks (public: the conflict lore lives in a sub-package) of the GUI style standard (separator, click lines, blocked-button block). */
public final class LoreBlocks {
    /** Mouse click a hint belongs to; the lang key under {@code gui.style.click} is derived from it. */
    public enum Click {
        LEFT("left", "ЛКМ"), RIGHT("right", "ПКМ"), SHIFT_LEFT("shift-left", "Shift+ЛКМ"), SHIFT_RIGHT("shift-right", "Shift+ПКМ");

        public final String key;
        public final String label;

        Click(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** One click line: which click does what ({@code action} is already localised text). */
    public record Hint(Click click, String action) {
    }

    private LoreBlocks() {
    }

    public static Component separator(MessageService messages, Player player) {
        return messages.component("gui.style.separator", player);
    }

    /** Separator followed by one line per hint. Empty list gives an empty result (no dangling separator). */
    public static List<Component> clicks(MessageService messages, Player player, List<Hint> hints) {
        if (hints.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(separator(messages, player));
        for (Hint hint : hints) {
            lines.add(messages.component("gui.style.click." + hint.click().key, Map.of("action", hint.action()), player));
        }
        return lines;
    }

    /**
     * Block for a disabled button: separator, status line, {@code Причина: …} lines (from a deny key) and the
     * click that would have worked, shown as blocked.
     */
    public static List<Component> blocked(MessageService messages, Player player, String statusKey, String denyKey,
                                   Map<String, String> placeholders, Hint blockedHint) {
        return blockedLines(messages, player, statusKey, messages.denyLore(denyKey, placeholders, player), blockedHint);
    }

    /** Click hint whose action text comes from a lang key; {@code <placeholders>} inside it are filled in here. */
    public static Hint hint(MessageService messages, Click click, String actionKey, Map<String, String> placeholders) {
        String text = messages.raw(actionKey);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return new Hint(click, text);
    }

    public static Hint hint(MessageService messages, Click click, String actionKey) {
        return hint(messages, click, actionKey, Map.of());
    }

    /**
     * Name of a disabled button: {@code Имя | Недоступно}. Built from the plain text of the normal name (the part
     * before a {@code " | "} state suffix), so no second lang key per button is needed.
     */
    public static Component disabledName(MessageService messages, Player player, String nameKey, Map<String, String> placeholders) {
        String plain = PlainTextComponentSerializer.plainText().serialize(messages.component(nameKey, placeholders, player));
        int bar = plain.indexOf(" | ");
        if (bar > 0) plain = plain.substring(0, bar);
        return messages.component("gui.style.name-unavailable", Map.of("name", plain), player);
    }

    public static Component disabledName(MessageService messages, Player player, String nameKey) {
        return disabledName(messages, player, nameKey, Map.of());
    }

    /** Disabled-button block with ready-made reason lines: separator, status, reasons, blocked click (may be null). */
    public static List<Component> blockedLines(MessageService messages, Player player, String statusKey,
                                        List<Component> reasonLines, Hint blockedHint) {
        List<Component> lines = new ArrayList<>();
        lines.add(separator(messages, player));
        lines.add(messages.component(statusKey, player));
        lines.addAll(reasonLines);
        if (blockedHint != null) {
            lines.add(messages.component("gui.style.blocked-click",
                    Map.of("click", blockedHint.click().label, "action", blockedHint.action()), player));
        }
        return lines;
    }

    /** Same block for a reason kept as one lang sentence (rendered as {@code Причина: …}). */
    public static List<Component> blockedReason(MessageService messages, Player player, String reasonKey,
                                         Map<String, String> placeholders, Hint blockedHint) {
        return blockedLines(messages, player, "gui.style.status.unavailable",
                List.of(messages.reason(reasonKey, placeholders, player)), blockedHint);
    }
}
