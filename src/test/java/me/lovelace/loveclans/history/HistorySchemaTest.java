package me.lovelace.loveclans.history;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistorySchemaTest {

    private static void insert(Connection c, UUID clan, HistoryType type, long ts) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(HistorySchema.INSERT)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, clan.toString());
            ps.setString(3, type.name());
            ps.setString(4, null);
            ps.setLong(5, ts);
            ps.setString(6, "{\"k\":\"v\"}");
            ps.executeUpdate();
        }
    }

    @Test
    void pagesAreNewestFirstAndFilteredByType() throws Exception {
        UUID clan = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(HistorySchema.CREATE_TABLE);
                HistorySchema.createIndexSql().forEach(sql -> {
                    try { st.executeUpdate(sql); } catch (Exception e) { fail(e); }
                });
            }
            insert(c, clan, HistoryType.WAR_WON, 100);
            insert(c, clan, HistoryType.MEMBER_JOINED, 200);
            insert(c, clan, HistoryType.WAR_LOST, 300);
            insert(c, UUID.randomUUID(), HistoryType.WAR_WON, 400); // another clan

            try (PreparedStatement ps = c.prepareStatement(HistorySchema.pageSql(0))) {
                ps.setString(1, clan.toString());
                ps.setInt(2, 2);
                ps.setInt(3, 0);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("WAR_LOST", rs.getString("type"));
                    assertTrue(rs.next());
                    assertEquals("MEMBER_JOINED", rs.getString("type"));
                    assertFalse(rs.next());
                }
            }
            try (PreparedStatement ps = c.prepareStatement(HistorySchema.pageSql(HistoryFilter.WARS.types().size()))) {
                int i = 1;
                ps.setString(i++, clan.toString());
                for (HistoryType t : HistoryFilter.WARS.types()) ps.setString(i++, t.name());
                ps.setInt(i++, 10);
                ps.setInt(i, 0);
                int rows = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows++;
                }
                assertEquals(2, rows);
            }
        }
    }

    @Test
    void filterCyclesThroughAllAndEveryTypeBelongsToAFilter() {
        HistoryFilter f = HistoryFilter.ALL;
        for (int i = 0; i < HistoryFilter.values().length; i++) f = f.next();
        assertEquals(HistoryFilter.ALL, f);
        for (HistoryType type : HistoryType.values()) {
            boolean covered = false;
            for (HistoryFilter filter : HistoryFilter.values()) {
                if (!filter.isAll() && filter.types().contains(type)) covered = true;
            }
            assertTrue(covered, type + " is in no filter");
        }
    }
}
