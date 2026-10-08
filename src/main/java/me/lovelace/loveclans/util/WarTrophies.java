package me.lovelace.loveclans.util;

/** Spoils of a won war: a share of the loser's treasury. */
public final class WarTrophies {

    private WarTrophies() {
    }

    /**
     * @param balance loser's treasury
     * @param percent share of the treasury taken (0..100)
     * @param cap     upper limit of the prize, 0 = unlimited
     */
    public static long amount(long balance, int percent, long cap) {
        if (balance <= 0 || percent <= 0) {
            return 0L;
        }
        long prize = balance * Math.min(percent, 100) / 100L;
        return cap > 0 ? Math.min(prize, cap) : prize;
    }
}
