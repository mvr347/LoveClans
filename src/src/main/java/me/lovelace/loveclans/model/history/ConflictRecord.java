package me.lovelace.loveclans.model.history;

import java.util.UUID;

/**
 * Запись завершённого конфликта. До появления архива война существовала только в памяти:
 * после окончания от неё не оставалось ничего, кроме счётчиков побед и поражений у клана,
 * поэтому нельзя было ни показать историю противостояния, ни дать бонус за реванш,
 * ни учесть давность победы при расчёте влияния.
 *
 * @param winnerClanId победитель; {@code null} — ничья или отменённый конфликт
 */
public record ConflictRecord(
        UUID id,
        ConflictKind kind,
        UUID attackerClanId,
        UUID defenderClanId,
        UUID winnerClanId,
        int attackerScore,
        int defenderScore,
        long startedAt,
        long endedAt
) {

    public boolean involves(UUID clanId) {
        return attackerClanId.equals(clanId) || defenderClanId.equals(clanId);
    }

    public boolean between(UUID first, UUID second) {
        return involves(first) && involves(second) && !first.equals(second);
    }

    /** Победил ли клан в этом конфликте. Ничья и отмена победой не считаются. */
    public boolean wonBy(UUID clanId) {
        return winnerClanId != null && winnerClanId.equals(clanId);
    }

    /** Проиграл ли клан: победитель есть, и это не он. */
    public boolean lostBy(UUID clanId) {
        return winnerClanId != null && involves(clanId) && !winnerClanId.equals(clanId);
    }

    /** Противник клана в этом конфликте. */
    public UUID opponentOf(UUID clanId) {
        return attackerClanId.equals(clanId) ? defenderClanId : attackerClanId;
    }
}
