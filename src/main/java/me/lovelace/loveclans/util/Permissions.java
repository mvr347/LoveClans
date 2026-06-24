package me.lovelace.loveclans.util;

import org.bukkit.permissions.Permissible;

public final class Permissions {
    public static final String COMMAND = "loveclans.command";
    public static final String CREATE = "loveclans.create";
    public static final String DISBAND = "loveclans.disband";
    public static final String INVITE = "loveclans.invite";
    public static final String ACCEPT = "loveclans.accept";
    public static final String LEAVE = "loveclans.leave";
    public static final String KICK = "loveclans.kick";
    public static final String RANK = "loveclans.rank";
    public static final String CLAIM = "loveclans.claim";
    public static final String UNCLAIM = "loveclans.unclaim";
    public static final String MENU = "loveclans.menu";
    public static final String CHEST = "loveclans.chest";
    public static final String RITUAL = "loveclans.ritual";
    public static final String WAR = "loveclans.war";
    public static final String DIPLOMACY = "loveclans.diplomacy";
    public static final String SETTINGS = "loveclans.settings";
    public static final String VOTE = "loveclans.vote";
    public static final String APPLICATIONS = "loveclans.applications";
    public static final String ADMIN = "loveclans.admin";
    public static final String HOME = "loveclans.home";

    private static final String LEGACY_PREFIX = "clans.";
    private static final String CURRENT_PREFIX = "loveclans.";

    private Permissions() {
    }

    /**
     * Checks a loveclans.* permission node, falling back to the pre-rebrand
     * clans.* node so permission setups configured before the LoveClans
     * rename keep working.
     */
    public static boolean has(Permissible permissible, String permission) {
        return permissible.hasPermission(permission) || permissible.hasPermission(legacy(permission));
    }

    private static String legacy(String permission) {
        return permission.startsWith(CURRENT_PREFIX)
                ? LEGACY_PREFIX + permission.substring(CURRENT_PREFIX.length())
                : permission;
    }
}
