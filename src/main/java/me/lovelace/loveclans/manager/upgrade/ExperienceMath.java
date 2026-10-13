package me.lovelace.loveclans.manager.upgrade;

/**
 * Pure experience arithmetic: the level curve and the experience tax. Kept free of Bukkit so the
 * numbers can be unit-tested; ClanManager only feeds it config values.
 *
 * <p>The curve is cumulative. {@code base} is the old threshold for each level; the cost of one
 * level-up (the step between two thresholds) is multiplied by that level's difficulty factor, so
 * every new step is "the old price x factor" - and levels a clan already has are never taken away.
 */
public final class ExperienceMath {

    private ExperienceMath() {}

    /**
     * @param level        target level
     * @param baseExp      experience needed for level 2 in the old curve
     * @param growth       per-level growth of the old curve
     * @param factors      difficulty factor for reaching level N, indexed by N (indices below 2 unused)
     * @param tailFactor   factor for levels beyond {@code factors}
     */
    public static long thresholdFor(int level, double baseExp, double growth, double[] factors, double tailFactor) {
        if (level <= 1) {
            return 0L;
        }
        double total = 0.0D;
        double previousOld = 0.0D;
        for (int l = 2; l <= level; l++) {
            double old = baseExp * Math.pow(growth, l - 2);
            double step = old - previousOld;
            double factor = l < factors.length && factors[l] > 0 ? factors[l] : tailFactor;
            total += step * factor;
            previousOld = old;
        }
        return Math.round(total);
    }

    /**
     * Experience tax: the more active members, the less experience the clan earns. Members up to
     * {@code freeMembers} cost nothing; each further one removes {@code perMember} (0.03 = 3%),
     * never going below {@code minFactor}.
     */
    public static double taxFactor(int activeMembers, int freeMembers, double perMember, double minFactor) {
        int extra = Math.max(0, activeMembers - freeMembers);
        double factor = 1.0D - extra * perMember;
        return Math.max(Math.min(1.0D, minFactor), Math.min(1.0D, factor));
    }

    /** Final amount: flat bonuses (upgrade + rewards, in percent) add up, then the tax applies. */
    public static long apply(long amount, double bonusPercent, double taxFactor) {
        if (amount <= 0) {
            return 0L;
        }
        return Math.round(amount * (1.0D + bonusPercent / 100.0D) * taxFactor);
    }
}
