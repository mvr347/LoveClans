package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import org.bukkit.Bukkit; // Import Bukkit for World access
import org.bukkit.Location; // Import Location
import org.bukkit.Material;
import org.bukkit.World; // Import World

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class SqlClanStorage implements ClanStorage {
    private final DatabaseManager database;

    public SqlClanStorage(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Collection<Clan>> loadAllClansAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                Map<UUID, Clan> clans = new LinkedHashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID id = UUID.fromString(result.getString("id"));
                        Material emblem = Material.matchMaterial(result.getString("emblem_material"));
                        if (emblem == null) {
                            emblem = Material.WHITE_BANNER;
                        }
                        long onlineTimeWeekly = 0L;
                        long lastDecayCheck = System.currentTimeMillis();
                        try {
                            onlineTimeWeekly = result.getLong("spirit_online_time");
                            lastDecayCheck = result.getLong("spirit_last_decay");
                        } catch (SQLException ignored) {}
                        me.lovelace.loveclans.model.spirit.SpiritAbility spiritAbility = null;
                        try {
                            String rawAbility = result.getString("spirit_ability");
                            if (rawAbility != null) {
                                spiritAbility = me.lovelace.loveclans.model.spirit.SpiritAbility.valueOf(rawAbility);
                            }
                        } catch (SQLException | IllegalArgumentException ignored) {}
                        long abilityChosenAt = 0L;
                        try {
                            abilityChosenAt = result.getLong("spirit_ability_chosen_at");
                        } catch (SQLException ignored) {}
                        ClanSpirit spirit = new ClanSpirit(
                                result.getInt("spirit_level"),
                                result.getLong("spirit_energy"),
                                result.getLong("spirit_awakened_until"),
                                onlineTimeWeekly,
                                lastDecayCheck,
                                spiritAbility,
                                abilityChosenAt
                        );
                        boolean isOpen = result.getInt("is_open") == 1;
                        int upgradePoints = 0;
                        try {
                            upgradePoints = result.getInt("upgrade_points");
                        } catch (SQLException ignored) {}

                        // Load home_location
                        Location homeLocation = null;
                        try {
                            String serializedHomeLocation = result.getString("home_location");
                            homeLocation = deserializeLocation(serializedHomeLocation);
                        } catch (SQLException ignored) {
                            // Column might not exist yet
                        }

                        Clan clan = new Clan(
                                id,
                                result.getString("name"),
                                result.getString("tag"),
                                result.getString("tag_color"),
                                result.getString("description"),
                                emblem,
                                result.getInt("level"),
                                result.getLong("experience"),
                                upgradePoints,
                                result.getInt("chest_rows"),
                                spirit,
                                result.getLong("created_at"),
                                isOpen,
                                homeLocation // Pass homeLocation to constructor
                        );
                        try {
                            clan.setWarsWon(result.getInt("wars_won"));
                            clan.setWarsLost(result.getInt("wars_lost"));
                            clan.setSiegesWon(result.getInt("sieges_won"));
                            clan.setSiegesLost(result.getInt("sieges_lost"));
                            clan.setRaidsWon(result.getInt("raids_won"));
                            clan.setRaidsLost(result.getInt("raids_lost"));
                            clan.setInfluence(result.getLong("influence"));
                        } catch (SQLException ignored) {
                            // Columns might not exist yet if plugin just updated
                        }
                        clans.put(id, clan);
                    }
                }
                loadMembers(connection, clans);
                loadTerritories(connection, clans);
                loadDiplomacy(connection, clans);
                loadUpgrades(connection, clans);
                loadPermissions(connection, clans);
                loadBank(connection, clans);
                return new ArrayList<>(clans.values());
            } catch (SQLException exception) {
                throw new StorageException("Unable to load clans", exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveClanAsync(Clan clan) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                connection.setAutoCommit(false);
                saveClan(connection, clan);
                for (ClanMember member : clan.members().values()) {
                    saveMember(connection, clan.id(), member);
                }
                for (ClanTerritory territory : clan.territories()) {
                    saveTerritory(connection, territory);
                }
                for (Map.Entry<UUID, DiplomacyRelation> entry : clan.diplomacy().entrySet()) {
                    saveDiplomacy(connection, clan.id(), entry.getKey(), entry.getValue());
                }
                for (Map.Entry<ClanUpgrade, Integer> entry : clan.upgrades().entrySet()) {
                    saveUpgrade(connection, clan.id(), entry.getKey(), entry.getValue());
                }
                savePermissions(connection, clan);
                connection.commit();
            } catch (SQLException exception) {
                exception.printStackTrace();
                throw new StorageException("Unable to save clan " + clan.id(), exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> deleteClanAsync(UUID clanId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                statement.setString(1, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to delete clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveMemberAsync(UUID clanId, ClanMember member) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                saveMember(connection, clanId, member);
            } catch (SQLException exception) {
                throw new StorageException("Unable to save member " + member.playerId(), exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> deleteMemberAsync(UUID clanId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_members WHERE clan_id = ? AND player_id = ?")) {
                statement.setString(1, clanId.toString());
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to delete member " + playerId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveTerritoryAsync(ClanTerritory territory) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                saveTerritory(connection, territory);
            } catch (SQLException exception) {
                throw new StorageException("Unable to save territory " + territory.id(), exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> deleteTerritoryAsync(UUID territoryId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_territories WHERE id = ?")) {
                statement.setString(1, territoryId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to delete territory " + territoryId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveDiplomacyAsync(UUID sourceClanId, UUID targetClanId, DiplomacyRelation relation) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                if (relation == DiplomacyRelation.NEUTRAL) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_diplomacy WHERE source_clan_id = ? AND target_clan_id = ?")) {
                        statement.setString(1, sourceClanId.toString());
                        statement.setString(2, targetClanId.toString());
                        statement.executeUpdate();
                    }
                    return;
                }
                saveDiplomacy(connection, sourceClanId, targetClanId, relation);
            } catch (SQLException exception) {
                throw new StorageException("Unable to save diplomacy", exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveUpgradeAsync(UUID clanId, ClanUpgrade upgrade, int level) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                saveUpgrade(connection, clanId, upgrade, level);
            } catch (SQLException exception) {
                throw new StorageException("Unable to save upgrade", exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Collection<ClanApplication>> loadAllApplicationsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Collection<ClanApplication> applications = new ArrayList<>();
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_applications");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    applications.add(new ClanApplication(
                            UUID.fromString(result.getString("clan_id")),
                            UUID.fromString(result.getString("applicant_id")),
                            result.getLong("applied_at")
                    ));
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to load clan applications", exception);
            }
            return applications;
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> saveApplicationAsync(ClanApplication application) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                String sql = upsertSql("clan_applications",
                        new String[]{"clan_id", "applicant_id"},
                        new String[]{"applied_at", "clan_id", "applicant_id"});
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, application.appliedAt());
                    statement.setString(2, application.clanId().toString());
                    statement.setString(3, application.applicantId().toString());
                    statement.executeUpdate();
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to save clan application", exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> deleteApplicationAsync(UUID clanId, UUID applicantId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_applications WHERE clan_id = ? AND applicant_id = ?")) {
                statement.setString(1, clanId.toString());
                statement.setString(2, applicantId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to delete clan application", exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> deleteAllApplicationsForClanAsync(UUID clanId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_applications WHERE clan_id = ?")) {
                statement.setString(1, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to delete all applications for clan " + clanId, exception);
            }
        }, database.executor());
    }

    // --- Lightweight, single-field clan updates ---

    @Override
    public CompletableFuture<Void> updateClanName(UUID clanId, String name) {
        return updateClanColumn(clanId, "name", name);
    }

    @Override
    public CompletableFuture<Void> updateClanTag(UUID clanId, String tag) {
        return updateClanColumn(clanId, "tag", tag);
    }

    @Override
    public CompletableFuture<Void> updateClanTagColor(UUID clanId, String tagColor) {
        return updateClanColumn(clanId, "tag_color", tagColor);
    }

    @Override
    public CompletableFuture<Void> updateClanEmblem(UUID clanId, Material emblem) {
        return updateClanColumn(clanId, "emblem_material", emblem.name());
    }

    @Override
    public CompletableFuture<Void> updateClanOpenStatus(UUID clanId, boolean open) {
        return updateClanColumn(clanId, "is_open", open ? 1 : 0);
    }

    @Override
    public CompletableFuture<Void> updateClanUpgradePoints(UUID clanId, int upgradePoints) {
        return updateClanColumn(clanId, "upgrade_points", upgradePoints);
    }

    @Override
    public CompletableFuture<Void> updateClanHomeLocation(UUID clanId, Location homeLocation) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE clans SET home_location = ? WHERE id = ?")) {
                String serialized = serializeLocation(homeLocation);
                if (serialized == null) {
                    statement.setNull(1, Types.VARCHAR);
                } else {
                    statement.setString(1, serialized);
                }
                statement.setString(2, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update home location for clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> updateClanSpiritAbility(UUID clanId, SpiritAbility ability, long chosenAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET spirit_ability = ?, spirit_ability_chosen_at = ? WHERE id = ?")) {
                if (ability == null) {
                    statement.setNull(1, Types.VARCHAR);
                } else {
                    statement.setString(1, ability.name());
                }
                statement.setLong(2, chosenAt);
                statement.setString(3, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update spirit ability for clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> updateClanProgression(UUID clanId, int level, long experience, int upgradePoints, int spiritLevel) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET level = ?, experience = ?, upgrade_points = ?, spirit_level = ? WHERE id = ?")) {
                statement.setInt(1, level);
                statement.setLong(2, experience);
                statement.setInt(3, upgradePoints);
                statement.setInt(4, spiritLevel);
                statement.setString(5, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update progression for clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Void> updateClanInfluenceStats(UUID clanId, int warsWon, int warsLost, int siegesWon,
                                                              int siegesLost, int raidsWon, int raidsLost, long influence) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clans SET wars_won = ?, wars_lost = ?, sieges_won = ?, sieges_lost = ?, " +
                                 "raids_won = ?, raids_lost = ?, influence = ? WHERE id = ?")) {
                statement.setInt(1, warsWon);
                statement.setInt(2, warsLost);
                statement.setInt(3, siegesWon);
                statement.setInt(4, siegesLost);
                statement.setInt(5, raidsWon);
                statement.setInt(6, raidsLost);
                statement.setLong(7, influence);
                statement.setString(8, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update influence stats for clan " + clanId, exception);
            }
        }, database.executor());
    }

    private CompletableFuture<Void> updateClanColumn(UUID clanId, String column, Object value) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE clans SET " + column + " = ? WHERE id = ?")) {
                statement.setObject(1, value);
                statement.setString(2, clanId.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new StorageException("Unable to update " + column + " for clan " + clanId, exception);
            }
        }, database.executor());
    }

    // --- Clan bank / treasury ---

    @Override
    public CompletableFuture<Long> adjustBankAmountAsync(UUID clanId, String itemId, long delta) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                String sql = database.type() == DatabaseType.MYSQL
                        ? "INSERT INTO clan_bank (clan_id, item_id, amount) VALUES (?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
                        : "INSERT INTO clan_bank (clan_id, item_id, amount) VALUES (?, ?, ?) " +
                          "ON CONFLICT(clan_id, item_id) DO UPDATE SET amount = clan_bank.amount + excluded.amount";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, clanId.toString());
                    statement.setString(2, itemId);
                    statement.setLong(3, delta);
                    statement.executeUpdate();
                }
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT amount FROM clan_bank WHERE clan_id = ? AND item_id = ?")) {
                    select.setString(1, clanId.toString());
                    select.setString(2, itemId);
                    try (ResultSet result = select.executeQuery()) {
                        return result.next() ? result.getLong("amount") : 0L;
                    }
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to adjust bank amount for clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Boolean> withdrawBankAmountAsync(UUID clanId, String itemId, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE clan_bank SET amount = amount - ? WHERE clan_id = ? AND item_id = ? AND amount >= ?")) {
                statement.setLong(1, amount);
                statement.setString(2, clanId.toString());
                statement.setString(3, itemId);
                statement.setLong(4, amount);
                // Conditioning on "amount >= ?" makes this a single atomic statement: concurrent
                // withdrawals on the same clan/item can't both succeed and drive the balance negative.
                return statement.executeUpdate() > 0;
            } catch (SQLException exception) {
                throw new StorageException("Unable to withdraw bank amount for clan " + clanId, exception);
            }
        }, database.executor());
    }

    // --- Clan chest (physical item storage) ---

    @Override
    public CompletableFuture<Void> updateClanChestRows(UUID clanId, int chestRows) {
        return updateClanColumn(clanId, "chest_rows", chestRows);
    }

    @Override
    public CompletableFuture<Void> saveChestContentsAsync(UUID clanId, byte[] contents) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                String sql = database.type() == DatabaseType.MYSQL
                        ? "INSERT INTO clan_chest (clan_id, contents) VALUES (?, ?) " +
                          "ON DUPLICATE KEY UPDATE contents = VALUES(contents)"
                        : "INSERT INTO clan_chest (clan_id, contents) VALUES (?, ?) " +
                          "ON CONFLICT(clan_id) DO UPDATE SET contents = excluded.contents";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, clanId.toString());
                    statement.setBytes(2, contents);
                    statement.executeUpdate();
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to save chest contents for clan " + clanId, exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<byte[]> loadChestContentsAsync(UUID clanId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT contents FROM clan_chest WHERE clan_id = ?")) {
                statement.setString(1, clanId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getBytes("contents") : null;
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to load chest contents for clan " + clanId, exception);
            }
        }, database.executor());
    }

    // --- Clan contracts (weekly quests) ---

    @Override
    public CompletableFuture<Void> saveContractProgressAsync(ClanQuestProgress progress) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.dataSource().getConnection()) {
                String sql = database.type() == DatabaseType.MYSQL
                        ? "INSERT INTO clan_contracts (clan_id, contract_id, progress, completed, claimed, last_reset) " +
                          "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE contract_id = VALUES(contract_id), " +
                          "progress = VALUES(progress), completed = VALUES(completed), claimed = VALUES(claimed), last_reset = VALUES(last_reset)"
                        : "INSERT INTO clan_contracts (clan_id, contract_id, progress, completed, claimed, last_reset) " +
                          "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(clan_id) DO UPDATE SET contract_id = excluded.contract_id, " +
                          "progress = excluded.progress, completed = excluded.completed, claimed = excluded.claimed, last_reset = excluded.last_reset";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, progress.clanId().toString());
                    statement.setString(2, progress.questId());
                    statement.setInt(3, progress.objectiveProgress().getOrDefault(0, 0));
                    statement.setInt(4, progress.completed() ? 1 : 0);
                    statement.setInt(5, progress.claimed() ? 1 : 0);
                    statement.setLong(6, progress.lastReset());
                    statement.executeUpdate();
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to save contract progress for clan " + progress.clanId(), exception);
            }
        }, database.executor());
    }

    @Override
    public CompletableFuture<Collection<ClanQuestProgress>> loadAllContractsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<ClanQuestProgress> result = new ArrayList<>();
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_contracts");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<Integer, Integer> progressMap = new LinkedHashMap<>();
                    progressMap.put(0, rs.getInt("progress"));
                    result.add(new ClanQuestProgress(
                            UUID.fromString(rs.getString("clan_id")),
                            rs.getString("contract_id"),
                            progressMap,
                            rs.getInt("completed") != 0,
                            rs.getInt("claimed") != 0,
                            rs.getLong("last_reset")
                    ));
                }
            } catch (SQLException exception) {
                throw new StorageException("Unable to load clan contracts", exception);
            }
            return result;
        }, database.executor());
    }

    private void loadBank(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_bank");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("clan_id")));
                if (clan == null) {
                    continue;
                }
                clan.putBankAmount(result.getString("item_id"), result.getLong("amount"));
            }
        } catch (SQLException ignored) {
            // Table might not exist yet if plugin just updated
        }
    }

    private void loadMembers(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_members");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("clan_id")));
                if (clan == null) {
                    continue;
                }
                ClanRank rank = ClanRank.valueOf(result.getString("rank"));
                int contribution = 0;
                try {
                    contribution = result.getInt("contribution");
                } catch (SQLException ignored) {}

                clan.putMember(new ClanMember(
                        UUID.fromString(result.getString("player_id")),
                        rank,
                        result.getLong("joined_at"),
                        result.getLong("last_seen"),
                        contribution
                ));
            }
        }
    }

    private void loadTerritories(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_territories");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("clan_id")));
                if (clan == null) {
                    continue;
                }
                clan.addTerritory(new ClanTerritory(
                        UUID.fromString(result.getString("id")),
                        clan.id(),
                        result.getString("world"),
                        result.getInt("min_x"),
                        result.getInt("min_y"),
                        result.getInt("min_z"),
                        result.getInt("max_x"),
                        result.getInt("max_y"),
                        result.getInt("max_z"),
                        result.getString("advanced_claim_id") == null ? null : UUID.fromString(result.getString("advanced_claim_id")),
                        UUID.fromString(result.getString("claimed_by")),
                        result.getLong("claimed_at"),
                        result.getObject("banner_x") == null ? null : result.getInt("banner_x"),
                        result.getObject("banner_y") == null ? null : result.getInt("banner_y"),
                        result.getObject("banner_z") == null ? null : result.getInt("banner_z"),
                        result.getString("name"),
                        result.getBoolean("pvp"),
                        result.getBoolean("is_capital")
                ));
            }
        }
    }

    private void loadDiplomacy(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_diplomacy");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("source_clan_id")));
                if (clan == null) {
                    continue;
                }
                clan.setDiplomacy(UUID.fromString(result.getString("target_clan_id")), DiplomacyRelation.valueOf(result.getString("relation")));
            }
        }
    }

    private void loadUpgrades(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_upgrades");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("clan_id")));
                if (clan == null) {
                    continue;
                }
                try {
                    clan.setUpgradeLevel(ClanUpgrade.valueOf(result.getString("upgrade_name")), result.getInt("upgrade_level"));
                } catch (IllegalArgumentException ignored) {
                    // Upgrade type no longer exists (legacy data); skip it.
                }
            }
        }
    }

    private void loadPermissions(Connection connection, Map<UUID, Clan> clans) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_permissions");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Clan clan = clans.get(UUID.fromString(result.getString("clan_id")));
                if (clan == null) {
                    continue;
                }
                try {
                    ClanRank rank = ClanRank.valueOf(result.getString("rank"));
                    ClanPermission permission = ClanPermission.valueOf(result.getString("permission"));
                    clan.setPermission(rank, permission, true);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException ignored) {
            // Table might not exist yet if plugin just updated
        }
    }

    private void saveClan(Connection connection, Clan clan) throws SQLException {
        String[] columns = {
                "name", "tag", "tag_color", "description", "emblem_material",
                "level", "experience", "upgrade_points", "chest_rows", "spirit_level", "spirit_energy",
                "spirit_awakened_until", "spirit_online_time", "spirit_last_decay", "spirit_ability",
                "spirit_ability_chosen_at", "created_at", "is_open", "home_location", "id"
        };
        String sql = upsertSql("clans", new String[]{"id"}, columns);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            statement.setString(paramIndex++, clan.name());
            statement.setString(paramIndex++, clan.tag());
            statement.setString(paramIndex++, clan.tagColor());
            statement.setString(paramIndex++, clan.description());
            statement.setString(paramIndex++, clan.emblem().name());
            statement.setInt(paramIndex++, clan.level());
            statement.setLong(paramIndex++, clan.experience());
            statement.setInt(paramIndex++, clan.upgradePoints());
            statement.setInt(paramIndex++, clan.chestRows());
            statement.setInt(paramIndex++, clan.spirit().level());
            statement.setLong(paramIndex++, clan.spirit().energy());
            statement.setLong(paramIndex++, clan.spirit().awakenedUntil());
            statement.setLong(paramIndex++, clan.spirit().onlineTimeWeekly());
            statement.setLong(paramIndex++, clan.spirit().lastDecayCheck());
            if (clan.spirit().ability() == null) {
                statement.setNull(paramIndex++, Types.VARCHAR);
            } else {
                statement.setString(paramIndex++, clan.spirit().ability().name());
            }
            statement.setLong(paramIndex++, clan.spirit().abilityChosenAt());
            statement.setLong(paramIndex++, clan.createdAt());
            statement.setInt(paramIndex++, clan.isOpen() ? 1 : 0);
            String serializedHomeLocation = serializeLocation(clan.getHomeLocation().orElse(null));
            if (serializedHomeLocation == null) {
                statement.setNull(paramIndex++, Types.VARCHAR);
            } else {
                statement.setString(paramIndex++, serializedHomeLocation);
            }
            statement.setString(paramIndex++, clan.id().toString());
            statement.executeUpdate();
        }
    }

    private void saveMember(Connection connection, UUID clanId, ClanMember member) throws SQLException {
        String[] columns = {"rank", "joined_at", "last_seen", "contribution", "clan_id", "player_id"};
        String sql = upsertSql("clan_members", new String[]{"clan_id", "player_id"}, columns);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, member.rank().name());
            statement.setLong(2, member.joinedAt());
            statement.setLong(3, member.lastSeen());
            statement.setInt(4, member.contribution());
            statement.setString(5, clanId.toString());
            statement.setString(6, member.playerId().toString());
            statement.executeUpdate();
        }
    }

    private void saveTerritory(Connection connection, ClanTerritory territory) throws SQLException {
        String[] columns = {
                "id", "clan_id", "world", "min_x", "min_y", "min_z", "max_x", "max_y", "max_z",
                "advanced_claim_id", "claimed_by", "claimed_at", "banner_x", "banner_y", "banner_z",
                "name", "pvp", "is_capital"
        };
        String sql = upsertSql("clan_territories", new String[]{"id"}, columns);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            statement.setString(paramIndex++, territory.id().toString());
            statement.setString(paramIndex++, territory.clanId().toString());
            statement.setString(paramIndex++, territory.world());
            statement.setInt(paramIndex++, territory.minX());
            statement.setInt(paramIndex++, territory.minY());
            statement.setInt(paramIndex++, territory.minZ());
            statement.setInt(paramIndex++, territory.maxX());
            statement.setInt(paramIndex++, territory.maxY());
            statement.setInt(paramIndex++, territory.maxZ());
            if (territory.advancedClaimId() == null) {
                statement.setNull(paramIndex++, Types.VARCHAR);
            } else {
                statement.setString(paramIndex++, territory.advancedClaimId().toString());
            }
            statement.setString(paramIndex++, territory.claimedBy().toString());
            statement.setLong(paramIndex++, territory.claimedAt());

            if (territory.bannerX() == null) statement.setNull(paramIndex++, Types.INTEGER); else statement.setInt(paramIndex++, territory.bannerX());
            if (territory.bannerY() == null) statement.setNull(paramIndex++, Types.INTEGER); else statement.setInt(paramIndex++, territory.bannerY());
            if (territory.bannerZ() == null) statement.setNull(paramIndex++, Types.INTEGER); else statement.setInt(paramIndex++, territory.bannerZ());

            if (territory.name() == null) statement.setNull(paramIndex++, Types.VARCHAR); else statement.setString(paramIndex++, territory.name());
            statement.setInt(paramIndex++, territory.pvp() ? 1 : 0);
            statement.setInt(paramIndex++, territory.isCapital() ? 1 : 0);

            statement.executeUpdate();
        }
    }

    private void saveDiplomacy(Connection connection, UUID sourceClanId, UUID targetClanId, DiplomacyRelation relation) throws SQLException {
        String[] columns = {"relation", "updated_at", "source_clan_id", "target_clan_id"};
        String sql = upsertSql("clan_diplomacy", new String[]{"source_clan_id", "target_clan_id"}, columns);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, relation.name());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, sourceClanId.toString());
            statement.setString(4, targetClanId.toString());
            statement.executeUpdate();
        }
    }

    private void saveUpgrade(Connection connection, UUID clanId, ClanUpgrade upgrade, int level) throws SQLException {
        String[] columns = {"upgrade_level", "clan_id", "upgrade_name"};
        String sql = upsertSql("clan_upgrades", new String[]{"clan_id", "upgrade_name"}, columns);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, level);
            statement.setString(2, clanId.toString());
            statement.setString(3, upgrade.name());
            statement.executeUpdate();
        }
    }

    private void savePermissions(Connection connection, Clan clan) throws SQLException {
        try {
            // First delete all permissions for this clan to easily save them all
            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM clan_permissions WHERE clan_id = ?")) {
                deleteStatement.setString(1, clan.id().toString());
                deleteStatement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO clan_permissions (clan_id, rank, permission) VALUES (?, ?, ?)")) {
                for (ClanRank rank : ClanRank.values()) {
                    for (ClanPermission permission : ClanPermission.values()) {
                        if (clan.getPermission(rank, permission)) {
                            statement.setString(1, clan.id().toString());
                            statement.setString(2, rank.name());
                            statement.setString(3, permission.name());
                            statement.addBatch();
                        }
                    }
                }
                statement.executeBatch();
            }
        } catch (SQLException ignored) {
            // Table might not exist yet if plugin just updated
        }
    }

    /**
     * Builds an atomic {@code INSERT ... ON CONFLICT/DUPLICATE KEY UPDATE} statement, replacing
     * the previous non-atomic "SELECT exists then INSERT/UPDATE" pattern. Two concurrent saves for
     * the same row (e.g. two members of the same clan changing settings at once, across the
     * connection pool) could previously interleave between the exists-check and the branch taken,
     * silently losing an update; a single upsert statement removes that window entirely.
     *
     * @param table            target table name
     * @param conflictColumns  columns making up the row's natural key (used in SQLite's
     *                         {@code ON CONFLICT(...)} clause; ignored for MySQL, which resolves
     *                         the conflicting unique/primary key implicitly)
     * @param columns          all columns being inserted, in the order values will be bound
     */
    private String upsertSql(String table, String[] conflictColumns, String[] columns) {
        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", Collections.nCopies(columns.length, "?"));
        List<String> conflictList = Arrays.asList(conflictColumns);
        List<String> updateColumns = Arrays.stream(columns)
                .filter(column -> !conflictList.contains(column))
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table)
                .append(" (").append(columnList).append(") VALUES (").append(placeholders).append(") ");

        if (database.type() == DatabaseType.MYSQL) {
            String updates = updateColumns.stream()
                    .map(column -> column + " = VALUES(" + column + ")")
                    .collect(Collectors.joining(", "));
            sql.append("ON DUPLICATE KEY UPDATE ").append(updates);
        } else {
            String updates = updateColumns.stream()
                    .map(column -> column + " = excluded." + column)
                    .collect(Collectors.joining(", "));
            sql.append("ON CONFLICT(").append(String.join(", ", conflictColumns)).append(") DO UPDATE SET ").append(updates);
        }
        return sql.toString();
    }

    // Helper methods for Location serialization/deserialization
    private String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return String.format("%s;%f;%f;%f;%f;%f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    private Location deserializeLocation(String serializedLocation) {
        if (serializedLocation == null || serializedLocation.isEmpty()) {
            return null;
        }
        String[] parts = serializedLocation.split(";");
        if (parts.length != 6) {
            // Log a warning or handle invalid format
            return null;
        }
        try {
            String worldName = parts[0];
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                // Log a warning or handle missing world
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            // Log error
            return null;
        }
    }
}
