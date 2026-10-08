package me.lovelace.loveclans.activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory totals of the activity system: the part with all the rules (periods, summing, idempotency) and no
 * database or Bukkit in it. Everything is synchronized: awards come from the main thread, reads from GUI code and
 * the flush task.
 */
public final class ActivityLedger {

    private record Row(UUID clan, UUID player, String period) {}

    /** One persisted cell: the absolute total of a (clan, player, period, category). */
    public record Cell(UUID clan, UUID player, String period, ActivityCategory category, long points) {}

    private static final ActivityPeriod[] STORED = {ActivityPeriod.LIFETIME, ActivityPeriod.WEEKLY, ActivityPeriod.MONTHLY};

    private final Map<Row, long[]> totals = new HashMap<>();
    private final Set<Row> dirtyRows = new LinkedHashSet<>();
    private final Set<String> processed = new HashSet<>();

    public synchronized void add(UUID clan, UUID player, ActivityCategory category, long amount, long nowMillis) {
        if (amount <= 0 || clan == null || player == null || category == null) return;
        for (ActivityPeriod period : STORED) {
            Row row = new Row(clan, player, PeriodKeys.of(period, nowMillis));
            totals.computeIfAbsent(row, r -> new long[ActivityCategory.values().length])[category.ordinal()] += amount;
            dirtyRows.add(row);
        }
    }

    /** True the first time a key is seen: use it so that one event pays exactly once. */
    public synchronized boolean claim(String eventKey) {
        return processed.add(eventKey);
    }

    public synchronized void markProcessed(String eventKey) {
        processed.add(eventKey);
    }

    public synchronized void load(UUID clan, UUID player, String period, ActivityCategory category, long points) {
        totals.computeIfAbsent(new Row(clan, player, period), r -> new long[ActivityCategory.values().length])[category.ordinal()] = points;
    }

    /** All rows changed since the last call, as absolute values ready to be written. */
    public synchronized List<Cell> drainDirty() {
        List<Cell> cells = new ArrayList<>();
        for (Row row : dirtyRows) {
            long[] values = totals.get(row);
            if (values == null) continue;
            for (ActivityCategory category : ActivityCategory.values()) {
                if (values[category.ordinal()] != 0) {
                    cells.add(new Cell(row.clan(), row.player(), row.period(), category, values[category.ordinal()]));
                }
            }
        }
        dirtyRows.clear();
        return cells;
    }

    /** Puts cells back after a failed write so that the next flush retries them. */
    public synchronized void requeue(List<Cell> cells) {
        for (Cell cell : cells) dirtyRows.add(new Row(cell.clan(), cell.player(), cell.period()));
    }

    public synchronized long[] playerTotals(UUID player, ActivityPeriod period, long nowMillis) {
        String key = PeriodKeys.of(period, nowMillis);
        long[] sum = new long[ActivityCategory.values().length];
        for (Map.Entry<Row, long[]> e : totals.entrySet()) {
            if (e.getKey().player().equals(player) && e.getKey().period().equals(key)) addTo(sum, e.getValue());
        }
        return sum;
    }

    public synchronized long[] clanTotals(UUID clan, ActivityPeriod period, long nowMillis) {
        String key = PeriodKeys.of(period, nowMillis);
        long[] sum = new long[ActivityCategory.values().length];
        for (Map.Entry<Row, long[]> e : totals.entrySet()) {
            if (e.getKey().clan().equals(clan) && e.getKey().period().equals(key)) addTo(sum, e.getValue());
        }
        return sum;
    }

    /** Players that earned activity inside the clan, highest total first. */
    public synchronized List<PlayerActivity> membersRanking(UUID clan, ActivityPeriod period, long nowMillis) {
        String key = PeriodKeys.of(period, nowMillis);
        Map<UUID, long[]> byPlayer = new HashMap<>();
        for (Map.Entry<Row, long[]> e : totals.entrySet()) {
            if (e.getKey().clan().equals(clan) && e.getKey().period().equals(key)) {
                addTo(byPlayer.computeIfAbsent(e.getKey().player(), p -> new long[ActivityCategory.values().length]), e.getValue());
            }
        }
        List<PlayerActivity> list = new ArrayList<>();
        byPlayer.forEach((player, values) -> list.add(PlayerActivity.of(player, values)));
        list.sort(Comparator.comparingLong(PlayerActivity::total).reversed());
        return list;
    }

    public synchronized List<ClanActivity> clanRanking(ActivityPeriod period, long nowMillis, int limit) {
        String key = PeriodKeys.of(period, nowMillis);
        Map<UUID, long[]> byClan = new HashMap<>();
        for (Map.Entry<Row, long[]> e : totals.entrySet()) {
            if (e.getKey().period().equals(key)) {
                addTo(byClan.computeIfAbsent(e.getKey().clan(), c -> new long[ActivityCategory.values().length]), e.getValue());
            }
        }
        List<ClanActivity> list = new ArrayList<>();
        byClan.forEach((clan, values) -> list.add(ClanActivity.of(clan, values)));
        list.sort(Comparator.comparingLong(ClanActivity::total).reversed());
        return limit > 0 && list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /** A disbanded clan's rows go with it. */
    public synchronized void dropClan(UUID clan) {
        totals.keySet().removeIf(r -> r.clan().equals(clan));
        dirtyRows.removeIf(r -> r.clan().equals(clan));
    }

    private static void addTo(long[] target, long[] source) {
        for (int i = 0; i < target.length; i++) target[i] += source[i];
    }
}
