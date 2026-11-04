package me.lovelace.loveclans.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Caller owns the transaction: both the exact vow and progression change together, or neither. */
final class ContractFailureSettlement {
    private ContractFailureSettlement() {}

    static boolean apply(
            Connection connection,
            String table,
            String clanId,
            String questId,
            long startedAt,
            int level,
            long experience,
            int upgradePoints,
            int spiritLevel)
            throws SQLException {
        if (!table.equals("clan_contracts") && !table.equals("clan_monthly_contracts")) {
            throw new IllegalArgumentException("Unknown vow table");
        }
        try (PreparedStatement delete =
                connection.prepareStatement(
                        "DELETE FROM "
                                + table
                                + " WHERE clan_id = ? AND contract_id = ? AND started_at = ? AND"
                                + " completed = 0")) {
            delete.setString(1, clanId);
            delete.setString(2, questId);
            delete.setLong(3, startedAt);
            if (delete.executeUpdate() != 1) return false;
        }
        try (PreparedStatement update =
                connection.prepareStatement(
                        "UPDATE clans SET level = ?, experience = ?, upgrade_points = ?,"
                            + " spirit_level = ? WHERE id = ?")) {
            update.setInt(1, level);
            update.setLong(2, experience);
            update.setInt(3, upgradePoints);
            update.setInt(4, spiritLevel);
            update.setString(5, clanId);
            if (update.executeUpdate() != 1)
                throw new SQLException("Clan disappeared while settling vow");
        }
        return true;
    }
}
