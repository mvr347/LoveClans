package me.lovelace.loveclans.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clan colors are stored as a single MiniMessage open tag ({@code <gold>}, {@code <#ff8800>}) and lang strings close
 * them with {@code </color>}. A pre-parsed placeholder only substitutes the open tag, so the closing one was printed
 * as literal text and the color bled into the rest of the line. Expanding both tags up front fixes that.
 */
public final class ClanColorTags {

    public static final Set<String> KEYS = Set.of("color", "color1", "color2");
    private static final Pattern SIMPLE_TAG = Pattern.compile("^<([#a-zA-Z0-9_:]+)>$");

    private ClanColorTags() {
    }

    public static boolean isColorKey(String key, String value) {
        return KEYS.contains(key) && value != null && SIMPLE_TAG.matcher(value).matches();
    }

    /** Replaces {@code <key>} with the open tag and {@code </key>} with the matching closing tag. */
    public static String expand(String raw, String key, String openTag) {
        var m = SIMPLE_TAG.matcher(openTag);
        if (!m.matches()) {
            return raw;
        }
        String name = m.group(1);
        int colon = name.indexOf(':');
        String closeName = colon > 0 ? name.substring(0, colon) : name;
        return raw.replace("</" + key + ">", "</" + closeName + ">").replace("<" + key + ">", openTag);
    }
}
