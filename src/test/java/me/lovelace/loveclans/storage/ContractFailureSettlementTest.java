package me.lovelace.loveclans.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class ContractFailureSettlementTest {
    private Connection database() throws SQLException {
        Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var sql = db.createStatement()) {
            sql.execute(
                    "CREATE TABLE clans (id TEXT PRIMARY KEY, level INT, experience BIGINT,"
                        + " upgrade_points INT, spirit_level INT)");
            sql.execute("INSERT INTO clans VALUES ('clan', 3, 1000, 5, 2)");
            sql.execute(ContractSchema.createTableSql("clan_contracts"));
            sql.execute(
                    "INSERT INTO clan_contracts (clan_id, contract_id, started_at, penalty_percent)"
                        + " VALUES ('clan','vow',10,150)");
        }
        db.setAutoCommit(false);
        return db;
    }

    private long scalar(Connection db, String query) throws SQLException {
        try (var sql = db.createStatement();
                var rs = sql.executeQuery(query)) {
            return rs.getLong(1);
        }
    }

    @Test
    void settlementIsAtomicAndCannotChargeTwiceOrRemoveANewerVow() throws Exception {
        try (var db = database()) {
            assertTrue(
                    ContractFailureSettlement.apply(
                            db, "clan_contracts", "clan", "vow", 10, 3, 250, 5, 2));
            db.commit();
            assertEquals(250, scalar(db, "SELECT experience FROM clans"));
            assertFalse(
                    ContractFailureSettlement.apply(
                            db, "clan_contracts", "clan", "vow", 10, 3, 0, 5, 2));
            assertEquals(250, scalar(db, "SELECT experience FROM clans"));
            try (var sql = db.createStatement()) {
                sql.execute(
                        "INSERT INTO clan_contracts (clan_id,contract_id,started_at) VALUES"
                            + " ('clan','new',20)");
            }
            assertFalse(
                    ContractFailureSettlement.apply(
                            db, "clan_contracts", "clan", "vow", 10, 3, 0, 5, 2));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM clan_contracts"));
        }
    }

    @Test
    void failedProgressionWriteRollsBackDeletionAndKeepsVowForRetry() throws Exception {
        try (var db = database()) {
            try (var sql = db.createStatement()) {
                sql.execute(
                        "CREATE TRIGGER reject_penalty BEFORE UPDATE ON clans BEGIN SELECT"
                            + " RAISE(FAIL,'disk simulation'); END");
            }
            db.commit();
            assertThrows(
                    SQLException.class,
                    () ->
                            ContractFailureSettlement.apply(
                                    db, "clan_contracts", "clan", "vow", 10, 3, 250, 5, 2));
            db.rollback();
            assertEquals(1000, scalar(db, "SELECT experience FROM clans"));
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM clan_contracts"));
        }
    }
}
