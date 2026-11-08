package me.lovelace.loveclans.siege;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/**
 * Pixel widths of vanilla sidebar text (same glyph table as LoveEssential's SidebarWidth), so the siege board can
 * be made wider and its brand line centred. Text uses legacy section codes, which take no width themselves.
 */
public final class SidebarMetrics {
    private SidebarMetrics() {
    }

    public static int width(String legacy) {
        int result = 0;
        boolean bold = false;
        for (int i = 0; i < legacy.length(); ) {
            int c = legacy.codePointAt(i);
            i += Character.charCount(c);
            if (c == '§' && i < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(i++));
                if (code == 'l') bold = true;
                else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) bold = false;
                continue;
            }
            result += glyph(c) + (bold && c != ' ' && c != 160 ? 1 : 0);
        }
        return result;
    }

    public static int width(Component line) {
        return line instanceof TextComponent text ? width(text.content()) : 0;
    }

    private static int glyph(int c) {
        if (c == ' ' || c == 160) return 4;
        if ("!.,:;i|¦".indexOf(c) >= 0) return 2;
        if ("'`l".indexOf(c) >= 0) return 3;
        if ("I[]tії".indexOf(c) >= 0) return 4;
        if ("fkк(){}<>".indexOf(c) >= 0) return 5;
        if ("@~жшщыюЖМШЩЮ".indexOf(c) >= 0) return 7;
        if (c >= 0x2000 && c <= 0x2bff) return 10;
        if (c >= 0xe000 && c <= 0xf8ff) return 9;
        return 6;
    }

    /** Pads the line with non-breaking spaces on both sides so it is centred in a board {@code target} px wide. */
    public static Component center(String legacy, int target) {
        int spaces = Math.max(1, (target - width(legacy) + 7) / 8);
        String pad = "\u00a0".repeat(spaces);
        return Component.text(pad + legacy + pad);
    }

    /** The widest line of the board, in pixels. */
    public static int widest(List<Component> lines) {
        return lines.stream().mapToInt(SidebarMetrics::width).max().orElse(0);
    }
}
