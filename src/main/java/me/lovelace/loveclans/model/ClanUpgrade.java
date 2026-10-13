package me.lovelace.loveclans.model;

public enum ClanUpgrade {
    MEMBERS("Состав клана"),
    CLAIM_SIZE("Размер территории"),
    EXPERIENCE("Развитие клана"),
    CHEST("Сундук"),
    TAX("Налоги");

    private final String displayName;

    ClanUpgrade(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
