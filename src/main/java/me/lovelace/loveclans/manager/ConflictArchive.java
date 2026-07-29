package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.history.ConflictRecord;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Архив завершённых конфликтов. Войны, осады и набеги жили только в памяти: после
 * окончания от них не оставалось ничего, кроме счётчиков побед у клана. Из-за этого
 * нельзя было ни показать историю противостояния, ни отличить свежую победу от
 * прошлогодней при расчёте влияния.
 *
 * Отменённые конфликты не записываются: они ничем не закончились и историей не являются.
 */
public final class ConflictArchive {

    /** Сколько записей архива просматривается при расчёте свежести заслуг. */
    private static final int FRESHNESS_SCAN_LIMIT = 500;

    private final LoveClansPlugin plugin;
    /** Свежесть заслуг по кланам: влияние считается синхронно, ходить в базу оттуда нельзя. */
    private final Map<UUID, Double> freshness = new ConcurrentHashMap<>();

    public ConflictArchive(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Записывает завершённый конфликт.
     *
     * @param winnerClanId победитель; {@code null} — ничья
     */
    public void record(ConflictKind kind, UUID attackerClanId, UUID defenderClanId, UUID winnerClanId,
                       int attackerScore, int defenderScore, long startedAt) {
        if (attackerClanId == null || defenderClanId == null) {
            return;
        }
        ConflictRecord record = new ConflictRecord(
                UUID.randomUUID(), kind, attackerClanId, defenderClanId, winnerClanId,
                attackerScore, defenderScore, startedAt, System.currentTimeMillis());

        plugin.getStorage().saveConflictAsync(record)
                .thenRun(() -> {
                    refreshFreshnessAsync(attackerClanId);
                    refreshFreshnessAsync(defenderClanId);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                        "Не удалось записать конфликт в архив — история противостояния останется неполной", throwable);
                    return null;
                });
    }

    /**
     * Свежесть боевых заслуг клана: доля его побед, одержанных за последние
     * {@code history.influence-decay-days} дней. Единица — все победы свежие,
     * нижняя граница ({@code history.influence-decay-floor}) — все давние.
     *
     * Значение кэшируется, потому что влияние пересчитывается синхронно на главном
     * потоке, а архив живёт в базе. Клан без записей в архиве получает единицу:
     * иначе у всех кланов, воевавших до появления архива, влияние обрушилось бы разом.
     */
    public double freshnessOf(UUID clanId) {
        if (clanId == null) return 1.0;
        Double cached = freshness.get(clanId);
        return cached == null ? 1.0 : cached;
    }

    /** Пересчитывает свежесть заслуг клана по архиву и кладёт в кэш. */
    public CompletableFuture<Double> refreshFreshnessAsync(UUID clanId) {
        int decayDays = plugin.getConfig().getInt("history.influence-decay-days", 30);
        if (clanId == null || decayDays <= 0) {
            if (clanId != null) freshness.put(clanId, 1.0);
            return CompletableFuture.completedFuture(1.0);
        }

        double floor = plugin.getConfig().getDouble("history.influence-decay-floor", 0.25);
        long cutoff = System.currentTimeMillis() - decayDays * 86_400_000L;

        return historyOf(clanId, FRESHNESS_SCAN_LIMIT).thenApply(records -> {
            long total = records.stream().filter(record -> record.wonBy(clanId)).count();
            if (total == 0) {
                freshness.put(clanId, 1.0);
                return 1.0;
            }
            long recent = records.stream()
                    .filter(record -> record.wonBy(clanId) && record.endedAt() >= cutoff)
                    .count();
            double value = Math.max(floor, (double) recent / total);
            freshness.put(clanId, value);
            return value;
        });
    }

    public void forget(UUID clanId) {
        if (clanId != null) freshness.remove(clanId);
    }

    public CompletableFuture<List<ConflictRecord>> historyOf(UUID clanId, int limit) {
        if (clanId == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return plugin.getStorage().loadConflictsForClanAsync(clanId, limit)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Не удалось прочитать архив конфликтов клана", throwable);
                    return Collections.emptyList();
                });
    }

    public CompletableFuture<List<ConflictRecord>> historyBetween(UUID first, UUID second, int limit) {
        if (first == null || second == null || first.equals(second)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return plugin.getStorage().loadConflictsBetweenAsync(first, second, limit)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Не удалось прочитать историю противостояния", throwable);
                    return Collections.emptyList();
                });
    }
}
