package me.lovelace.loveclans.activity;

/**
 * Tables of the activity system. One row per (clan, player, period, category) holding the running total: the
 * history of single actions is never stored. Kept as plain SQL strings so a test can run exactly what production runs.
 */
public final class ActivitySchema {
    private ActivitySchema() {
    }

    public static final String CREATE_ACTIVITY = "CREATE TABLE IF NOT EXISTS clan_activity ("
            + "clan_id VARCHAR(36) NOT NULL, "
            + "player_id VARCHAR(36) NOT NULL, "
            + "period_key VARCHAR(16) NOT NULL, "
            + "category VARCHAR(16) NOT NULL, "
            + "points BIGINT NOT NULL DEFAULT 0, "
            + "PRIMARY KEY (clan_id, player_id, period_key, category))";

    /** Ids of awards that were already paid, so a repeated event (or a restart in the middle) cannot pay twice. */
    public static final String CREATE_PROCESSED = "CREATE TABLE IF NOT EXISTS clan_activity_processed ("
            + "event_key VARCHAR(100) NOT NULL PRIMARY KEY, "
            + "created_at BIGINT NOT NULL)";

    /** Parameters: clan_id, player_id, period_key, category, points (absolute value). */
    public static String upsertSql(boolean mysql) {
        String insert = "INSERT INTO clan_activity (clan_id, player_id, period_key, category, points) VALUES (?, ?, ?, ?, ?) ";
        return mysql
                ? insert + "ON DUPLICATE KEY UPDATE points = VALUES(points)"
                : insert + "ON CONFLICT(clan_id, player_id, period_key, category) DO UPDATE SET points = excluded.points";
    }

    /** Parameters: event_key, created_at. */
    public static String insertProcessedSql(boolean mysql) {
        return (mysql ? "INSERT IGNORE" : "INSERT OR IGNORE") + " INTO clan_activity_processed (event_key, created_at) VALUES (?, ?)";
    }

    public static final String LOAD_PERIODS = "SELECT clan_id, player_id, period_key, category, points FROM clan_activity "
            + "WHERE period_key IN ('L', ?, ?)";
    public static final String LOAD_PROCESSED = "SELECT event_key FROM clan_activity_processed WHERE created_at >= ?";
    public static final String PRUNE_PROCESSED = "DELETE FROM clan_activity_processed WHERE created_at < ?";
}
