package me.lovelace.loveclans.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Bukkit's copyDefaults only adds missing keys, so texts that were reworded in the jar never reach a server's
 * existing lang.yml (old "Сейчас: Все" labels, emoji button names). Each lang-version bump lists the sections that
 * must be taken from the jar again; the caller keeps a backup of the old file.
 */
public final class LangMigrator {

    public static final String VERSION_KEY = "lang-version";

    /** Sections overwritten when migrating to version N (index = N - 2). */
    private static final List<List<String>> OVERWRITE_BY_VERSION = List.of(
            List.of("gui", "war", "territory.banner", "trade", "item.war-compass", "admin", "clan.help"),
            // v3: conflicts revamp - boss bars, raid/siege/casus/diplomacy texts were reworded or added
            List.of("gui", "war", "raid", "siege", "casus", "diplomacy", "item", "admin", "clan.help", "modifier")
    );

    private LangMigrator() {
    }

    public static int latestVersion() {
        return OVERWRITE_BY_VERSION.size() + 1;
    }

    /** True when the server file is older than the jar and was changed. */
    public static boolean migrate(FileConfiguration server, FileConfiguration bundled) {
        int current = server.getInt(VERSION_KEY, 1);
        int target = Math.min(bundled.getInt(VERSION_KEY, 1), latestVersion());
        if (current >= target) {
            return false;
        }
        for (int version = current + 1; version <= target; version++) {
            for (String root : OVERWRITE_BY_VERSION.get(version - 2)) {
                overwrite(server, bundled, root);
            }
        }
        server.set(VERSION_KEY, target);
        return true;
    }

    private static void overwrite(FileConfiguration server, FileConfiguration bundled, String root) {
        Object value = bundled.get(root);
        if (value instanceof ConfigurationSection section) {
            server.set(root, null);
            for (String key : section.getKeys(true)) {
                Object leaf = section.get(key);
                if (!(leaf instanceof ConfigurationSection)) {
                    server.set(root + "." + key, leaf);
                }
            }
        } else if (value != null) {
            server.set(root, value);
        }
    }
}
