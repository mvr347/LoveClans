package me.lovelace.loveclans.history;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.storage.DatabaseManager;

import java.lang.reflect.Type;
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
import java.util.logging.Level;

public final class HistoryManager implements ClanHistoryService {
    private static final Gson GSON = new Gson();
    private static final Type METADATA_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private final LoveClansPlugin plugin;
    private final DatabaseManager database;

    public HistoryManager(LoveClansPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** Index creation tolerant of "already exists" (MySQL cannot say IF NOT EXISTS). */
    public static void createIndexes(Statement statement) {
        for (String sql : HistorySchema.createIndexSql()) {
            try {
                statement.executeUpdate(sql);
            } catch (SQLException ignored) {
                // already there
            }
        }
    }

    @Override
    public void add(UUID clanId, HistoryType type, UUID actorId, Map<String, String> metadata) {
        if (clanId == null || type == null) return;
        long now = System.currentTimeMillis();
        String json = metadata == null || metadata.isEmpty() ? null : GSON.toJson(metadata);
        database.executor().execute(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement(HistorySchema.INSERT)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, clanId.toString());
                ps.setString(3, type.name());
                ps.setString(4, actorId == null ? null : actorId.toString());
                ps.setLong(5, now);
                ps.setString(6, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write clan history " + type + ": " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<ClanHistoryEntry>> get(UUID clanId, int page, int pageSize) {
        return get(clanId, HistoryFilter.ALL, page, pageSize);
    }

    @Override
    public CompletableFuture<List<ClanHistoryEntry>> get(UUID clanId, HistoryFilter filter, int page, int pageSize) {
        List<HistoryType> types = filter.isAll() ? List.of() : new ArrayList<>(filter.types());
        int size = Math.max(1, pageSize);
        int offset = Math.max(0, page) * size;
        return CompletableFuture.supplyAsync(() -> {
            List<ClanHistoryEntry> entries = new ArrayList<>();
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement(HistorySchema.pageSql(types.size()))) {
                int index = 1;
                ps.setString(index++, clanId.toString());
                for (HistoryType type : types) ps.setString(index++, type.name());
                ps.setInt(index++, size);
                ps.setInt(index, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) parse(rs).ifPresent(entries::add);
                }
            } catch (SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
            return entries;
        }, database.executor());
    }

    private static java.util.Optional<ClanHistoryEntry> parse(ResultSet rs) throws SQLException {
        try {
            String actor = rs.getString(4);
            String raw = rs.getString(6);
            Map<String, String> metadata = raw == null ? Map.of() : GSON.fromJson(raw, METADATA_TYPE);
            return java.util.Optional.of(new ClanHistoryEntry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                    HistoryType.valueOf(rs.getString(3)), actor == null ? null : UUID.fromString(actor), rs.getLong(5),
                    metadata == null ? new HashMap<>() : metadata));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty(); // a type from a newer version
        }
    }

    public void purgeClan(UUID clanId) {
        database.executor().execute(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement("DELETE FROM clan_history WHERE clan_id = ?")) {
                ps.setString(1, clanId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete history of clan " + clanId + ": " + e.getMessage(), e);
            }
        });
    }
}
