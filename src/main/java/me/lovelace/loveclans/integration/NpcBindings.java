package me.lovelace.loveclans.integration;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Pure helpers for NPC bindings stored in config.yml. A binding is a String key: for the {@code citizens}
 * provider it is the numeric Citizens id written as text, for {@code loveessential} its own {@code ext-xxxxxxxx} id.
 * Older configs kept an Integer under {@code npc-id} (-1 = unbound); that value is still read and is migrated
 * to {@code npc-key} by {@link #migrate}.
 */
public final class NpcBindings {

    public static final String KEY = "npc-key";
    public static final String LEGACY_KEY = "npc-id";

    /** The NPC roles the plugin binds. */
    public enum Slot {
        GUILDMASTER("guildmaster", "clans.guildmaster"),
        BANNER("banner", "clans.banner");

        private final String type;
        private final String section;

        Slot(String type, String section) {
            this.type = type;
            this.section = section;
        }

        public String type() {
            return type;
        }

        public String section() {
            return section;
        }

        public static Slot byType(String type) {
            if (type == null) {
                return null;
            }
            String lower = type.toLowerCase(Locale.ROOT);
            for (Slot slot : values()) {
                if (slot.type.equals(lower)) {
                    return slot;
                }
            }
            return null;
        }
    }

    private NpcBindings() {
    }

    /**
     * Turns a raw config value into a canonical key, or null for "not bound". Numbers and numeric strings are
     * canonicalised ("05" -> "5"); negative numbers (the old -1 marker), blanks and non-scalar values mean unbound.
     */
    public static String normalize(Object raw) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value < 0 ? null : Long.toString(value);
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (trimmed.matches("-?\\d+")) {
                try {
                    long value = Long.parseLong(trimmed);
                    return value < 0 ? null : Long.toString(value);
                } catch (NumberFormatException tooLong) {
                    return trimmed;
                }
            }
            return trimmed;
        }
        return null;
    }

    /** Whether two keys name the same NPC (both sides are canonicalised first). */
    public static boolean sameKey(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);
        return left != null && left.equals(right);
    }

    /** The bound key of a section, reading the legacy numeric {@code npc-id} when {@code npc-key} is empty. */
    public static String read(ConfigurationSection config, String section) {
        String key = normalize(config.get(section + "." + KEY));
        return key != null ? key : normalize(config.get(section + "." + LEGACY_KEY));
    }

    public static String read(ConfigurationSection config, Slot slot) {
        return read(config, slot.section());
    }

    public static void write(ConfigurationSection config, Slot slot, String key) {
        config.set(slot.section() + "." + LEGACY_KEY, null);
        config.set(slot.section() + "." + KEY, key == null ? "" : key);
    }

    public static void clear(ConfigurationSection config, Slot slot) {
        write(config, slot, null);
    }

    /**
     * Moves legacy {@code npc-id} values into {@code npc-key} as text and removes the old key. An existing
     * non-empty {@code npc-key} wins. Returns true when the config was changed and should be saved.
     */
    public static boolean migrate(ConfigurationSection config) {
        boolean changed = false;
        for (Slot slot : Slot.values()) {
            String legacyPath = slot.section() + "." + LEGACY_KEY;
            if (!config.contains(legacyPath, true)) {
                continue;
            }
            String legacy = normalize(config.get(legacyPath));
            if (legacy != null && normalize(config.get(slot.section() + "." + KEY)) == null) {
                config.set(slot.section() + "." + KEY, legacy);
            }
            config.set(legacyPath, null);
            changed = true;
        }
        return changed;
    }

    public static List<Slot> boundSlots(ConfigurationSection config) {
        return java.util.Arrays.stream(Slot.values()).filter(slot -> read(config, slot) != null).toList();
    }
}
