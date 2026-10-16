package me.lovelace.loveclans.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class DatabaseManager implements AutoCloseable {
    private final LoveClansPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    // Treasury (chest_money) writes carry absolute balances; on MySQL the pool is multi-threaded and
    // two writes for one clan could land out of order. One thread keeps them in submission order.
    private ExecutorService chestMoneyExecutor;
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
        chestMoneyExecutor = Executors.newSingleThreadExecutor(new ClanThreadFactory());
        createSchema();
        migrateEconomy();
    }

    /**
     * Rescales the treasury and escrowed trade money once when LoveCore's economy scale version grew
     * (see {@link EconomyMigration}). SQLite gets a file copy first; MySQL needs a manual backup.
     */
    private void migrateEconomy() {
        java.util.Optional<dev.lovelace.lovecore.api.economy.LoveEconomy> economy;
        try {
            economy = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.LoveEconomy.class);
        } catch (Throwable t) {
            return; // LoveCore API unavailable: nothing to compare the version with
        }
        if (economy.isEmpty()) return;
        int target = economy.get().economyScaleVersion();
        double factor = plugin.getConfig().getDouble("economy.migration.factor", 5.0);
        try (Connection connection = dataSource.getConnection()) {
            if (EconomyMigration.needsRescale(connection, target)) {
                if (type == DatabaseType.SQLITE) {
                    try (Statement st = connection.createStatement()) {
                        st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    }
                    File db = new File(plugin.getDataFolder(), "loveclans.db");
                    File backup = new File(plugin.getDataFolder(), db.getName() + ".pre-economy-v" + target);
                    if (!backup.exists()) {
                        java.nio.file.Files.copy(db.toPath(), backup.toPath());
                        plugin.getLogger().info("Economy migration: database copy saved to " + backup.getName());
                    }
                } else {
                    plugin.getLogger().warning("Economy migration on MySQL: make sure you have a backup of the database.");
                }
            }
            EconomyMigration.migrate(connection, target, factor, plugin.getLogger());
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Economy migration failed - treasury amounts were NOT rescaled", exception);
        }
    }

    public DataSource dataSource() {
        return Objects.requireNonNull(dataSource, "DatabaseManager is not initialized");
    }

    public ExecutorService chestMoneyExecutor() {
        return Objects.requireNonNull(chestMoneyExecutor, "DatabaseManager is not initialized");
    }

    public ExecutorService executor() {
        return Objects.requireNonNull(executor, "DatabaseManager is not initialized");
    }

    /** Логгер плагина — хранилищу он нужен, чтобы не глушить испорченные данные молча. */
    java.util.logging.Logger logger() {
        return plugin.getLogger();
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
            addColumnIfMissing(connection, statement, "clans", "is_open", "TINYINT NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, statement, "clans", "upgrade_points", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_online_time", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_last_decay", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "home_location", "TEXT");
            addColumnIfMissing(connection, statement, "clans", "spirit_ability", "VARCHAR(32)");
            addColumnIfMissing(connection, statement, "clans", "spirit_ability_chosen_at", "BIGINT NOT NULL DEFAULT 0");
            // Power + Mastery spirit (2026-10-12): old spirit_level/spirit_energy become Power, Mastery starts neutral.
            addColumnIfMissing(connection, statement, "clans", "spirit_mastery", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_mastery_day", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_mastery_off", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_mastery_def", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_activity", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_activity_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_loss_progress", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "spirit_loss_notice", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "wars_won", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "wars_lost", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "sieges_won", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "sieges_lost", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "raids_won", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "raids_lost", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "influence", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "perk", "VARCHAR(32)");
            addColumnIfMissing(connection, statement, "clans", "perk_chosen_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "chest_money", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "last_tax_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "chest_tax_locked", "TINYINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "is_recognized", "TINYINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "unpaid_tax_since", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "server_trade_stacks", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clans", "server_trade_week", "INT NOT NULL DEFAULT 0");

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
             addColumnIfMissing(connection, statement, "clan_members", "contribution", "INT NOT NULL DEFAULT 0");

            // min_x..max_z больше не читаются приложением — геометрия территории теперь всегда
            // приходит из LoveClaims через advanced_claim_id (AdvancedClaimsHook#boundingBoxOf).
            // Колонки остались NOT NULL и в схеме (SqlClanStorage пишет туда 0), потому что
            // SQLite не умеет дёшево снять NOT NULL без пересборки таблицы — так безопаснее,
            // чем рисковать миграцией существующих баз ради вычищения мёртвых столбцов.
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

            ConflictCooldownStore.createTable(statement);
            statement.executeUpdate(me.lovelace.loveclans.activity.ActivitySchema.CREATE_ACTIVITY);
            statement.executeUpdate(me.lovelace.loveclans.activity.ActivitySchema.CREATE_PROCESSED);
            statement.executeUpdate(me.lovelace.loveclans.history.HistorySchema.CREATE_TABLE);
            me.lovelace.loveclans.history.HistoryManager.createIndexes(statement);
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
                        id %s,
                        clan_id VARCHAR(36) NOT NULL,
                        action VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        timestamp BIGINT NOT NULL,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """.formatted(autoIncrementPrimaryKey()));

            // Физический общий сундук клана (отдельно от банка-казны)
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_chest (
                        clan_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        contents BLOB,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            // Клановые обеты (§1) — по одному активному обету на клан в каждой из двух таблиц:
            // clan_contracts (недельный) и clan_monthly_contracts (месячный).
            // target/reward_xp/started_at/expires_at хранят снимок сложности на момент выбора
            // (§1.2), чтобы уход/вход участников не менял уже начатый контракт задним числом.
            // 2026-10-05: колонки last_reset здесь больше нет. Она была NOT NULL без значения по
            // умолчанию, а INSERT её не заполнял — на чистой БД любое сохранение недельного обета
            // падало с «NOT NULL constraint failed», то есть обет нельзя было взять вообще.
            statement.executeUpdate(ContractSchema.createTableSql("clan_contracts"));
            // Таблицы старой схемы: дописать недостающие колонки и убрать last_reset (см. выше).
            addColumnIfMissing(connection, statement, "clan_contracts", "target", "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clan_contracts", "reward_xp", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clan_contracts", "started_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "clan_contracts", "expires_at", "BIGINT NOT NULL DEFAULT 0");
            dropColumnIfExists(connection, statement, "clan_contracts", "last_reset");

            statement.executeUpdate(ContractSchema.createTableSql("clan_monthly_contracts"));
            // clan_daily_contracts (ежедневные обеты) убрана из кода 2026-10-05; таблица, если она была
            // создана раньше, остаётся нетронутой — данные не удаляем без явной просьбы владельца.

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

            // Модификаторы клана (репарации, щиты, дебаффы, доступные поводы).
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_modifiers (
                        id VARCHAR(36) PRIMARY KEY,
                        clan_id VARCHAR(36) NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        payload TEXT,
                        started_at BIGINT NOT NULL,
                        ends_at BIGINT NOT NULL,
                        stacks INT NOT NULL DEFAULT 1,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_modifiers_clan ON clan_modifiers(clan_id)");

            // Торговля между кланами через сундук (§4.2) — money/items уже списаны с clan_from
            // (эскроу) на момент создания строки; при ACCEPTED оба зачисляются clan_to, при
            // DECLINED/CANCELLED возвращаются обратно clan_from. items — сериализованный
            // ItemStack[] (см. InventorySerialization), может быть NULL при чисто денежной сделке.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_trades (
                        id VARCHAR(36) PRIMARY KEY,
                        clan_from VARCHAR(36) NOT NULL,
                        clan_to VARCHAR(36) NOT NULL,
                        money BIGINT NOT NULL DEFAULT 0,
                        items BLOB,
                        status VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        resolved_at BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_trade_deliveries (
                        id VARCHAR(36) PRIMARY KEY,
                        clan_id VARCHAR(36) NOT NULL,
                        from_tag VARCHAR(32),
                        from_tag_color VARCHAR(16),
                        money BIGINT NOT NULL DEFAULT 0,
                        items BLOB,
                        due_at BIGINT NOT NULL,
                        money_delivered TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_trade_deliveries_clan ON clan_trade_deliveries(clan_id)");
            // Items owed to a player who was offline when they were granted (orphan banner cleanup).
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_pending_items (
                        id VARCHAR(36) PRIMARY KEY,
                        player_id VARCHAR(36) NOT NULL,
                        clan_id VARCHAR(36) NOT NULL,
                        kind VARCHAR(32) NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """);
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_pending_items_player ON clan_pending_items(player_id)");
            // State orders (clans.trade.state-orders): one row per opened order, contributions per clan.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_state_orders (
                        id VARCHAR(36) PRIMARY KEY,
                        category VARCHAR(16) NOT NULL,
                        materials TEXT NOT NULL,
                        total INT NOT NULL,
                        filled INT NOT NULL DEFAULT 0,
                        starts_at BIGINT NOT NULL,
                        ends_at BIGINT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_state_order_contrib (
                        order_id VARCHAR(36) NOT NULL,
                        clan_id VARCHAR(36) NOT NULL,
                        amount INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (order_id, clan_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_conflicts (
                        id VARCHAR(36) PRIMARY KEY,
                        kind VARCHAR(16) NOT NULL,
                        attacker_clan_id VARCHAR(36) NOT NULL,
                        defender_clan_id VARCHAR(36) NOT NULL,
                        winner_clan_id VARCHAR(36),
                        attacker_score INT NOT NULL DEFAULT 0,
                        defender_score INT NOT NULL DEFAULT 0,
                        started_at BIGINT NOT NULL,
                        ended_at BIGINT NOT NULL
                    )
                    """);
            // Кланы удаляются, а их история остаётся: внешних ключей тут намеренно нет,
            // иначе распад клана стирал бы и противостояния, в которых он участвовал.
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_conflicts_attacker ON clan_conflicts(attacker_clan_id)");
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_conflicts_defender ON clan_conflicts(defender_clan_id)");

            // Дипломатия v2 (§1): живой score отношений A -> B (-100..+100)
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_relations (
                        source_clan_id VARCHAR(36) NOT NULL,
                        target_clan_id VARCHAR(36) NOT NULL,
                        score INT NOT NULL DEFAULT 0,
                        score_24h_ago INT NOT NULL DEFAULT 0,
                        last_trend_update BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (source_clan_id, target_clan_id),
                        FOREIGN KEY (source_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_relations_target ON clan_relations(target_clan_id)");

            // Дипломатия v2 (§5): соглашения (пакты NAP, TRADE, ALLIANCE)
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_pacts (
                        id VARCHAR(36) PRIMARY KEY,
                        first_clan_id VARCHAR(36) NOT NULL,
                        second_clan_id VARCHAR(36) NOT NULL,
                        pact_type VARCHAR(32) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL DEFAULT 0,
                        FOREIGN KEY (first_clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                        FOREIGN KEY (second_clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_pacts_first ON clan_pacts(first_clan_id)");
            createIndex(statement, "CREATE INDEX IF NOT EXISTS idx_clan_pacts_second ON clan_pacts(second_clan_id)");

            migrateLegacyDiplomacy(connection);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_diplomacy_meta (
                        meta_key VARCHAR(64) PRIMARY KEY,
                        meta_value VARCHAR(64) NOT NULL
                    )
                    """);
            migrateSharedPairScores(connection);
            UpgradeStore.createTables(statement);
            UpgradeStore.runLegacyMigration(statement);
        } catch (SQLException exception) {
            throw new StorageException("Unable to create database schema", exception);
        }
    }

    /**
     * Добавляет колонку, если её ещё нет. Раньше каждая миграция была отдельным
     * ALTER TABLE в try с пустым catch: так глушился не только ожидаемый «колонка
     * уже есть», но и любой настоящий сбой — плагин молча стартовал с неполной
     * схемой и падал позже в неожиданном месте.
     *
     * Наличие колонки проверяется через метаданные JDBC, поэтому проверка одинаково
     * работает и на SQLite, и на MySQL.
     */
    private void addColumnIfMissing(Connection connection, Statement statement,
                                    String table, String column, String definition) throws SQLException {
        if (columnExists(connection, table, column)) {
            return;
        }
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException exception) {
            // Колонку мог добавить параллельный старт другого узла — это не ошибка.
            if (columnExists(connection, table, column)) {
                return;
            }
            plugin.getLogger().log(Level.WARNING,
                "Не удалось добавить колонку " + table + "." + column + " — схема осталась неполной", exception);
        }
    }

    private void dropColumnIfExists(Connection connection, Statement statement, String table, String column) throws SQLException {
        if (!columnExists(connection, table, column)) {
            return;
        }
        try {
            statement.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + column);
        } catch (SQLException exception) {
            if (!columnExists(connection, table, column)) {
                return;
            }
            plugin.getLogger().log(Level.WARNING,
                "Не удалось удалить колонку " + table + "." + column + " — сохранение обетов может не работать", exception);
        }
    }

    /** SQLite spells an auto-id column differently from MySQL; "AUTOINCREMENT" is a syntax error on MySQL. */
    private String autoIncrementPrimaryKey() {
        return type == DatabaseType.MYSQL
                ? "BIGINT PRIMARY KEY AUTO_INCREMENT"
                : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    /**
     * "CREATE INDEX IF NOT EXISTS" is SQLite/MariaDB syntax; MySQL rejects it. On MySQL the clause is dropped and the
     * "duplicate key name" error (1061) of an already existing index is ignored.
     */
    private void createIndex(Statement statement, String sql) throws SQLException {
        if (type != DatabaseType.MYSQL) {
            statement.executeUpdate(sql);
            return;
        }
        try {
            statement.executeUpdate(sql.replace("IF NOT EXISTS ", ""));
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 1061) {
                throw exception;
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void migrateLegacyDiplomacy(Connection connection) {
        try {
            if (!tableExists(connection, "clan_diplomacy") || !tableExists(connection, "clan_relations")) {
                return;
            }
            try (Statement checkStmt = connection.createStatement();
                 ResultSet checkRs = checkStmt.executeQuery("SELECT COUNT(*) FROM clan_relations")) {
                if (checkRs.next() && checkRs.getInt(1) > 0) {
                    return;
                }
            }

            String selectSql = "SELECT source_clan_id, target_clan_id, relation, updated_at FROM clan_diplomacy";
            List<Object[]> rows = new ArrayList<>();
            try (Statement selectStmt = connection.createStatement();
                 ResultSet rs = selectStmt.executeQuery(selectSql)) {
                while (rs.next()) {
                    rows.add(new Object[]{
                            rs.getString("source_clan_id"),
                            rs.getString("target_clan_id"),
                            rs.getString("relation"),
                            rs.getLong("updated_at")
                    });
                }
            }

            if (rows.isEmpty()) {
                return;
            }

            String insertRelationSql = type == DatabaseType.MYSQL
                    ? "INSERT IGNORE INTO clan_relations (source_clan_id, target_clan_id, score, score_24h_ago, last_trend_update, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
                    : "INSERT OR IGNORE INTO clan_relations (source_clan_id, target_clan_id, score, score_24h_ago, last_trend_update, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
            String insertPactSql = type == DatabaseType.MYSQL
                    ? "INSERT IGNORE INTO clan_pacts (id, first_clan_id, second_clan_id, pact_type, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)"
                    : "INSERT OR IGNORE INTO clan_pacts (id, first_clan_id, second_clan_id, pact_type, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement relStmt = connection.prepareStatement(insertRelationSql);
                 PreparedStatement pactStmt = connection.prepareStatement(insertPactSql)) {

                Set<String> processedAlliances = new HashSet<>();

                for (Object[] row : rows) {
                    String src = (String) row[0];
                    String tgt = (String) row[1];
                    String rel = (String) row[2];
                    long updated = (Long) row[3];
                    if (updated <= 0) updated = System.currentTimeMillis();

                    if ("ALLY".equalsIgnoreCase(rel)) {
                        relStmt.setString(1, src);
                        relStmt.setString(2, tgt);
                        relStmt.setInt(3, 95);
                        relStmt.setInt(4, 95);
                        relStmt.setLong(5, updated);
                        relStmt.setLong(6, updated);
                        relStmt.executeUpdate();

                        relStmt.setString(1, tgt);
                        relStmt.setString(2, src);
                        relStmt.setInt(3, 95);
                        relStmt.setInt(4, 95);
                        relStmt.setLong(5, updated);
                        relStmt.setLong(6, updated);
                        relStmt.executeUpdate();

                        String pairKey = src.compareTo(tgt) < 0 ? src + ":" + tgt : tgt + ":" + src;
                        if (processedAlliances.add(pairKey)) {
                            pactStmt.setString(1, UUID.randomUUID().toString());
                            pactStmt.setString(2, src);
                            pactStmt.setString(3, tgt);
                            pactStmt.setString(4, "ALLIANCE");
                            pactStmt.setLong(5, updated);
                            pactStmt.setLong(6, 0L);
                            pactStmt.executeUpdate();
                        }
                    } else if ("ENEMY".equalsIgnoreCase(rel)) {
                        relStmt.setString(1, src);
                        relStmt.setString(2, tgt);
                        relStmt.setInt(3, -70);
                        relStmt.setInt(4, -70);
                        relStmt.setLong(5, updated);
                        relStmt.setLong(6, updated);
                        relStmt.executeUpdate();
                    }
                }
                plugin.getLogger().info("Миграция дипломатии v1 -> v2: перенесено " + rows.size() + " записей в clan_relations и clan_pacts.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось выполнить миграцию legacy дипломатии: " + e.getMessage(), e);
        }
    }

    /**
     * Relation scores used to be one-directional ("our view of them" and "their view of us"). The score is now shared by
     * the pair: both mirrored rows get the rounded average. Duplicate pacts (one alliance used to be saved 2-3 times)
     * are collapsed, and rows pointing at clans that no longer exist are dropped. Runs once, guarded by a marker row,
     * and is a single transaction so a failure leaves the old data untouched.
     */
    private void migrateSharedPairScores(Connection connection) {
        final String marker = "pair_score_v1";
        boolean previousAutoCommit = true;
        try {
            try (PreparedStatement check = connection.prepareStatement("SELECT meta_value FROM clan_diplomacy_meta WHERE meta_key = ?")) {
                check.setString(1, marker);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Set<String> clanIds = new HashSet<>();
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM clans")) {
                while (rs.next()) clanIds.add(rs.getString(1));
            }

            // Relations: pair key (smaller id first) -> [score a->b, score b->a, updated_at]
            Map<String, long[]> pairs = new HashMap<>();
            Map<String, String[]> pairIds = new HashMap<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT source_clan_id, target_clan_id, score, updated_at FROM clan_relations")) {
                while (rs.next()) {
                    String src = rs.getString(1);
                    String tgt = rs.getString(2);
                    if (!clanIds.contains(src) || !clanIds.contains(tgt) || src.equals(tgt)) continue;
                    boolean forward = src.compareTo(tgt) < 0;
                    String a = forward ? src : tgt;
                    String b = forward ? tgt : src;
                    String key = a + ":" + b;
                    long[] values = pairs.computeIfAbsent(key, k -> new long[]{0, 0, 0});
                    pairIds.putIfAbsent(key, new String[]{a, b});
                    values[forward ? 0 : 1] = rs.getInt(3);
                    values[2] = Math.max(values[2], rs.getLong(4));
                }
            }

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM clan_relations");
            }
            String insertSql = "INSERT INTO clan_relations (source_clan_id, target_clan_id, score, score_24h_ago, last_trend_update, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                for (Map.Entry<String, long[]> entry : pairs.entrySet()) {
                    long[] v = entry.getValue();
                    int shared = (int) Math.max(-100, Math.min(100, Math.round((v[0] + v[1]) / 2.0)));
                    if (shared == 0) continue;
                    String[] ids = pairIds.get(entry.getKey());
                    long updated = v[2] > 0 ? v[2] : now;
                    for (int i = 0; i < 2; i++) {
                        insert.setString(1, ids[i]);
                        insert.setString(2, ids[1 - i]);
                        insert.setInt(3, shared);
                        insert.setInt(4, shared);
                        insert.setLong(5, now);
                        insert.setLong(6, updated);
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }

            // Pacts: keep the oldest row per (unordered pair, type), drop pacts of vanished clans
            List<String[]> pactRows = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, first_clan_id, second_clan_id, pact_type FROM clan_pacts ORDER BY created_at ASC")) {
                while (rs.next()) {
                    pactRows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
            Set<String> seenPacts = new HashSet<>();
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM clan_pacts WHERE id = ?")) {
                for (String[] row : pactRows) {
                    String a = row[1].compareTo(row[2]) < 0 ? row[1] : row[2];
                    String b = row[1].compareTo(row[2]) < 0 ? row[2] : row[1];
                    boolean valid = clanIds.contains(a) && clanIds.contains(b) && !a.equals(b);
                    if (!valid || !seenPacts.add(a + ":" + b + ":" + row[3])) {
                        delete.setString(1, row[0]);
                        delete.addBatch();
                    }
                }
                delete.executeBatch();
            }

            try (PreparedStatement mark = connection.prepareStatement("INSERT INTO clan_diplomacy_meta (meta_key, meta_value) VALUES (?, ?)")) {
                mark.setString(1, marker);
                mark.setString(2, String.valueOf(now));
                mark.executeUpdate();
            }
            connection.commit();
            plugin.getLogger().info("Миграция дипломатии: общий счёт пары применён к " + pairs.size() + " парам кланов.");
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().log(Level.WARNING, "Не удалось выполнить миграцию общего счёта дипломатии: " + e.getMessage(), e);
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) {}
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (var rs = connection.getMetaData().getTables(null, null, table, null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (chestMoneyExecutor != null) {
            chestMoneyExecutor.shutdown();
            try {
                if (!chestMoneyExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) chestMoneyExecutor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                chestMoneyExecutor.shutdownNow();
            }
        }
        if (executor != null) {
            executor.shutdown();
            try {
                // Give in-flight async saves (queued right before shutdown, e.g. a clan save
                // triggered on the last tick) a chance to finish before the pool goes away -
                // otherwise they race dataSource.close() below and throw/lose the write.
                if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Незавершённые задачи хранилища не успели закончиться за 10с при отключении плагина.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
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