package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;

/**
 * Единая матрица «что можно делать при каком уровне отношений». Её читают и GUI (кнопка активна или серая с причиной),
 * и менеджеры (серверная проверка), поэтому описание кнопки и реальное поведение не расходятся.
 */
public final class DiplomacyActions {
    private DiplomacyActions() {
    }

    public enum Action {
        GIFT, TRADE, IMPROVE, INSULT, EMBARGO, BLOCKADE, CONFLICT, NAP, TRADE_PACT, ALLIANCE
    }

    /** Состояние пары кланов, от которого зависят доступные действия. */
    public record Context(DiplomacyTier tier, int score, boolean nap, boolean tradePact, boolean alliance) {
        public boolean anyPact() {
            return nap || tradePact || alliance;
        }
    }

    /**
     * Возвращает ключ lang с причиной отказа или {@code null}, если действие доступно.
     *
     * @param minScore минимальный score для пактов (из конфига); для остальных действий игнорируется
     */
    public static String denyKey(Action action, Context ctx, int minScore) {
        DiplomacyTier tier = ctx.tier();
        boolean bad = tier == DiplomacyTier.HOSTILE || tier == DiplomacyTier.UNFRIENDLY;
        return switch (action) {
            case GIFT -> tier == DiplomacyTier.HOSTILE ? "diplomacy.deny.gift-hostile" : null;
            case TRADE -> tier == DiplomacyTier.HOSTILE ? "diplomacy.deny.trade-hostile" : null;
            case IMPROVE -> ctx.alliance() ? "diplomacy.deny.improve-allied" : null;
            case INSULT -> ctx.anyPact() ? "diplomacy.deny.insult-pact"
                    : tier.ordinal() >= DiplomacyTier.FRIENDLY.ordinal() ? "diplomacy.deny.insult-friends" : null;
            case EMBARGO -> ctx.anyPact() ? "diplomacy.deny.sanction-pact" : bad ? null : "diplomacy.deny.embargo-tier";
            case BLOCKADE -> ctx.anyPact() ? "diplomacy.deny.sanction-pact"
                    : tier == DiplomacyTier.HOSTILE ? null : "diplomacy.deny.blockade-tier";
            case CONFLICT -> ctx.nap() || ctx.alliance() ? "diplomacy.deny.conflict-pact"
                    : tier.ordinal() > DiplomacyTier.NEUTRAL.ordinal() ? "diplomacy.deny.conflict-tier" : null;
            case NAP, TRADE_PACT, ALLIANCE -> ctx.score() < minScore ? "diplomacy.pact.score-too-low" : null;
        };
    }

    public static boolean allowed(Action action, Context ctx, int minScore) {
        return denyKey(action, ctx, minScore) == null;
    }

    /**
     * Whether the action makes sense for this pair at all, as opposed to being merely blocked right now. The menu hides
     * irrelevant buttons (no embargo against a friend, no pact offers to an enemy) and keeps relevant-but-blocked ones
     * grey with the reason (cooldown, money, rights). Every tier and pact rule of {@link #denyKey} is structural; only
     * the pacts keep a hint window: an offer stays visible while the score is within {@code hintMargin} of its minimum,
     * so the goal is on screen before it can be reached.
     */
    public static boolean relevant(Action action, Context ctx, int minScore, int hintMargin) {
        return switch (action) {
            case NAP, TRADE_PACT, ALLIANCE -> ctx.score() >= minScore - Math.max(0, hintMargin);
            default -> denyKey(action, ctx, minScore) == null;
        };
    }
}
