package me.lovelace.loveclans.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * One-time rescale of stored money when LoveCore's {@code economy.scale-version} grows.
 *
 * <p>Covers the clan treasury ({@code clans.chest_money}), the money escrowed in pending clan trades
 * and undelivered trade deliveries. The version the data was written under lives in
 * {@code economy_meta}; the rescale and the version write share one transaction, so a crash cannot
 * apply it twice. Written with plain select/update-by-key so it runs unchanged on SQLite and MySQL.</p>
 */
public final class EconomyMigration {

    private static final String META_KEY = "scale_version";

    private EconomyMigration() {
    }

    private record Target(String table, String keyColumn, String moneyColumn, String where) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target("clans", "id", "chest_money", null),
            new Target("clan_trades", "id", "money", "status = 'PENDING'"),
            new Target("clan_trade_deliveries", "id", "money", "money_delivered = 0"));

    /** True when the stored version is lower than the target (or absent) and there is money to rescale. */
    public static boolean needsRescale(Connection conn, int targetVersion) throws SQLException {
        ensureMeta(conn);
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) return false;
        for (Target t : TARGETS) {
            if (!tableExists(conn, t.table())) continue;
            String sql = "SELECT 1 FROM " + t.table() + " WHERE " + t.moneyColumn() + " > 0"
                    + (t.where() != null ? " AND " + t.where() : "") + " LIMIT 1";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    /** @return number of rows rescaled. */
    public static int migrate(Connection conn, int targetVersion, double factor, Logger log) throws SQLException {
        ensureMeta(conn);
        Integer stored = readVersion(conn);
        if (stored != null && stored >= targetVersion) return 0;

        boolean wasAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int from = stored != null ? stored : 1;
            int rows = 0;
            if (from < targetVersion && factor > 0 && factor != 1.0) {
                double mult = Math.pow(factor, targetVersion - from);
                for (Target t : TARGETS) {
                    rows += rescale(conn, t, mult);
                }
            }
            writeVersion(conn, targetVersion);
            conn.commit();
            if (rows > 0) {
                log.info("Economy migration v" + from + " -> v" + targetVersion + ": rescaled " + rows
                        + " rows (x" + factor + " per step)");
            }
            return rows;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(wasAuto);
        }
    }

    private static int rescale(Connection conn, Target t, double mult) throws SQLException {
        if (!tableExists(conn, t.table())) return 0;
        List<String> keys = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        String select = "SELECT " + t.keyColumn() + ", " + t.moneyColumn() + " FROM " + t.table()
                + " WHERE " + t.moneyColumn() + " > 0" + (t.where() != null ? " AND " + t.where() : "");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                keys.add(rs.getString(1));
                values.add(rs.getLong(2));
            }
        }
        String update = "UPDATE " + t.table() + " SET " + t.moneyColumn() + " = ? WHERE " + t.keyColumn() + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            for (int i = 0; i < keys.size(); i++) {
                ps.setLong(1, Math.max(1L, Math.round(values.get(i) * mult)));
                ps.setString(2, keys.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return keys.size();
    }

    private static void ensureMeta(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS economy_meta ("
                    + "meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(64) NOT NULL)");
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static Integer readVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT meta_value FROM economy_meta WHERE meta_key = ?")) {
            ps.setString(1, META_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                try {
                    return Integer.parseInt(rs.getString(1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    private static void writeVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM economy_meta WHERE meta_key = ?")) {
            del.setString(1, META_KEY);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO economy_meta (meta_key, meta_value) VALUES (?, ?)")) {
            ins.setString(1, META_KEY);
            ins.setString(2, String.valueOf(version));
            ins.executeUpdate();
        }
    }
}
