package me.lovelace.loveclans.model;

import me.lovelace.loveclans.model.spirit.SpiritAbility;

public record ClanSpirit(int level, long energy, long awakenedUntil, long onlineTimeWeekly, long lastDecayCheck, SpiritAbility ability, long abilityChosenAt) {

    public static final long ABILITY_CHANGE_COOLDOWN_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    public static ClanSpirit fresh() {
        return new ClanSpirit(1, 0L, 0L, 0L, System.currentTimeMillis(), null, 0L);
    }

    public boolean awakened(long now) {
        return awakenedUntil > now;
    }

    public ClanSpirit withLevel(int level) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability, abilityChosenAt);
    }

    public ClanSpirit addEnergy(long amount) {
        return new ClanSpirit(level, Math.max(0L, energy + amount), awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability, abilityChosenAt);
    }

    public ClanSpirit awakenUntil(long timestamp) {
        return new ClanSpirit(level, energy, timestamp, onlineTimeWeekly, lastDecayCheck, ability, abilityChosenAt);
    }

    public ClanSpirit withOnlineTimeWeekly(long onlineTimeWeekly) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability, abilityChosenAt);
    }

    public ClanSpirit withLastDecayCheck(long lastDecayCheck) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability, abilityChosenAt);
    }

    public ClanSpirit withAbility(SpiritAbility ability, long chosenAt) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability, chosenAt);
    }

    public long abilityCooldownRemaining(long now) {
        if (ability == null) return 0L;
        return Math.max(0L, abilityChosenAt + ABILITY_CHANGE_COOLDOWN_MILLIS - now);
    }
}
