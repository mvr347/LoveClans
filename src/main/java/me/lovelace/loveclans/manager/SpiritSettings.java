package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.spirit.DefeatPowerLoss;
import me.lovelace.loveclans.model.spirit.MasteryLedger;
import me.lovelace.loveclans.model.spirit.SpiritBonus;
import me.lovelace.loveclans.model.spirit.SpiritBonusFormula;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Supplier;

/**
 * Reads {@code mechanics.spirit.*}. Every default equals the value shipped in config.yml, so a server that deleted a key
 * behaves like one that kept it. Read on demand: reload picks changes up without a restart.
 */
public final class SpiritSettings {
    private static final String ROOT = "mechanics.spirit.";
    private final Supplier<FileConfiguration> config;

    public SpiritSettings(Supplier<FileConfiguration> config) {
        this.config = config;
    }

    private double d(String path, double def) {
        return config.get().getDouble(ROOT + path, def);
    }

    private int i(String path, int def) {
        return config.get().getInt(ROOT + path, def);
    }

    public int tickSeconds() {
        return Math.max(1, i("tick-seconds", 15));
    }

    public long claimExp() {
        return (long) d("power.claim-exp", 120);
    }

    public long killExp() {
        return (long) d("power.kill-exp", 18);
    }

    public int killsPerHour() {
        return i("power.kills-per-hour", 8);
    }

    public long participationExp() {
        return (long) d("power.participation-exp", 40);
    }

    public int participationCap() {
        return i("power.participation-cap", 6);
    }

    public double activityHalfLifeHours() {
        return Math.max(1.0, d("activity.window-hours", 48) / 2.0);
    }

    public double activityPoints(String what, double def) {
        return d("activity.points." + what, def);
    }

    public MasteryLedger.Caps masteryCaps() {
        return new MasteryLedger.Caps(i("mastery.daily-cap.offense", 15), i("mastery.daily-cap.defense", 15));
    }

    public int masteryGain(String what, int def) {
        return i("mastery.gains." + what, def);
    }

    public double damageTakenPerMasteryPoint() {
        return Math.max(1.0, d("mastery.gains.damage-taken-per-point", 20));
    }

    public int unlockStep(SpiritBonus bonus) {
        return i("bonuses." + bonus.id() + ".unlock-step", bonus.defaultUnlockStep());
    }

    public double perStep(SpiritBonus bonus) {
        return d("bonuses." + bonus.id() + ".per-step", bonus.defaultPerStep());
    }

    /** Current value of {@code bonus} at {@code step}; 0 while locked. */
    public double value(SpiritBonus bonus, int step) {
        return SpiritBonusFormula.value(unlockStep(bonus), perStep(bonus), step);
    }

    public int overloadThreshold() {
        return i("overload.threshold", SpiritBonusFormula.DEFAULT_OVERLOAD_THRESHOLD);
    }

    public double overloadIncomingPercent() {
        return d("overload.extra-incoming-percent", 10);
    }

    public double overloadOutgoingPercent() {
        return d("overload.reduced-outgoing-percent", 10);
    }

    public double decayIntervalHours() {
        return Math.max(0.25, d("decay.check-interval-hours", 1));
    }

    public int noneAfterDays() {
        return Math.max(1, i("decay.none-after-days", 3));
    }

    public boolean notifyOnPowerLoss() {
        return config.get().getBoolean(ROOT + "decay.notify-on-power-loss", true);
    }

    public SpiritDecayPolicy.Config decay() {
        return new SpiritDecayPolicy.Config(
                tier("high", 50, 1, 0), tier("medium", 15, 6, 0), tier("low", 1, 12, 0.15), tier("none", 0, 20, 0.4),
                Math.max(0, i("decay.min-power-level", 0)), i("decay.mastery-drift-per-day", 5));
    }

    private SpiritDecayPolicy.TierConfig tier(String name, double minScore, double energyPercent, double powerLoss) {
        String base = "decay.tiers." + name + ".";
        return new SpiritDecayPolicy.TierConfig(d(base + "min-score", minScore), d(base + "energy-percent", energyPercent),
                d(base + "power-loss", powerLoss));
    }

    public DefeatPowerLoss.Config defeat() {
        return new DefeatPowerLoss.Config(i("defeat.base-loss", 2), i("defeat.close-loss", 1), i("defeat.soft-min-power", 8),
                d("defeat.close-margin", 0.2));
    }

    public ConfigurationSection section() {
        return config.get().getConfigurationSection(ROOT.substring(0, ROOT.length() - 1));
    }
}
