package me.lovelace.loveclans.model.spirit;

import me.lovelace.loveclans.model.spirit.SpiritBonusFormula.Side;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The bonuses the spirit gives, four per side. Each unlocks at a step of {@link SpiritBonusFormula#step} and grows
 * linearly by its per-step value; the defaults below are mirrored in {@code mechanics.spirit.bonuses.<id>} in config.
 * Offensive bonuses apply in PvP against other clans everywhere; defensive ones that need a base work on own land.
 */
public enum SpiritBonus {
    PRESSURE(Side.OFFENSE, 1, 5, false),
    BLOODLUST(Side.OFFENSE, 2, 3, false),
    PURSUIT(Side.OFFENSE, 3, 1, false),
    INTIMIDATE(Side.OFFENSE, 4, 10, false),
    FORTITUDE(Side.DEFENSE, 1, 5, false),
    ROOTS(Side.DEFENSE, 2, 0.5, true),
    RETRIBUTION(Side.DEFENSE, 3, 5, true),
    UNYIELDING(Side.DEFENSE, 4, 25, true);

    private final Side side;
    private final int defaultUnlockStep;
    private final double defaultPerStep;
    private final boolean ownLandOnly;

    SpiritBonus(Side side, int defaultUnlockStep, double defaultPerStep, boolean ownLandOnly) {
        this.side = side;
        this.defaultUnlockStep = defaultUnlockStep;
        this.defaultPerStep = defaultPerStep;
        this.ownLandOnly = ownLandOnly;
    }

    public Side side() {
        return side;
    }

    public int defaultUnlockStep() {
        return defaultUnlockStep;
    }

    public double defaultPerStep() {
        return defaultPerStep;
    }

    public boolean ownLandOnly() {
        return ownLandOnly;
    }

    /** Lower-case id used in config and lang. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<SpiritBonus> of(Side side) {
        return Arrays.stream(values()).filter(b -> b.side == side).toList();
    }
}
