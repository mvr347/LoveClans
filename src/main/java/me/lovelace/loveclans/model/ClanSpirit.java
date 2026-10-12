package me.lovelace.loveclans.model;

import me.lovelace.loveclans.model.spirit.MasteryLedger;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy;

/**
 * The clan spirit. Two axes: <b>Power</b> ({@link #level()} 0-10 with {@link #energy()} as progress to the next level)
 * and <b>Mastery</b> (-100 defence .. +100 offence), which only moves through actions. {@code activityScore} is a
 * decaying measure of how busy the clan is and drives how fast an idle spirit fades; {@code lossProgress} is the
 * accumulated erosion that costs one power level when it reaches 1.
 */
public record ClanSpirit(int level, long energy, long awakenedUntil, long lastDecayCheck,
                         int mastery, long masteryDay, int masteryOffenseToday, int masteryDefenseToday,
                         double activityScore, long activityAt, double lossProgress, long lastLossNotice) {

    public static ClanSpirit fresh() {
        return new ClanSpirit(1, 0L, 0L, System.currentTimeMillis(), 0, 0L, 0, 0, 0.0, System.currentTimeMillis(), 0.0, 0L);
    }

    public boolean awakened(long now) {
        return awakenedUntil > now;
    }

    public ClanSpirit withLevel(int level) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, activityScore, activityAt, lossProgress, lastLossNotice);
    }

    public ClanSpirit addEnergy(long amount) {
        return withEnergy(Math.max(0L, energy + amount));
    }

    public ClanSpirit withEnergy(long energy) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, activityScore, activityAt, lossProgress, lastLossNotice);
    }

    public ClanSpirit awakenUntil(long timestamp) {
        return new ClanSpirit(level, energy, timestamp, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, activityScore, activityAt, lossProgress, lastLossNotice);
    }

    public ClanSpirit withLastDecayCheck(long lastDecayCheck) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, activityScore, activityAt, lossProgress, lastLossNotice);
    }

    public MasteryLedger.State masteryState() {
        return new MasteryLedger.State(mastery, masteryDay, masteryOffenseToday, masteryDefenseToday);
    }

    public ClanSpirit withMastery(MasteryLedger.State state) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, state.mastery(), state.day(),
                state.offenseToday(), state.defenseToday(), activityScore, activityAt, lossProgress, lastLossNotice);
    }

    public ClanSpirit withActivity(double score, long at) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, score, at, lossProgress, lastLossNotice);
    }

    /** Applies the outcome of a decay check (level, energy, erosion and mastery together). */
    public ClanSpirit withDecay(SpiritDecayPolicy.State state) {
        return new ClanSpirit(state.level(), state.energy(), awakenedUntil, lastDecayCheck, state.mastery(), masteryDay,
                masteryOffenseToday, masteryDefenseToday, activityScore, activityAt, state.lossProgress(), lastLossNotice);
    }

    public ClanSpirit withLastLossNotice(long at) {
        return new ClanSpirit(level, energy, awakenedUntil, lastDecayCheck, mastery, masteryDay, masteryOffenseToday,
                masteryDefenseToday, activityScore, activityAt, lossProgress, at);
    }
}
