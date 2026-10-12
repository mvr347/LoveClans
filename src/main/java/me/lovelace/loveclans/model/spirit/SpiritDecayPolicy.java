package me.lovelace.loveclans.model.spirit;

/**
 * Pure rules for the spirit fading when a clan is idle: the less happens, the faster energy melts and, once energy is
 * gone, power erodes. Percentages and chances are per DAY and scaled by how long the check interval is, so the hourly
 * timer and a daily one give the same result. Power loss is deterministic accumulation instead of dice, so a clan can
 * see it coming and a bad roll never costs a level out of nowhere.
 */
public final class SpiritDecayPolicy {
    private SpiritDecayPolicy() {
    }

    public enum Tier { HIGH, MEDIUM, LOW, NONE }

    public record TierConfig(double minScore, double energyPercentPerDay, double powerLossPerDay) {
    }

    public record Config(TierConfig high, TierConfig medium, TierConfig low, TierConfig none, int minPowerLevel,
                         int masteryDriftPerDay) {
        public TierConfig of(Tier tier) {
            return switch (tier) {
                case HIGH -> high;
                case MEDIUM -> medium;
                case LOW -> low;
                case NONE -> none;
            };
        }
    }

    public record State(int level, long energy, double lossProgress, int mastery) {
    }

    public record Result(State state, boolean levelLost) {
    }

    /** Activity score halves every {@code halfLifeHours}; new points are added on top. */
    public static double decayedScore(double score, long scoreAtMillis, long nowMillis, double halfLifeHours) {
        if (score <= 0 || nowMillis <= scoreAtMillis || halfLifeHours <= 0) return Math.max(0, score);
        double hours = (nowMillis - scoreAtMillis) / 3_600_000.0;
        return score * Math.pow(0.5, hours / halfLifeHours);
    }

    /** {@code idleForNoneWindow}: nobody of the clan has been seen for the "none" window. */
    public static Tier tier(double score, boolean idleForNoneWindow, Config cfg) {
        if (idleForNoneWindow) return Tier.NONE;
        if (score >= cfg.high().minScore()) return Tier.HIGH;
        if (score >= cfg.medium().minScore()) return Tier.MEDIUM;
        if (score >= cfg.low().minScore()) return Tier.LOW;
        return Tier.NONE;
    }

    /**
     * One check. {@code intervalHours} is the time since the last check; {@code newDay} is true on the first check of a
     * UTC day (mastery drifts toward neutral once per day, only when the clan is idle).
     */
    public static Result apply(State state, Tier tier, double intervalHours, boolean newDay, Config cfg,
                               java.util.function.IntToLongFunction expForNextLevel) {
        TierConfig t = cfg.of(tier);
        double fraction = Math.max(0.0, intervalHours) / 24.0;

        long energy = state.energy();
        if (t.energyPercentPerDay() > 0 && energy > 0) {
            long loss = Math.round(energy * (t.energyPercentPerDay() / 100.0) * fraction);
            energy = Math.max(0L, energy - Math.max(1L, loss));
        }

        int level = state.level();
        double progress = state.lossProgress();
        boolean lost = false;
        if (energy == 0 && level > cfg.minPowerLevel() && t.powerLossPerDay() > 0) {
            progress += t.powerLossPerDay() * fraction;
            if (progress >= 1.0) {
                level--;
                progress -= 1.0;
                lost = true;
            }
        } else if (energy > 0 || t.powerLossPerDay() <= 0) {
            progress = 0.0; // erosion only accumulates while the well is dry
        }
        if (level <= cfg.minPowerLevel()) progress = 0.0;

        int mastery = state.mastery();
        if (newDay && (tier == Tier.LOW || tier == Tier.NONE)) {
            mastery = MasteryLedger.driftToNeutral(mastery, cfg.masteryDriftPerDay());
        }
        return new Result(new State(level, energy, progress, mastery), lost);
    }
}
