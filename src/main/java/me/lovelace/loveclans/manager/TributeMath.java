package me.lovelace.loveclans.manager;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Daily tribute (reparations) a defeated clan pays: a flat base grows with the payer's level, active members and
 * influence, so a big strong clan pays more than a small one, then it is clamped to [min-daily, max-daily].
 * Pure arithmetic, no Bukkit state, so the weights can be tested.
 */
public final class TributeMath {
    private TributeMath() {
    }

    public static long daily(ConfigurationSection cfg, int payerLevel, int activeMembers, long influence) {
        double base = cfg.getDouble("daily-amount", 300);
        base += payerLevel * cfg.getDouble("daily-per-defender-level", 40);
        base += Math.max(0, activeMembers) * cfg.getDouble("daily-per-member", 15);
        base += Math.max(0L, influence) * cfg.getDouble("daily-per-influence", 0.2);
        long min = cfg.getLong("min-daily", 100);
        long max = cfg.getLong("max-daily", 5000);
        long value = Math.round(base);
        if (max > 0) value = Math.min(max, value);
        return Math.max(min, value);
    }
}
