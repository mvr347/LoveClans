package me.lovelace.loveclans.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/**
 * Price and lifetime of a casus belli, read from {@code casus-belli} in config.yml. War prices live under
 * {@code just-reasons} / {@code frivolous-reasons}; a siege uses an explicit {@code siege.*} override when present and
 * otherwise the war price times {@code siege-cost-multiplier}.
 */
public final class CasusPrices {
    private static final Map<String, Long> JUST_DEFAULTS = Map.of(
            "revenge_raid", 50L, "revenge_war", 50L, "revenge_siege", 50L, "unpaid_tribute", 25L, "broken_peace", 50L);
    private static final Map<String, Long> FRIVOLOUS_DEFAULTS = Map.of(
            "insult", 2000L, "dislike", 2000L, "looked_wrong", 2500L, "bad_fashion", 2500L, "land_envy", 3000L, "drunk_dare", 1500L);
    private static final Map<String, Integer> TTL_DEFAULTS = Map.of("unpaid_tribute", 21);

    private CasusPrices() {
    }

    public static long cost(ConfigurationSection casus, boolean just, String reasonId, boolean war) {
        String group = just ? "just-reasons" : "frivolous-reasons";
        if (!war && casus != null && casus.contains("siege." + group + "." + reasonId + ".cost")) {
            return Math.max(0L, casus.getLong("siege." + group + "." + reasonId + ".cost"));
        }
        long base = defaultCost(just, reasonId);
        if (casus != null) {
            base = casus.getLong(group + "." + reasonId + ".cost", base);
        }
        if (war) {
            return Math.max(0L, base);
        }
        double multiplier = casus == null ? 2.5 : casus.getDouble("siege-cost-multiplier", 2.5);
        return Math.max(0L, Math.round(base * multiplier));
    }

    public static int ttlDays(ConfigurationSection casus, String reasonId) {
        int fallback = TTL_DEFAULTS.getOrDefault(reasonId, 14);
        return casus == null ? fallback : Math.max(1, casus.getInt("just-reasons." + reasonId + ".ttl-days", fallback));
    }

    private static long defaultCost(boolean just, String reasonId) {
        return (just ? JUST_DEFAULTS : FRIVOLOUS_DEFAULTS).getOrDefault(reasonId, just ? 50L : 2000L);
    }
}
