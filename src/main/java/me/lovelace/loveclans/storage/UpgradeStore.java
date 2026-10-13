package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.model.upgrade.UpgradeOffer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for upgrade offers and guild rewards. The SQL is kept in static strings so a test can
 * run the very same statements on SQLite. Rows are read once at startup; afterwards the in-memory
 * state (touched only on the main thread) is the source of truth and writes are fire-and-forget.
 */
public final class UpgradeStore {

    public static final String CREATE_OFFERS = "CREATE TABLE IF NOT EXISTS clan_upgrade_offers ("
            + "id VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "clan_id VARCHAR(36) NOT NULL, "
            + "option1 VARCHAR(32) NOT NULL, "
            + "option2 VARCHAR(32), "
            + "type VARCHAR(16) NOT NULL, "
            + "status VARCHAR(16) NOT NULL, "
            + "chosen VARCHAR(32), "
            + "created_at BIGINT NOT NULL, "
            + "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE)";

    public static final String CREATE_REWARDS = "CREATE TABLE IF NOT EXISTS clan_guild_rewards ("
            + "clan_id VARCHAR(36) NOT NULL, "
            + "reward_id VARCHAR(32) NOT NULL, "
            + "unlocked_at BIGINT NOT NULL, "
            + "PRIMARY KEY (clan_id, reward_id), "
            + "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE)";

    public static final String INSERT_OFFER = "INSERT INTO clan_upgrade_offers "
            + "(id, clan_id, option1, option2, type, status, chosen, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** The status guard makes a second concurrent write a no-op (0 rows) instead of a double spend. */
    public static final String MARK_HANDLED = "UPDATE clan_upgrade_offers SET status = ?, chosen = ? "
            + "WHERE id = ? AND status = 'PENDING'";

    public static final String SELECT_OFFERS = "SELECT id, clan_id, option1, option2, type, status, chosen, created_at "
            + "FROM clan_upgrade_offers ORDER BY created_at, id";

    public static String insertRewardSql(boolean mysql) {
        return mysql
                ? "INSERT IGNORE INTO clan_guild_rewards (clan_id, reward_id, unlocked_at) VALUES (?, ?, ?)"
                : "INSERT OR IGNORE INTO clan_guild_rewards (clan_id, reward_id, unlocked_at) VALUES (?, ?, ?)";
    }

    /**
     * One-shot, idempotent cleanup of the retired upgrade/perk system: levels of the removed
     * TERRITORIES upgrade come back as points (the row is deleted afterwards, so a second run adds
     * nothing) and the old perk names are reset so those clans are offered a direction instead.
     */
    public static List<String> legacyMigrationSql() {
        return List.of(
                "UPDATE clans SET upgrade_points = upgrade_points + COALESCE((SELECT SUM(u.upgrade_level) "
                        + "FROM clan_upgrades u WHERE u.clan_id = clans.id AND u.upgrade_name = 'TERRITORIES'), 0)",
                "DELETE FROM clan_upgrades WHERE upgrade_name = 'TERRITORIES'",
                "UPDATE clans SET perk = NULL, perk_chosen_at = 0 WHERE perk IN ('HARVESTER', 'MINER', 'WARRIOR')");
    }

    private final DatabaseManager database;

    public UpgradeStore(DatabaseManager database) {
        this.database = database;
    }

    public static void createTables(Statement statement) throws SQLException {
        statement.executeUpdate(CREATE_OFFERS);
        statement.executeUpdate(CREATE_REWARDS);
    }

    public static void runLegacyMigration(Statement statement) throws SQLException {
        for (String sql : legacyMigrationSql()) {
            statement.executeUpdate(sql);
        }
    }

    public Map<UUID, List<UpgradeOffer>> loadOffers() {
        Map<UUID, List<UpgradeOffer>> result = new HashMap<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_OFFERS);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                UpgradeOffer offer = new UpgradeOffer(
                        UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("clan_id")),
                        UpgradeOffer.Kind.valueOf(rows.getString("type")),
                        rows.getString("option1"),
                        rows.getString("option2"),
                        UpgradeOffer.Status.valueOf(rows.getString("status")),
                        rows.getString("chosen"),
                        rows.getLong("created_at"));
                result.computeIfAbsent(offer.clanId(), k -> new ArrayList<>()).add(offer);
            }
        } catch (SQLException exception) {
            throw new StorageException("Unable to load upgrade offers", exception);
        }
        return result;
    }

    public Map<UUID, Map<String, Long>> loadRewards() {
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT clan_id, reward_id, unlocked_at FROM clan_guild_rewards");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.computeIfAbsent(UUID.fromString(rows.getString("clan_id")), k -> new HashMap<>())
                        .put(rows.getString("reward_id"), rows.getLong("unlocked_at"));
            }
        } catch (SQLException exception) {
            throw new StorageException("Unable to load guild rewards", exception);
        }
        return result;
    }

    public CompletableFuture<Void> insertOffer(UpgradeOffer offer) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(INSERT_OFFER)) {
                statement.setString(1, offer.id().toString());
                statement.setString(2, offer.clanId().toString());
                statement.setString(3, offer.option1());
                statement.setString(4, offer.option2());
                statement.setString(5, offer.kind().name());
                statement.setString(6, offer.status().name());
                statement.setString(7, offer.chosen());
                statement.setLong(8, offer.createdAt());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to save upgrade offer " + offer.id(), exception);
            }
        }, database.executor());
    }

    public CompletableFuture<Void> markHandled(UpgradeOffer offer) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(MARK_HANDLED)) {
                statement.setString(1, offer.status().name());
                statement.setString(2, offer.chosen());
                statement.setString(3, offer.id().toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update upgrade offer " + offer.id(), exception);
            }
        }, database.executor());
    }

    public CompletableFuture<Void> insertReward(UUID clanId, String rewardId, long unlockedAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         insertRewardSql(database.type() == DatabaseType.MYSQL))) {
                statement.setString(1, clanId.toString());
                statement.setString(2, rewardId);
                statement.setLong(3, unlockedAt);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to save guild reward " + rewardId, exception);
            }
        }, database.executor());
    }
}
