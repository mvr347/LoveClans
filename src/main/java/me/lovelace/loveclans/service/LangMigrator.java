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
            List.of("gui", "war", "raid", "siege", "casus", "diplomacy", "item", "admin", "clan.help", "modifier"),
            // v4: unified GUI patch - white-first color policy, diplomacy v2, gifts, compact spirit, conflict submenus
            List.of("gui", "deny", "clan", "diplomacy", "gift", "war", "siege", "raid"),
            // v5: spirit/perk descriptions rewritten to match the real effects, diplomacy deny texts
            List.of("gui", "deny", "diplomacy", "gift"),
            // v6: admin raid forcestart texts, dead deny.*.not-in-territory keys removed
            List.of("deny", "admin", "clan.help"),
            // v7: military actions screen rework (gui.conflict.*), deny texts take <max> placeholders
            List.of("gui", "deny"),
            // v8: spirit rework - Power + Mastery, abilities and the level ladder removed
            List.of("gui", "spirit"),
            // v9: capital menu (shield and relocation buttons), relocation seal item, admin seal command
            List.of("gui", "deny", "item", "admin", "clan.help", "diplomacy"),
            // v10: item chest refuses coins, shield expiry message
            List.of("deny", "chest", "diplomacy"),
            // v11: GUI lore style standard - click hints, status/reason lines, progress bars, white coin values
            List.of("gui", "clan.banner", "raid.loot"),
            // v12: members screen - invitation/application items show names, relative times and the cancel hint
            List.of("gui.applications", "gui.members"),
            // v13: member heads and profile menu in the lore standard, diplomacy descriptions rewritten
            List.of("gui.members", "gui.member-detail", "gui.diplomacy")
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
