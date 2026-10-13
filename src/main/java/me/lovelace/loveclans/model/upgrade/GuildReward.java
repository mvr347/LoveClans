package me.lovelace.loveclans.model.upgrade;

/**
 * One-time clan achievements. Each grants a small permanent bonus; {@code bonus} names what it
 * improves and {@code percent} how much (rewards of the same kind simply add up).
 */
public enum GuildReward {
    PIONEER(Bonus.EXPERIENCE, 2.0D),
    FIRST_BLOOD(Bonus.DAMAGE_OWN_LAND, 3.0D),
    MERCHANT(Bonus.TAX_DISCOUNT, 2.0D),
    DEFENDER(Bonus.RESISTANCE_OWN_LAND, 3.0D),
    DIPLOMAT(Bonus.INFLUENCE, 2.0D),
    RICH(Bonus.INCOME, 2.0D),
    LEGION(Bonus.EXPERIENCE, 1.0D),
    VETERAN(Bonus.RESISTANCE, 2.0D),
    CONQUEROR(Bonus.DAMAGE_OFFENSE, 3.0D);

    public enum Bonus {
        /** Share added to all clan experience gained. */
        EXPERIENCE,
        /** Damage dealt to enemy clans while standing on the clan's own land. */
        DAMAGE_OWN_LAND,
        /** Damage dealt to enemy clans while attacking away from the clan's own land. */
        DAMAGE_OFFENSE,
        /** Damage resistance against enemy clans on the clan's own land. */
        RESISTANCE_OWN_LAND,
        /** Damage resistance against enemy clans anywhere. */
        RESISTANCE,
        /** Discount on the weekly chest tax. */
        TAX_DISCOUNT,
        /** Added to the final influence value. */
        INFLUENCE,
        /** Added to treasury income from state purchases and conflict rewards. */
        INCOME
    }

    private final Bonus bonus;
    private final double percent;

    GuildReward(Bonus bonus, double percent) {
        this.bonus = bonus;
        this.percent = percent;
    }

    public Bonus bonus() {
        return bonus;
    }

    public double percent() {
        return percent;
    }

    public String langKey() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
