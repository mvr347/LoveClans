package me.lovelace.loveclans.manager;

/**
 * What a clan pays for losing its banner and buying a replacement: spirit levels and a share of the experience
 * earned inside the current level. Pure arithmetic so it is unit-tested without a server. The clan level itself
 * never drops - only the progress towards the next one - because lowering it would take upgrades away.
 */
public final class BannerReplacementPenalty {
    private BannerReplacementPenalty() {}

    /** Spirit level after the penalty, never below 1. */
    public static int spiritLevel(int current, int levelsLost) {
        return Math.max(1, current - Math.max(0, levelsLost));
    }

    /** Spirit energy stays below the threshold of the (possibly lower) level, so no free level-up follows. */
    public static long spiritEnergy(long energy, int newLevel, long expForNextLevel) {
        return Math.max(0L, Math.min(energy, Math.max(0L, expForNextLevel - 1)));
    }

    /** Experience to remove: {@code percent} of what the clan earned since the start of its current level. */
    public static long experienceLoss(long experience, long levelStartExperience, int percent) {
        long progress = Math.max(0L, experience - levelStartExperience);
        int clamped = Math.max(0, Math.min(100, percent));
        return progress * clamped / 100L;
    }

    /** Price of the replacement banner: {@code percent} of the full price, rounded up to a whole copper coin. */
    public static long replacementCost(long fullCost, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return (fullCost * clamped + 99L) / 100L;
    }
}
