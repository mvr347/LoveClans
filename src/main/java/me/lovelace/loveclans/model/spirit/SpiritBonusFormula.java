package me.lovelace.loveclans.model.spirit;

/**
 * Pure maths of what the spirit gives. Power (1-10) and Mastery (-100..100) both have to be high: the step is the
 * smaller of {@code ceil(power / 2)} and {@code |mastery| / 20}, so 0..5. Every bonus unlocks at some step and then
 * grows linearly by {@code perStep} with each further step (5 -> 10 -> 15 -> 20 -> 25 for the damage bonuses).
 */
public final class SpiritBonusFormula {
    public static final int MAX_STEP = 5;
    public static final int DEFAULT_OVERLOAD_THRESHOLD = 90;

    private SpiritBonusFormula() {
    }

    public enum Side { OFFENSE, DEFENSE, NONE }

    public static Side side(int mastery) {
        return mastery > 0 ? Side.OFFENSE : mastery < 0 ? Side.DEFENSE : Side.NONE;
    }

    public static int step(int powerLevel, int mastery) {
        int fromPower = (Math.max(0, powerLevel) + 1) / 2;
        int fromMastery = Math.abs(mastery) / 20;
        return Math.min(MAX_STEP, Math.min(fromPower, fromMastery));
    }

    /** Value of one bonus at {@code step}: 0 until it unlocks, then {@code perStep} per step since (inclusive). */
    public static double value(int unlockStep, double perStep, int step) {
        if (step < unlockStep) return 0.0;
        return perStep * (step - unlockStep + 1);
    }

    /** True when the axis is pegged so far that the opposite side pays for it. */
    public static boolean overloaded(int mastery, int threshold) {
        return Math.abs(mastery) >= threshold;
    }
}
