package me.lovelace.loveclans.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real contract DDL and write statement against a real SQLite database. */
class ContractSchemaTest {

    private static final String CLAN = "clan-1";
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE clans (id VARCHAR(36) PRIMARY KEY)");
            st.execute("INSERT INTO clans VALUES ('" + CLAN + "')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private void upsert(String table, String contractId, int progress, int completed, int claimed) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(ContractSchema.upsertSql(table, false))) {
            ps.setString(1, CLAN);
            ps.setString(2, contractId);
            ps.setInt(3, progress);
            ps.setInt(4, completed);
            ps.setInt(5, claimed);
            ps.setInt(6, 100);
            ps.setLong(7, 7000L);
            ps.setLong(8, 1L);
            ps.setLong(9, 2L);
            ps.executeUpdate();
        }
    }

    private String read(String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM " + table + " WHERE clan_id = '" + CLAN + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void weeklyAndMonthlyTablesAcceptAnInsertAndThenUpdateTheSameRow() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(ContractSchema.createTableSql("clan_contracts"));
            st.execute(ContractSchema.createTableSql("clan_monthly_contracts"));
        }
        for (String table : new String[]{"clan_contracts", "clan_monthly_contracts"}) {
            upsert(table, "vow_a", 0, 0, 0);
            upsert(table, "vow_a", 42, 0, 0);
            assertEquals("42", read(table, "progress"), table);
            upsert(table, "vow_a", 100, 1, 1);
            assertEquals("1", read(table, "claimed"), table);
            assertEquals("7000", read(table, "reward_xp"), table);
        }
    }

    @Test
    void theLegacyLastResetColumnBlockedEverySaveAndDroppingItFixesIt() throws Exception {
        try (Statement st = conn.createStatement()) {
            // the schema older versions created: last_reset NOT NULL without a default, never filled by the insert
            st.execute("CREATE TABLE clan_contracts (clan_id VARCHAR(36) NOT NULL PRIMARY KEY, contract_id VARCHAR(64) NOT NULL, "
                    + "progress INT NOT NULL DEFAULT 0, completed INT NOT NULL DEFAULT 0, claimed INT NOT NULL DEFAULT 0, "
                    + "last_reset BIGINT NOT NULL, FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE)");
            st.execute("ALTER TABLE clan_contracts ADD COLUMN target INT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE clan_contracts ADD COLUMN reward_xp BIGINT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE clan_contracts ADD COLUMN started_at BIGINT NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE clan_contracts ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0");
        }
        SQLException failure = assertThrows(SQLException.class, () -> upsert("clan_contracts", "vow_a", 0, 0, 0));
        assertTrue(failure.getMessage().contains("last_reset"), failure.getMessage());

        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE clan_contracts DROP COLUMN last_reset");
        }
        upsert("clan_contracts", "vow_a", 5, 0, 0);
        assertEquals("5", read("clan_contracts", "progress"));
    }

    @Test
    void deletingAClanRemovesItsContractRows() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(ContractSchema.createTableSql("clan_contracts"));
        }
        upsert("clan_contracts", "vow_a", 1, 0, 0);
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM clans WHERE id = '" + CLAN + "'");
        }
        assertEquals(null, read("clan_contracts", "progress"));
    }
}
