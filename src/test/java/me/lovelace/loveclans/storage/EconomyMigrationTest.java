package me.lovelace.loveclans.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EconomyMigrationTest {

    private static final Logger LOG = Logger.getLogger("test");
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE clans (id VARCHAR(36) PRIMARY KEY, chest_money BIGINT NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE clan_trades (id VARCHAR(36) PRIMARY KEY, money BIGINT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL)");
            st.execute("CREATE TABLE clan_trade_deliveries (id VARCHAR(36) PRIMARY KEY, money BIGINT NOT NULL DEFAULT 0, money_delivered TINYINT NOT NULL DEFAULT 0)");
            st.execute("INSERT INTO clans VALUES ('a', 1000), ('b', 0), ('c', 7)");
            st.execute("INSERT INTO clan_trades VALUES ('t1', 400, 'PENDING'), ('t2', 400, 'ACCEPTED'), ('t3', 100, 'CANCELLED')");
            st.execute("INSERT INTO clan_trade_deliveries VALUES ('d1', 200, 0), ('d2', 200, 1)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private long money(String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void rescalesTreasuryAndEscrowedMoneyOnly() throws Exception {
        assertTrue(EconomyMigration.needsRescale(conn, 2));
        assertEquals(2 + 1 + 1, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        assertEquals(5000, money("SELECT chest_money FROM clans WHERE id='a'"));
        assertEquals(0, money("SELECT chest_money FROM clans WHERE id='b'"));
        assertEquals(35, money("SELECT chest_money FROM clans WHERE id='c'"));
        assertEquals(2000, money("SELECT money FROM clan_trades WHERE id='t1'"));
        assertEquals(400, money("SELECT money FROM clan_trades WHERE id='t2'"));
        assertEquals(100, money("SELECT money FROM clan_trades WHERE id='t3'"));
        assertEquals(1000, money("SELECT money FROM clan_trade_deliveries WHERE id='d1'"));
        assertEquals(200, money("SELECT money FROM clan_trade_deliveries WHERE id='d2'"));
    }

    @Test
    void secondRunDoesNothing() throws Exception {
        EconomyMigration.migrate(conn, 2, 5.0, LOG);
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        assertEquals(5000, money("SELECT chest_money FROM clans WHERE id='a'"));
    }

    @Test
    void emptyDatabaseJustRecordsTheVersion() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM clans");
            st.execute("DELETE FROM clan_trades");
            st.execute("DELETE FROM clan_trade_deliveries");
        }
        assertFalse(EconomyMigration.needsRescale(conn, 2));
        assertEquals(0, EconomyMigration.migrate(conn, 2, 5.0, LOG));
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO clans VALUES ('z', 10)");
        }
        assertEquals(1, EconomyMigration.migrate(conn, 3, 5.0, LOG));
        assertEquals(50, money("SELECT chest_money FROM clans WHERE id='z'"));
    }

    @Test
    void missingOptionalTablesAreSkipped() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE clan_trades");
            st.execute("DROP TABLE clan_trade_deliveries");
        }
        assertEquals(2, EconomyMigration.migrate(conn, 2, 5.0, LOG));
    }
}
