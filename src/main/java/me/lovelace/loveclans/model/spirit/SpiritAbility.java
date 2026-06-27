package me.lovelace.loveclans.model.spirit;

public enum SpiritAbility {
    PHOENIX("Феникс",
            "<yellow>Пассивный баф:</yellow>\n" +
            "<green>Устойчивость к огню</green>\n" +
            "\n" +
            "<yellow>Во время войны клана:</yellow>\n" +
            "<green>+4 сердца</green>\n" +
            "\n" +
            "<yellow>Здоровье < 50%:</yellow>\n" +
            "<green>Регенерация II (40%) / III (30%) / IV (25%)</green>\n" +
            "<red>Поджигает атакующего</red>\n" +
            "\n" +
            "<yellow>Здоровье < 30%:</yellow>\n" +
            "<green>Ускорение</green>\n" +
            "<red>Замедляет удары атакующего</red>"),
    BERSERKER("Берсерк",
            "<yellow>Пассивный баф:</yellow>\n" +
            "<green>Сопротивление урону I</green>\n" +
            "\n" +
            "<yellow>Здоровье < 50%:</yellow>\n" +
            "<green>Скорость атаки +0.1% за каждый % нехватки HP (макс. +20%)</green>\n" +
            "\n" +
            "<yellow>Здоровье < 20%:</yellow>\n" +
            "<green>Сопротивление урону I</green>\n" +
            "<green>Регенерация II</green>"),
    SANCTUARY("Святилище",
            "<yellow>Пассивный баф:</yellow>\n" +
            "<green>+3 к броне</green>\n" +
            "\n" +
            "<yellow>Здоровье < 50%:</yellow>\n" +
            "<green>Сопротивление урону I (II на 35%, III на 30%, IV на 20%)</green>\n" +
            "\n" +
            "<yellow>Здоровье < 30%:</yellow>\n" +
            "<green>+5 к броне (+10 на 20%)</green>");

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
