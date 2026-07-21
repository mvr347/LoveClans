package me.lovelace.loveclans.model;

public enum ClanPerk {
    HARVESTER("Земледелие"),
    MINER("Ресурсодобыча"),
    WARRIOR("Война");

    private final String displayName;

    ClanPerk(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
