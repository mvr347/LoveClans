package me.lovelace.loveclans.model.diplomacy;

/**
 * Типы соглашений (пактов) между кланами (§5).
 */
public enum PactType {
    /** Пакт о ненападении (Non-Aggression Pact). Порог score >= -10. Запрещает войну/осаду/рейд. */
    NAP("Ненападение", -10),
    /** Торговое соглашение. Порог score >= +20. Даёт бонусы и сокращает кулдауны торговли. */
    TRADE("Торговое соглашение", 20),
    /** Союз. Порог score >= +90. Взаимное согласие, блокирует урон/атаки. */
    ALLIANCE("Союз", 90);

    private final String displayName;
    private final int minScoreRequired;

    PactType(String displayName, int minScoreRequired) {
        this.displayName = displayName;
        this.minScoreRequired = minScoreRequired;
    }

    public String displayName() {
        return displayName;
    }

    public int minScoreRequired() {
        return minScoreRequired;
    }
}
