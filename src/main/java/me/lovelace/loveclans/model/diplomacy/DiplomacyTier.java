package me.lovelace.loveclans.model.diplomacy;

import me.lovelace.loveclans.model.DiplomacyRelation;

/**
 * 6 тиров отношений в Дипломатии v2 (§1):
 * <ul>
 *   <li>−100…−60: Враждебные (hostile, ▼ / ▼▼)</li>
 *   <li>−59…−20: Натянутые (unfriendly / cool, ▼)</li>
 *   <li>−19…+19: Нейтральные (neutral, — / ·)</li>
 *   <li>+20…+59: Дружелюбные (cordial, ▲)</li>
 *   <li>+60…+89: Друзья (friendly, ▲▲)</li>
 *   <li>+90…+100: Союзники (allied, ▲▲▲) — только при активном пакте союза</li>
 * </ul>
 */
public enum DiplomacyTier {
    HOSTILE("Враждебные", "§c", "relation-hostile", "▼"),
    UNFRIENDLY("Натянутые", "§6", "relation-cool", "▼"),
    NEUTRAL("Нейтральные", "§7", "relation-neutral", "—"),
    CORDIAL("Дружелюбные", "§a", "relation-cordial", "▲"),
    FRIENDLY("Друзья", "§2", "relation-friendly", "▲▲"),
    ALLIED("Союзники", "§b", "relation-allied", "▲▲▲");

    private final String defaultTitle;
    private final String colorCode;
    private final String headKey;
    private final String defaultTrendSymbol;

    DiplomacyTier(String defaultTitle, String colorCode, String headKey, String defaultTrendSymbol) {
        this.defaultTitle = defaultTitle;
        this.colorCode = colorCode;
        this.headKey = headKey;
        this.defaultTrendSymbol = defaultTrendSymbol;
    }

    public String defaultTitle() {
        return defaultTitle;
    }

    public String colorCode() {
        return colorCode;
    }

    public String headKey() {
        return headKey;
    }

    public String defaultTrendSymbol() {
        return defaultTrendSymbol;
    }

    public String coloredTitle() {
        return colorCode + defaultTitle;
    }

    /**
     * Преобразует числовой score в тир отношений с учётом наличия взаимного пакта союза.
     */
    public static DiplomacyTier fromScore(int score, boolean hasAlliancePact) {
        if (hasAlliancePact && score >= alliedMin) {
            return ALLIED;
        }
        if (score >= friendlyMin) {
            return FRIENDLY;
        }
        if (score >= cordialMin) {
            return CORDIAL;
        }
        if (score >= neutralMin) {
            return NEUTRAL;
        }
        if (score >= unfriendlyMin) {
            return UNFRIENDLY;
        }
        return HOSTILE;
    }

    // Нижние границы тиров. Читаются из config.yml (diplomacy.tiers), значения по умолчанию совпадают с таблицей выше.
    private static volatile int alliedMin = 90;
    private static volatile int friendlyMin = 60;
    private static volatile int cordialMin = 20;
    private static volatile int neutralMin = -19;
    private static volatile int unfriendlyMin = -59;

    /** Задаёт нижние границы тиров; вызывается при загрузке конфига. Некорректный порядок игнорируется. */
    public static void configure(int unfriendly, int neutral, int cordial, int friendly, int allied) {
        if (!(unfriendly < neutral && neutral < cordial && cordial < friendly && friendly <= allied)) {
            return;
        }
        unfriendlyMin = unfriendly;
        neutralMin = neutral;
        cordialMin = cordial;
        friendlyMin = friendly;
        alliedMin = allied;
    }

    public static void resetThresholds() {
        configure(-59, -19, 20, 60, 90);
    }

    /** Нижняя граница тира (для подсказок «до следующего тира»); у HOSTILE — минимум шкалы. */
    public int minScore() {
        return switch (this) {
            case HOSTILE -> ClanRelationRecord.MIN_SCORE;
            case UNFRIENDLY -> unfriendlyMin;
            case NEUTRAL -> neutralMin;
            case CORDIAL -> cordialMin;
            case FRIENDLY -> friendlyMin;
            case ALLIED -> alliedMin;
        };
    }

    /**
     * Обратная совместимость с легаси enum {@link DiplomacyRelation}.
     */
    public DiplomacyRelation toLegacyRelation() {
        return switch (this) {
            case ALLIED -> DiplomacyRelation.ALLY;
            case HOSTILE -> DiplomacyRelation.ENEMY;
            default -> DiplomacyRelation.NEUTRAL;
        };
    }
}
