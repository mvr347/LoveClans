package me.lovelace.loveclans.activity;

import java.util.UUID;

/** Aggregated activity of a whole clan; the per-category fields add up to {@code total}. */
public record ClanActivity(UUID clanId, long total, long war, long raid, long siege, long territory,
                           long economy, long diplomacy) {
    public static ClanActivity of(UUID clanId, long[] byCategory) {
        long sum = 0;
        for (long v : byCategory) sum += v;
        return new ClanActivity(clanId, sum, byCategory[0], byCategory[1], byCategory[2], byCategory[3],
                byCategory[4], byCategory[5]);
    }
}
