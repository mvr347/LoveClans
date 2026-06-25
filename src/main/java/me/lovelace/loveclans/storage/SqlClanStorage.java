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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
                        ClanSpirit spirit = new ClanSpirit(
                                result.getInt("spirit_level"),
                                result.getLong("spirit_energy"),
                                result.getLong("spirit_awakened_until"),
                                onlineTimeWeekly,
                                lastDecayCheck,
                                spiritAbility
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
                        clans.put(id, clan);
                    }
                }
                loadMembers(connection, clans);
                loadTerritories(connection, clans);
                loadDiplomacy(connection, clans);
                loadUpgrades(connection, clans);
                loadPermissions(connection, clans);
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
                boolean exists = applicationExists(connection, application.clanId(), application.applicantId());
                String sql = exists
                        ? "UPDATE clan_applications SET applied_at = ? WHERE clan_id = ? AND applicant_id = ?"
                        : "INSERT INTO clan_applications (applied_at, clan_id, applicant_id) VALUES (?, ?, ?)";
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
        boolean exists = exists(connection, "clans", "id", clan.id().toString());
        
        // try (java.sql.Statement s = connection.createStatement()) {
        //     s.executeUpdate("ALTER TABLE clans ADD COLUMN upgrade_points INT NOT NULL DEFAULT 0");
        // } catch (SQLException ignored) {}
        
        // try (java.sql.Statement s = connection.createStatement()) {
        //     s.executeUpdate("ALTER TABLE clans ADD COLUMN home_location VARCHAR(255)");
        // } catch (SQLException ignored) {}

        String sql = exists
                ? """
                  UPDATE clans SET name = ?, tag = ?, tag_color = ?, description = ?, emblem_material = ?,
                  level = ?, experience = ?, upgrade_points = ?, chest_rows = ?, spirit_level = ?, spirit_energy = ?,
                  spirit_awakened_until = ?, spirit_online_time = ?, spirit_last_decay = ?, spirit_ability = ?, created_at = ?, is_open = ?,
                  home_location = ? WHERE id = ?
                  """
                : """
                  INSERT INTO clans (name, tag, tag_color, description, emblem_material, level, experience, upgrade_points,
                  chest_rows, spirit_level, spirit_energy, spirit_awakened_until, spirit_online_time, spirit_last_decay, spirit_ability,
                  created_at, is_open, home_location, id)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """;
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
        boolean exists = memberExists(connection, clanId, member.playerId());
        
        // try (java.sql.Statement s = connection.createStatement()) {
        //     s.executeUpdate("ALTER TABLE clan_members ADD COLUMN contribution INT NOT NULL DEFAULT 0");
        // } catch (SQLException ignored) {}
        
        String sql = exists
                ? "UPDATE clan_members SET rank = ?, joined_at = ?, last_seen = ?, contribution = ? WHERE clan_id = ? AND player_id = ?"
                : "INSERT INTO clan_members (rank, joined_at, last_seen, contribution, clan_id, player_id) VALUES (?, ?, ?, ?, ?, ?)";
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
        boolean exists = territoryExists(connection, territory.id());

        // try (java.sql.Statement s = connection.createStatement()) {
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN banner_x INT");
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN banner_y INT");
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN banner_z INT");
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN name VARCHAR(64)");
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN pvp TINYINT DEFAULT 0");
        //      s.executeUpdate("ALTER TABLE clan_territories ADD COLUMN is_capital TINYINT DEFAULT 0");
        // } catch (SQLException ignored) {}

        String sql = exists
                ? """
                  UPDATE clan_territories SET clan_id = ?, world = ?, min_x = ?, min_y = ?, min_z = ?, max_x = ?, max_y = ?, max_z = ?, 
                  advanced_claim_id = ?, claimed_by = ?, claimed_at = ?, banner_x = ?, banner_y = ?, banner_z = ?, name = ?, pvp = ?, is_capital = ?
                  WHERE id = ?
                  """
                : """
                  INSERT INTO clan_territories (id, clan_id, world, min_x, min_y, min_z, max_x, max_y, max_z, advanced_claim_id, claimed_by, claimed_at, banner_x, banner_y, banner_z, name, pvp, is_capital)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            if (!exists) { // For INSERT, id is the first parameter
                statement.setString(paramIndex++, territory.id().toString());
            }
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
            
            if (exists) { // For UPDATE, id is the last parameter (WHERE clause)
                statement.setString(paramIndex++, territory.id().toString());
            }
            
            statement.executeUpdate();
        }
    }

    private void saveDiplomacy(Connection connection, UUID sourceClanId, UUID targetClanId, DiplomacyRelation relation) throws SQLException {
        boolean exists = diplomacyExists(connection, sourceClanId, targetClanId);
        String sql = exists
                ? "UPDATE clan_diplomacy SET relation = ?, updated_at = ? WHERE source_clan_id = ? AND target_clan_id = ?"
                : "INSERT INTO clan_diplomacy (relation, updated_at, source_clan_id, target_clan_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, relation.name());
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, sourceClanId.toString());
            statement.setString(4, targetClanId.toString());
            statement.executeUpdate();
        }
    }

    private void saveUpgrade(Connection connection, UUID clanId, ClanUpgrade upgrade, int level) throws SQLException {
        boolean exists = upgradeExists(connection, clanId, upgrade);
        String sql = exists
                ? "UPDATE clan_upgrades SET upgrade_level = ? WHERE clan_id = ? AND upgrade_name = ?"
                : "INSERT INTO clan_upgrades (upgrade_level, clan_id, upgrade_name) VALUES (?, ?, ?)";
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

    private boolean memberExists(Connection connection, UUID clanId, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM clan_members WHERE clan_id = ? AND player_id = ?")) {
            statement.setString(1, clanId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean territoryExists(Connection connection, UUID territoryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM clan_territories WHERE id = ?")) {
            statement.setString(1, territoryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean diplomacyExists(Connection connection, UUID sourceClanId, UUID targetClanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM clan_diplomacy WHERE source_clan_id = ? AND target_clan_id = ?")) {
            statement.setString(1, sourceClanId.toString());
            statement.setString(2, targetClanId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean upgradeExists(Connection connection, UUID clanId, ClanUpgrade upgrade) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM clan_upgrades WHERE clan_id = ? AND upgrade_name = ?")) {
            statement.setString(1, clanId.toString());
            statement.setString(2, upgrade.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean applicationExists(Connection connection, UUID clanId, UUID applicantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM clan_applications WHERE clan_id = ? AND applicant_id = ?")) {
            statement.setString(1, clanId.toString());
            statement.setString(2, applicantId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean exists(Connection connection, String table, String column, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
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