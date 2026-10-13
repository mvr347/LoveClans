package me.lovelace.loveclans.model;

/** The clan's one-time development direction (chosen at level 5, strengthened at 10 and 15). */
public enum ClanDirection {
    FARMING("Земледелие"),
    MINING("Ресурсодобыча"),
    MILITARY("Военное дело");

    private final String displayName;

    ClanDirection(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Tier 0..3 for a clan level, thresholds being the levels where the direction grows. */
    public static int tier(int clanLevel, int first, int second, int third) {
        if (clanLevel >= third) return 3;
        if (clanLevel >= second) return 2;
        if (clanLevel >= first) return 1;
        return 0;
    }
}
