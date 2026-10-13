package me.lovelace.loveclans.storage;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeStoreTest {

    private static Connection db() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE clans (id VARCHAR(36) PRIMARY KEY, upgrade_points INT DEFAULT 0, "
                    + "perk VARCHAR(32), perk_chosen_at BIGINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE clan_upgrades (clan_id VARCHAR(36), upgrade_name VARCHAR(64), "
                    + "upgrade_level INT, PRIMARY KEY (clan_id, upgrade_name))");
            UpgradeStore.createTables(st);
        }
        return c;
    }

    @Test
    void secondHandlingOfTheSameOfferChangesNothing() throws Exception {
        try (Connection c = db()) {
            try (PreparedStatement ps = c.prepareStatement(UpgradeStore.INSERT_OFFER)) {
                ps.setString(1, "o1");
                ps.setString(2, "c1");
                ps.setString(3, "MEMBERS");
                ps.setString(4, "TAX");
                ps.setString(5, "NORMAL");
                ps.setString(6, "PENDING");
                ps.setString(7, null);
                ps.setLong(8, 1L);
                ps.executeUpdate();
            }
            assertEquals(1, handle(c, "o1", "CHOSEN", "MEMBERS"));
            assertEquals(0, handle(c, "o1", "CHOSEN", "TAX"), "an already handled offer must not be rewritten");
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT chosen FROM clan_upgrade_offers")) {
                assertTrue(rs.next());
                assertEquals("MEMBERS", rs.getString(1));
            }
        }
    }

    private static int handle(Connection c, String id, String status, String chosen) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(UpgradeStore.MARK_HANDLED)) {
            ps.setString(1, status);
            ps.setString(2, chosen);
            ps.setString(3, id);
            return ps.executeUpdate();
        }
    }

    @Test
    void rewardIsStoredOnce() throws Exception {
        try (Connection c = db()) {
            for (int i = 0; i < 3; i++) {
                try (PreparedStatement ps = c.prepareStatement(UpgradeStore.insertRewardSql(false))) {
                    ps.setString(1, "c1");
                    ps.setString(2, "PIONEER");
                    ps.setLong(3, i);
                    ps.executeUpdate();
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*), MIN(unlocked_at) FROM clan_guild_rewards")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(0, rs.getLong(2), "the first unlock time wins");
            }
        }
    }

    @Test
    void legacyMigrationRefundsTerritoriesOnceAndResetsOldPerks() throws Exception {
        try (Connection c = db(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO clans VALUES ('a', 1, 'WARRIOR', 99), ('b', 0, 'FARMING', 5)");
            st.executeUpdate("INSERT INTO clan_upgrades VALUES ('a', 'TERRITORIES', 3), ('a', 'MEMBERS', 2)");
            UpgradeStore.runLegacyMigration(st);
            UpgradeStore.runLegacyMigration(st); // restart: must not refund twice
            try (ResultSet rs = st.executeQuery("SELECT id, upgrade_points, perk, perk_chosen_at FROM clans ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt("upgrade_points"));
                assertEquals(null, rs.getString("perk"));
                assertEquals(0, rs.getLong("perk_chosen_at"));
                assertTrue(rs.next());
                assertEquals("FARMING", rs.getString("perk"), "a new-style direction must survive");
                assertFalse(rs.next());
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clan_upgrades WHERE upgrade_name = 'TERRITORIES'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
