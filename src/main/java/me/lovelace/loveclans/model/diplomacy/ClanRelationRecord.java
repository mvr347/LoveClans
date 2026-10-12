package me.lovelace.loveclans.model.diplomacy;

import java.util.Objects;
import java.util.UUID;

/**
 * Однонаправленное отношение клана source к клану target со шкалой score от -100 до +100 (§1).
 * Хранит значение score 24 часа назад для вычисления тренда изменения (▲/▼).
 */
public record ClanRelationRecord(
        UUID sourceClanId,
        UUID targetClanId,
        int score,
        int score24hAgo,
        long lastTrendUpdate,
        long updatedAt
) {
    public static final int MIN_SCORE = -100;
    public static final int MAX_SCORE = 100;

    public ClanRelationRecord {
        Objects.requireNonNull(sourceClanId, "sourceClanId");
        Objects.requireNonNull(targetClanId, "targetClanId");
        score = clamp(score);
        score24hAgo = clamp(score24hAgo);
    }

    public static int clamp(int val) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, val));
    }

    public static ClanRelationRecord createDefault(UUID sourceClanId, UUID targetClanId) {
        long now = System.currentTimeMillis();
        return new ClanRelationRecord(sourceClanId, targetClanId, 0, 0, now, now);
    }

    public static ClanRelationRecord of(UUID sourceClanId, UUID targetClanId, int score) {
        long now = System.currentTimeMillis();
        return new ClanRelationRecord(sourceClanId, targetClanId, score, score, now, now);
    }

    /**
     * Изменение score за последние 24 часа.
     */
    public int trend() {
        return score - score24hAgo;
    }

    public int trendDiff() {
        return trend();
    }

    /**
     * Юникод-символ тренда для GUI (§1):
     * ▲ / ▲▲ / ▲▲▲ при росте, ▼ / ▼▼ при падении, — при стабильности.
     */
    public String trendUnicode() {
        int diff = trend();
        if (diff >= 15) return "▲▲▲";
        if (diff >= 6) return "▲▲";
        if (diff > 0) return "▲";
        if (diff <= -15) return "▼▼▼";
        if (diff <= -6) return "▼▼";
        if (diff < 0) return "▼";
        return "—";
    }

    public String trendSymbol() {
        return trendUnicode();
    }

    public ClanRelationRecord withScore(int newScore, long now) {
        return new ClanRelationRecord(sourceClanId, targetClanId, clamp(newScore), score24hAgo, lastTrendUpdate, now);
    }

    public ClanRelationRecord withAdjustedScore(int delta, long now) {
        return withScore(score + delta, now);
    }

    /**
     * Сдвигает baseline 24-часового тренда, если прошло >= 24 часов с последней фиксации.
     */
    public ClanRelationRecord checkAndUpdate24hBaseline(long now) {
        long dayMillis = 24L * 3600_000L;
        if (now - lastTrendUpdate >= dayMillis) {
            return new ClanRelationRecord(sourceClanId, targetClanId, score, score, now, updatedAt);
        }
        return this;
    }
}
