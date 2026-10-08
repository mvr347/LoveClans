package me.lovelace.loveclans.activity;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.storage.DatabaseManager;
import me.lovelace.loveclans.storage.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * {@link ActivityService} implementation: totals live in an {@link ActivityLedger} and reach the database through a
 * periodic asynchronous flush, so a kill or a tick never waits for SQL.
 */
public final class ActivityManager implements ActivityService {
    private static final long PROCESSED_KEEP_MILLIS = 14L * 24 * 3600_000L;

    private final LoveClansPlugin plugin;
    private final DatabaseManager database;
    private final ActivityLedger ledger = new ActivityLedger();
    private final Queue<String> pendingProcessed = new ConcurrentLinkedQueue<>();

    public ActivityManager(LoveClansPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public ActivityLedger ledger() {
        return ledger;
    }

    private boolean mysql() {
        return database.type() == DatabaseType.MYSQL;
    }

    /** Blocking: call from an async startup task. Loads lifetime, current week and current month. */
    public void load() {
        long now = System.currentTimeMillis();
        try (Connection connection = database.dataSource().getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(ActivitySchema.LOAD_PERIODS)) {
                ps.setString(1, PeriodKeys.of(ActivityPeriod.WEEKLY, now));
                ps.setString(2, PeriodKeys.of(ActivityPeriod.MONTHLY, now));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            ledger.load(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3),
                                    ActivityCategory.valueOf(rs.getString(4)), rs.getLong(5));
                        } catch (IllegalArgumentException ignored) {
                            // unknown category from a newer version: skip the row
                        }
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(ActivitySchema.LOAD_PROCESSED)) {
                ps.setLong(1, now - PROCESSED_KEEP_MILLIS);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ledger.markProcessed(rs.getString(1));
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(ActivitySchema.PRUNE_PROCESSED)) {
                ps.setLong(1, now - PROCESSED_KEEP_MILLIS);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load clan activity: " + e.getMessage(), e);
        }
    }

    /** Writes everything changed since the last flush on the storage thread. */
    public void flushAsync() {
        List<ActivityLedger.Cell> cells = ledger.drainDirty();
        List<String> keys = drainProcessed();
        if (cells.isEmpty() && keys.isEmpty()) return;
        database.executor().execute(() -> write(cells, keys));
    }

    /** Blocking flush for onDisable: the storage executor is still alive at that point. */
    public void flushNow() {
        write(ledger.drainDirty(), drainProcessed());
    }

    private List<String> drainProcessed() {
        List<String> keys = new java.util.ArrayList<>();
        String key;
        while ((key = pendingProcessed.poll()) != null) keys.add(key);
        return keys;
    }

    private void write(List<ActivityLedger.Cell> cells, List<String> keys) {
        if (cells.isEmpty() && keys.isEmpty()) return;
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(ActivitySchema.upsertSql(mysql()));
                 PreparedStatement processed = connection.prepareStatement(ActivitySchema.insertProcessedSql(mysql()))) {
                for (ActivityLedger.Cell cell : cells) {
                    upsert.setString(1, cell.clan().toString());
                    upsert.setString(2, cell.player().toString());
                    upsert.setString(3, cell.period());
                    upsert.setString(4, cell.category().name());
                    upsert.setLong(5, cell.points());
                    upsert.addBatch();
                }
                upsert.executeBatch();
                long now = System.currentTimeMillis();
                for (String key : keys) {
                    processed.setString(1, key);
                    processed.setLong(2, now);
                    processed.addBatch();
                }
                processed.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save clan activity (will retry): " + e.getMessage(), e);
            ledger.requeue(cells);
            pendingProcessed.addAll(keys);
        }
    }

    // --- ActivityService ---

    @Override
    public void addPlayerActivity(UUID playerId, UUID clanId, ActivityCategory category, int amount) {
        ledger.add(clanId, playerId, category, amount, System.currentTimeMillis());
    }

    /**
     * Pays {@code amount} once per {@code eventKey}: a second call with the same key (a repeated event, or a
     * conflict that finished right before a restart) does nothing.
     */
    public void awardOnce(String eventKey, UUID playerId, UUID clanId, ActivityCategory category, int amount) {
        if (amount <= 0 || !ledger.claim(eventKey)) return;
        pendingProcessed.add(eventKey);
        addPlayerActivity(playerId, clanId, category, amount);
    }

    @Override
    public PlayerActivity getPlayerActivity(UUID playerId) {
        return getPlayerActivity(playerId, ActivityPeriod.LIFETIME);
    }

    @Override
    public ClanActivity getClanActivity(UUID clanId) {
        return getClanActivity(clanId, ActivityPeriod.LIFETIME);
    }

    @Override
    public PlayerActivity getPlayerActivity(UUID playerId, ActivityPeriod period) {
        return PlayerActivity.of(playerId, ledger.playerTotals(playerId, period, System.currentTimeMillis()));
    }

    @Override
    public ClanActivity getClanActivity(UUID clanId, ActivityPeriod period) {
        return ClanActivity.of(clanId, ledger.clanTotals(clanId, period, System.currentTimeMillis()));
    }

    @Override
    public List<PlayerActivity> getClanMembersRanking(UUID clanId, ActivityPeriod period) {
        return ledger.membersRanking(clanId, period, System.currentTimeMillis());
    }

    @Override
    public List<ClanActivity> getClanRanking(ActivityPeriod period, int limit) {
        return ledger.clanRanking(period, System.currentTimeMillis(), limit);
    }

    public void purgeClan(UUID clanId) {
        ledger.dropClan(clanId);
        database.executor().execute(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement("DELETE FROM clan_activity WHERE clan_id = ?")) {
                ps.setString(1, clanId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete activity of clan " + clanId + ": " + e.getMessage(), e);
            }
        });
    }
}
