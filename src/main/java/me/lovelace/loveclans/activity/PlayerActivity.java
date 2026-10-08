package me.lovelace.loveclans.activity;

import java.util.UUID;

/** Aggregated activity of one player; the per-category fields add up to {@code total}. */
public record PlayerActivity(UUID playerId, long total, long war, long raid, long siege, long territory,
                             long economy, long diplomacy) {
    public static PlayerActivity of(UUID playerId, long[] byCategory) {
        long sum = 0;
        for (long v : byCategory) sum += v;
        return new PlayerActivity(playerId, sum, byCategory[0], byCategory[1], byCategory[2], byCategory[3],
                byCategory[4], byCategory[5]);
    }
}
