package me.lovelace.loveclans.storage;

/**
 * Schema and write statement of the clan contract tables (one row per clan: {@code clan_contracts} for the
 * weekly vow, {@code clan_monthly_contracts} for the monthly one). Kept in one place so that the production
 * code and the test that runs it against a real SQLite database cannot drift apart.
 *
 * There is deliberately no {@code last_reset} column: an older schema had it as NOT NULL without a default
 * while the insert never filled it, so on SQLite every save of a weekly vow failed with a constraint error.
 */
final class ContractSchema {

    private ContractSchema() {}

    static String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "clan_id VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "contract_id VARCHAR(64) NOT NULL, "
                + "progress INT NOT NULL DEFAULT 0, "
                + "completed INT NOT NULL DEFAULT 0, "
                + "claimed INT NOT NULL DEFAULT 0, "
                + "target INT NOT NULL DEFAULT 0, "
                + "reward_xp BIGINT NOT NULL DEFAULT 0, "
                + "started_at BIGINT NOT NULL DEFAULT 0, "
                + "expires_at BIGINT NOT NULL DEFAULT 0, "
                + "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE)";
    }

    /** Parameters: clan_id, contract_id, progress, completed, claimed, target, reward_xp, started_at, expires_at. */
    static String upsertSql(String table, boolean mysql) {
        String columns = "INSERT INTO " + table
                + " (clan_id, contract_id, progress, completed, claimed, target, reward_xp, started_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ";
        return mysql
                ? columns + "ON DUPLICATE KEY UPDATE contract_id = VALUES(contract_id), "
                  + "progress = VALUES(progress), completed = VALUES(completed), claimed = VALUES(claimed), "
                  + "target = VALUES(target), reward_xp = VALUES(reward_xp), started_at = VALUES(started_at), expires_at = VALUES(expires_at)"
                : columns + "ON CONFLICT(clan_id) DO UPDATE SET contract_id = excluded.contract_id, "
                  + "progress = excluded.progress, completed = excluded.completed, claimed = excluded.claimed, "
                  + "target = excluded.target, reward_xp = excluded.reward_xp, started_at = excluded.started_at, expires_at = excluded.expires_at";
    }
}
