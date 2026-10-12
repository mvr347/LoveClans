package me.lovelace.loveclans.model.spirit;

/**
 * How many power levels a lost war or siege costs. A strong spirit that lost narrowly is spared (a close loss is
 * not a collapse); everyone else pays the base price. Raids never touch power.
 */
public final class DefeatPowerLoss {
    private DefeatPowerLoss() {
    }

    public record Config(int baseLoss, int closeLoss, int softMinPower, double closeMargin) {
    }

    /** {@code margin} is 0 (neck and neck) .. 1 (crushed); a negative value means "unknown" and counts as crushed. */
    public static int compute(int powerLevel, double margin, Config cfg) {
        boolean close = margin >= 0 && margin <= cfg.closeMargin();
        int loss = (close && powerLevel >= cfg.softMinPower()) ? cfg.closeLoss() : cfg.baseLoss();
        return Math.max(0, Math.min(loss, powerLevel));
    }
}
