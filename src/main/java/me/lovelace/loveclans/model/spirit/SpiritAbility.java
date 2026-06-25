package me.lovelace.loveclans.model.spirit;

public enum SpiritAbility {
    PHOENIX("Феникс", "При смертельном уроне на своей территории клан спасает игрока от гибели, оставляя ему 30% здоровья. Раз в 30 минут на каждого игрока."),
    BERSERKER("Берсерк", "Если здоровье ниже 30%, на своей территории игрок наносит на 25% больше урона."),
    SANCTUARY("Святилище", "На своей территории клан получает на 20% меньше урона от игроков враждующих кланов.");

    private final String displayName;
    private final String description;

    SpiritAbility(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
