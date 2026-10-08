package me.lovelace.loveclans.history;

import java.util.Collection;
import java.util.List;

/** SQL of the {@code clan_history} table, kept as strings so a test can run the same statements on SQLite. */
public final class HistorySchema {
    private HistorySchema() {
    }

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS clan_history ("
            + "id VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "clan_id VARCHAR(36) NOT NULL, "
            + "type VARCHAR(64) NOT NULL, "
            + "actor_id VARCHAR(36), "
            + "timestamp BIGINT NOT NULL, "
            + "metadata TEXT)";

    /** MySQL has no IF NOT EXISTS for indexes: the caller runs each statement and ignores "already exists". */
    public static List<String> createIndexSql() {
        return List.of(
                "CREATE INDEX idx_clan_history_clan_time ON clan_history (clan_id, timestamp)",
                "CREATE INDEX idx_clan_history_type ON clan_history (type)");
    }

    public static final String INSERT = "INSERT INTO clan_history (id, clan_id, type, actor_id, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?)";

    /** Parameters: clan_id, [types...], limit, offset. A page is read with LIMIT/OFFSET, never the whole history. */
    public static String pageSql(int typeCount) {
        String filter = typeCount > 0 ? " AND type IN (" + String.join(",", java.util.Collections.nCopies(typeCount, "?")) + ")" : "";
        return "SELECT id, clan_id, type, actor_id, timestamp, metadata FROM clan_history WHERE clan_id = ?"
                + filter + " ORDER BY timestamp DESC, id DESC LIMIT ? OFFSET ?";
    }

    public static String countSql(Collection<?> types) {
        return "SELECT COUNT(*) FROM clan_history WHERE clan_id = ?"
                + (types.isEmpty() ? "" : " AND type IN (" + String.join(",", java.util.Collections.nCopies(types.size(), "?")) + ")");
    }
}
