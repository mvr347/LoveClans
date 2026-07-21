package me.lovelace.loveclans.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class DatabaseManager implements AutoCloseable {
    private final LoveClansPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private DatabaseType type;

    public DatabaseManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        type = DatabaseType.parse(plugin.getConfig().getString("database.type", "SQLITE"));
        int poolSize = Math.max(1, plugin.getConfig().getInt("database.pool-size", type == DatabaseType.SQLITE ? 1 : 4));

        HikariConfig config = new HikariConfig();
        config.setPoolName("LoveClans-" + type.name().toLowerCase(Locale.ROOT));
        config.setMaximumPoolSize(type == DatabaseType.SQLITE ? 1 : poolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);

        if (type == DatabaseType.MYSQL) {
            configureMySql(config);
        } else {
            configureSqlite(config);
        }

        dataSource = new HikariDataSource(config);
        executor = Executors.newFixedThreadPool(type == DatabaseType.SQLITE ? 1 : poolSize, new ClanThreadFactory());
        createSchema();
    }

    public DataSource dataSource() {
        return Objects.requireNonNull(dataSource, "DatabaseManager is not initialized");
    }

    public ExecutorService executor() {
        return Objects.requireNonNull(executor, "DatabaseManager is not initialized");
    }

    public DatabaseType type() {
        return type;
    }

    private void configureSqlite(HikariConfig config) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder");
        }
        File databaseFile = new File(plugin.getDataFolder(), "loveclans.db");
        File legacyDatabaseFile = new File(plugin.getDataFolder(), "clans.db");
        if (!databaseFile.exists() && legacyDatabaseFile.exists() && !legacyDatabaseFile.renameTo(databaseFile)) {
            throw new IllegalStateException("Unable to migrate legacy clans.db to loveclans.db");
        }
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
    }

    private void configureMySql(HikariConfig config) {
        ConfigurationSection mysql = plugin.getConfig().getConfigurationSection("database.mysql");
        String host = mysql == null ? "localhost" : mysql.getString("host", "localhost");
        int port = mysql == null ? 3306 : mysql.getInt("port", 3306);
        String database = mysql == null ? "minecraft" : mysql.getString("database", "minecraft");
        boolean useSsl = mysql != null && mysql.getBoolean("use-ssl", false);
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&useUnicode=true"
                + "&characterEncoding=utf8");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(mysql == null ? "root" : mysql.getString("username", "root"));
        config.setPassword(mysql == null ? "" : mysql.getString("password", ""));

        ConfigurationSection properties = mysql == null ? null : mysql.getConfigurationSection("properties");
        if (properties != null) {
            for (String key : properties.getKeys(false)) {
                config.addDataSourceProperty(key, properties.get(key));
            }
        }
    }

    private void createSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(32) NOT NULL,
                        tag VARCHAR(16) NOT NULL UNIQUE,
                        tag_color VARCHAR(64) NOT NULL,
                        description TEXT NOT NULL,
                        emblem_material VARCHAR(64) NOT NULL,
                        level INT NOT NULL,
                        experience BIGINT NOT NULL,
                        upgrade_points INT NOT NULL DEFAULT 0,
                        chest_rows INT NOT NULL,
                        spirit_level INT NOT NULL,
                        spirit_energy BIGINT NOT NULL,
                        spirit_awakened_until BIGINT NOT NULL,
                        spirit_online_time BIGINT NOT NULL DEFAULT 0,
                        spirit_last_decay BIGINT NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        is_open TINYINT NOT NULL DEFAULT 1,
                        home_location TEXT,
                        wars_won INT NOT NULL DEFAULT 0,
                        wars_lost INT NOT NULL DEFAULT 0,
                        sieges_won INT NOT NULL DEFAULT 0,
                        sieges_lost INT NOT NULL DEFAULT 0,
                        raids_won INT NOT NULL DEFAULT 0,
                        raids_lost INT NOT NULL DEFAULT 0,
                        influence BIGINT NOT NULL DEFAULT 0,
                        perk VARCHAR(32),
                        perk_chosen_at BIGINT NOT NULL DEFAULT 0,
                        chest_money BIGINT NOT NULL DEFAULT 0,
                        last_tax_at BIGINT NOT NULL DEFAULT 0,
                        chest_tax_locked TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            // Migrations
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN is_open TINYINT NOT NULL DEFAULT 1"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN upgrade_points INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN spirit_online_time BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN spirit_last_decay BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN home_location TEXT"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN spirit_ability VARCHAR(32)"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN spirit_ability_chosen_at BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN wars_won INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN wars_lost INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN sieges_won INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN sieges_lost INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN raids_won INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN raids_lost INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN influence BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN perk VARCHAR(32)"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN perk_chosen_at BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN chest_money BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN last_tax_at BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clans ADD COLUMN chest_tax_locked TINYINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        clan_id VARCHAR(36) NOT NULL,
                        player_id VARCHAR(36) NOT NULL,
                        rank VARCHAR(32) NOT NULL,
                        joined_at BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        contribution INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (clan_id, player_id),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
             try { statement.executeUpdate("ALTER TABLE clan_members ADD COLUMN contribution INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_territories (
                        id VARCHAR(36) PRIMARY KEY,
                        clan_id VARCHAR(36) NOT NULL,
                        world VARCHAR(128) NOT NULL,
                        min_x INT NOT NULL,
                        min_y INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_y INT NOT NULL,
                        max_z INT NOT NULL,
                        advanced_claim_id VARCHAR(36),
                        claimed_by VARCHAR(36) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        banner_x INT,
                        banner_y INT,
                        banner_z INT,
                        name VARCHAR(64),
                        pvp TINYINT DEFAULT 0,
                        is_capital TINYINT DEFAULT 0,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_diplomacy (
                        source_clan_id VARCHAR(36) NOT NULL,
                        target_clan_id VARCHAR(36) NOT NULL,
                        relation VARCHAR(32) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (source_clan_id, target_clan_id),
                        FOREIGN KEY (source_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_upgrades (
                        clan_id VARCHAR(36) NOT NULL,
                        upgrade_name VARCHAR(64) NOT NULL,
                        upgrade_level INT NOT NULL,
                        PRIMARY KEY (clan_id, upgrade_name),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_permissions (
                        clan_id VARCHAR(36) NOT NULL,
                        rank VARCHAR(32) NOT NULL,
                        permission VARCHAR(64) NOT NULL,
                        PRIMARY KEY (clan_id, rank, permission),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_applications (
                        clan_id VARCHAR(36) NOT NULL,
                        applicant_id VARCHAR(36) NOT NULL,
                        applied_at BIGINT NOT NULL,
                        PRIMARY KEY (clan_id, applicant_id),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);


            // Legacy /clan bank ledger (§2 removed the command and merged money into the chest's
            // dedicated money slot - see clans.chest_money). Table is kept only so
            // SqlClanStorage#migrateLegacyBankMoneyAsync can do a one-time transfer into
            // chest_money on first load after upgrading; nothing writes to it anymore.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_bank (
                        clan_id VARCHAR(36) NOT NULL,
                        item_id VARCHAR(128) NOT NULL,
                        amount BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (clan_id, item_id),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            // History for Clan Spirit
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_spirit_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        clan_id VARCHAR(36) NOT NULL,
                        action VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        timestamp BIGINT NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            // Физический общий сундук клана (отдельно от банка-казны)
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_chest (
                        clan_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        contents BLOB,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            // Клановые обеты (§1) — раздельные слоты для еженедельного и ежедневного активного
            // контракта, по одной таблице на тип (одна активная строка на клан за раз в каждой).
            // target/reward_xp/started_at/expires_at хранят снимок сложности на момент выбора
            // (§1.2), чтобы уход/вход участников не менял уже начатый контракт задним числом.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_contracts (
                        clan_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        contract_id VARCHAR(64) NOT NULL,
                        progress INT NOT NULL DEFAULT 0,
                        completed INT NOT NULL DEFAULT 0,
                        claimed INT NOT NULL DEFAULT 0,
                        last_reset BIGINT NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            try { statement.executeUpdate("ALTER TABLE clan_contracts ADD COLUMN target INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clan_contracts ADD COLUMN reward_xp BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clan_contracts ADD COLUMN started_at BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { statement.executeUpdate("ALTER TABLE clan_contracts ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_daily_contracts (
                        clan_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        contract_id VARCHAR(64) NOT NULL,
                        progress INT NOT NULL DEFAULT 0,
                        completed INT NOT NULL DEFAULT 0,
                        claimed INT NOT NULL DEFAULT 0,
                        target INT NOT NULL DEFAULT 0,
                        reward_xp BIGINT NOT NULL DEFAULT 0,
                        started_at BIGINT NOT NULL DEFAULT 0,
                        expires_at BIGINT NOT NULL DEFAULT 0,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            // Эмбарго (§5.2) — взаимный запрет торговли. Одна строка на пару, clan_a/clan_b всегда
            // хранятся в каноническом порядке (clan_a < clan_b по строке), см. DiplomacyManager#pairKey.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_embargoes (
                        clan_a VARCHAR(36) NOT NULL,
                        clan_b VARCHAR(36) NOT NULL,
                        created_at BIGINT NOT NULL,
                        PRIMARY KEY (clan_a, clan_b)
                    )
                    """);

            // Блокада (§5.3) — односторонняя: clan_blocker блокирует clan_blocked.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_blockades (
                        clan_blocker VARCHAR(36) NOT NULL,
                        clan_blocked VARCHAR(36) NOT NULL,
                        created_at BIGINT NOT NULL,
                        PRIMARY KEY (clan_blocker, clan_blocked)
                    )
                    """);

            // Письма между кланами (§5.4).
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_letters (
                        id VARCHAR(36) PRIMARY KEY,
                        clan_from VARCHAR(36) NOT NULL,
                        clan_to VARCHAR(36) NOT NULL,
                        message TEXT NOT NULL,
                        is_read TINYINT NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new StorageException("Unable to create database schema", exception);
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static final class ClanThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "LoveClans-Storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}