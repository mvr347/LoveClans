package me.lovelace.loveclans.model;

public enum ClanUpgrade {
    MEMBERS("Состав клана"),
    TERRITORIES("Территории"),
    EXPERIENCE("Развитие клана"),
    CHEST("Сундук");

    private final String displayName;

    ClanUpgrade(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
