package me.lovelace.loveclans.model.spirit;

public enum SpiritAbility {
    PHOENIX("Феникс", "Базово: устойчивость к огню. Во время войны клана: +4 сердца. Здоровье < 50%: регенерация (II на 40%, III на 30%, IV на 25%) и поджигает атакующего. Здоровье < 30%: ускорение и замедляет удары атакующего."),
    BERSERKER("Берсерк", "Базово: сопротивление урону I. Здоровье < 50%: скорость атаки +0.1% за каждый процент нехватки (макс. +20%). Здоровье < 20%: сопротивление урону I и регенерация II."),
    SANCTUARY("Святилище", "Базово: +3 к броне. Здоровье < 50%: сопротивление урону I (II на 35%, III на 30%, IV на 20%). Здоровье < 30%: +5 к броне (+10 на 20%).");

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
