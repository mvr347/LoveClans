package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.LoveClansPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists the war / siege / raid pair cooldowns. They used to live only in memory, so a server restart reset every
 * 24h cooldown and the same pair could start a new conflict at once. The write is a DELETE + INSERT in one
 * transaction (portable between SQLite and MySQL, no UPSERT dialect) and runs on a Bukkit async task.
 */
public final class ConflictCooldownStore {
    public static final String WAR = "WAR";
    public static final String SIEGE = "SIEGE";
    public static final String RAID = "RAID";

    private final LoveClansPlugin plugin;
    private final DataSource dataSource;

    public ConflictCooldownStore(LoveClansPlugin plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }

    public static void createTable(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_conflict_cooldowns (
                    kind VARCHAR(8) NOT NULL,
                    clan_a VARCHAR(36) NOT NULL,
                    clan_b VARCHAR(36) NOT NULL,
                    started_at BIGINT NOT NULL,
                    PRIMARY KEY (kind, clan_a, clan_b)
                )
                """);
    }

    /** Reads every stored cooldown of a kind into {@code target} (keys as built by the managers). Blocking call. */
    public void loadInto(String kind, Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> target) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT clan_a, clan_b, started_at FROM clan_conflict_cooldowns WHERE kind = ?")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    target.put(new AbstractMap.SimpleImmutableEntry<>(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2))),
                            rs.getLong(3));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load " + kind + " cooldowns: " + e.getMessage(), e);
        }
    }

    public void saveAsync(String kind, AbstractMap.SimpleImmutableEntry<UUID, UUID> pair, long startedAt) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement(
                             "DELETE FROM clan_conflict_cooldowns WHERE kind = ? AND clan_a = ? AND clan_b = ?");
                     PreparedStatement insert = connection.prepareStatement(
                             "INSERT INTO clan_conflict_cooldowns (kind, clan_a, clan_b, started_at) VALUES (?, ?, ?, ?)")) {
                    delete.setString(1, kind);
                    delete.setString(2, pair.getKey().toString());
                    delete.setString(3, pair.getValue().toString());
                    delete.executeUpdate();
                    insert.setString(1, kind);
                    insert.setString(2, pair.getKey().toString());
                    insert.setString(3, pair.getValue().toString());
                    insert.setLong(4, startedAt);
                    insert.executeUpdate();
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + kind + " cooldown: " + e.getMessage(), e);
            }
        });
    }

    /** Removes the rows of a disbanded clan (the managers already drop them from memory). */
    public void deleteClanAsync(UUID clanId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM clan_conflict_cooldowns WHERE clan_a = ? OR clan_b = ?")) {
                ps.setString(1, clanId.toString());
                ps.setString(2, clanId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete cooldowns of clan " + clanId + ": " + e.getMessage(), e);
            }
        });
    }
}
