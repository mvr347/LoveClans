package me.lovelace.loveclans.model;

import me.lovelace.loveclans.model.spirit.SpiritAbility;

public record ClanSpirit(int level, long energy, long awakenedUntil, long onlineTimeWeekly, long lastDecayCheck, SpiritAbility ability) {

    public static ClanSpirit fresh() {
        return new ClanSpirit(1, 0L, 0L, 0L, System.currentTimeMillis(), null);
    }

    public boolean awakened(long now) {
        return awakenedUntil > now;
    }

    public ClanSpirit withLevel(int level) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability);
    }

    public ClanSpirit addEnergy(long amount) {
        return new ClanSpirit(level, Math.max(0L, energy + amount), awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability);
    }

    public ClanSpirit awakenUntil(long timestamp) {
        return new ClanSpirit(level, energy, timestamp, onlineTimeWeekly, lastDecayCheck, ability);
    }

    public ClanSpirit withOnlineTimeWeekly(long onlineTimeWeekly) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability);
    }

    public ClanSpirit withLastDecayCheck(long lastDecayCheck) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability);
    }

    public ClanSpirit withAbility(SpiritAbility ability) {
        return new ClanSpirit(level, energy, awakenedUntil, onlineTimeWeekly, lastDecayCheck, ability);
    }
}
