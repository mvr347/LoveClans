package me.lovelace.loveclans;

import me.lovelace.loveclans.api.LoveClansAPI;
import me.lovelace.loveclans.command.ClanCommand;
import me.lovelace.loveclans.economy.ItemsAdderEconomyService;
import me.lovelace.loveclans.manager.GuiManager;
import me.lovelace.loveclans.integration.AdvancedClaimsHook;
import me.lovelace.loveclans.integration.PlaceholderAPIHook;
import me.lovelace.loveclans.listener.ArtifactListener;
import me.lovelace.loveclans.listener.ChatInputListener;
import me.lovelace.loveclans.listener.ClanProtectionListener;
import me.lovelace.loveclans.listener.CombatListener;
import me.lovelace.loveclans.listener.PlayerConnectionListener;
import me.lovelace.loveclans.listener.ShieldColorListener;
import me.lovelace.loveclans.manager.AfkManager;
import me.lovelace.loveclans.manager.ArtifactManager;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.manager.RitualManager;
import me.lovelace.loveclans.manager.ShieldColorManager;
import me.lovelace.loveclans.manager.SpiritManager;
import me.lovelace.loveclans.manager.SuccessionManager;
import me.lovelace.loveclans.manager.WarManager;
import me.lovelace.loveclans.service.MessageService;
import me.lovelace.loveclans.storage.ClanStorage;
import me.lovelace.loveclans.storage.DatabaseManager;
import me.lovelace.loveclans.storage.SqlClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LoveClansPlugin extends JavaPlugin {
    private static final int LOVECLAIMS_INIT_RETRY_ATTEMPTS = 6;
    private static final long LOVECLAIMS_INIT_RETRY_DELAY_TICKS = 20L * 5L;

    private DatabaseManager databaseManager;
    private ClanStorage storage;
    private MessageService messages;
    private ClanManager clanManager;
    private WarManager warManager;
    private RitualManager ritualManager;
    private SuccessionManager successionManager;
    private SpiritManager spiritManager;
    private AfkManager afkManager;
    private ArtifactManager artifactManager;
    private GuiManager guiManager;
    private ShieldColorManager shieldColorManager;
    private AdvancedClaimsHook advancedClaimsHook;
    private ItemsAdderEconomyService itemsAdderEconomyService;
    private ClanProtectionListener clanProtectionListener;
    private BukkitTask heartbeatTask;
    private BukkitTask warTickTask;
    private final Map<UUID, BiConsumer<String, Boolean>> chatInputListeners = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new MessageService(this);
        messages.reload();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        storage = new SqlClanStorage(databaseManager);

        clanManager = new ClanManager(this, storage);
        warManager = new WarManager(this);
        ritualManager = new RitualManager(this);
        successionManager = new SuccessionManager(this);
        spiritManager = new SpiritManager(this);
        afkManager = new AfkManager(this);
        artifactManager = new ArtifactManager(this);
        guiManager = new GuiManager(this);
        shieldColorManager = new ShieldColorManager(this);
        advancedClaimsHook = new AdvancedClaimsHook(this);
        itemsAdderEconomyService = new ItemsAdderEconomyService();

        clanManager.loadAsync().thenRunAsync(() -> {
            runSync(() -> {
                LoveClansAPI.init(this);

                registerCommands();
                registerListeners();
                registerIntegrations();

                try {
                    if (Bukkit.getPluginManager().isPluginEnabled("LoveClaims")) {
                        advancedClaimsHook.initialize();
                        if (advancedClaimsHook.enabled()) {
                            getLogger().info("Успешная интеграция с LoveClaimsAPI!");
                        } else {
                            // LoveClaimsAPI может не успеть инициализироваться к этому моменту
                            // (асинхронная загрузка приватов внутри LoveClaims). Пробуем ещё раз
                            // несколько раз с задержкой, не блокируя запуск нашего плагина.
                            retryAdvancedClaimsHook(LOVECLAIMS_INIT_RETRY_ATTEMPTS);
                        }
                    }
                } catch (Throwable t) {
                    getLogger().warning("Не удалось инициализировать хук LoveClaims: " + t.getMessage());
                }

                spiritManager.start();
                successionManager.start();

                heartbeatTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    try {
                        ritualManager.tick();
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Ritual tick failed", t);
                    }
                }, 20L * 60L, 20L * 60L);

                // Войны тикают раз в секунду (а не раз в минуту, как ritualManager) - иначе
                // отсчёт до капитуляции и подсветка врагов на территории обновлялись бы слишком
                // редко, чтобы выглядеть как живой таймер/эффект.
                warTickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    try {
                        warManager.tick();
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.SEVERE, "War tick failed", t);
                    }
                    try {
                        if (clanProtectionListener != null) {
                            clanProtectionListener.updateGlowingPlayers();
                        }
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Glow update failed", t);
                    }
                }, 20L, 20L);
            });
        }).exceptionally(throwable -> {
            getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА ЗАГРУЗКИ ПЛАГИНА: " + throwable.getMessage());
            throwable.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return null;
        });
    }

    /**
     * Повторяет попытку инициализации хука LoveClaims с задержкой, пока не останется
     * попыток или LoveClaims не отключится. Нужно потому, что LoveClaimsAPI может
     * стать готовым позже одного фиксированного ретрая (например, при медленной
     * загрузке базы данных LoveClaims).
     */
    private void retryAdvancedClaimsHook(int attemptsLeft) {
        if (attemptsLeft <= 0) {
            getLogger().warning("LoveClaimsAPI так и не инициализировался — интеграция отключена.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("LoveClaims")) {
                getLogger().warning("LoveClaims был отключён во время инициализации — интеграция отключена.");
                return;
            }
            advancedClaimsHook.initialize();
            if (advancedClaimsHook.enabled()) {
                getLogger().info("Успешная интеграция с LoveClaimsAPI (повторная попытка)!");
            } else {
                retryAdvancedClaimsHook(attemptsLeft - 1);
            }
        }, LOVECLAIMS_INIT_RETRY_DELAY_TICKS);
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        if (warTickTask != null) {
            warTickTask.cancel();
        }
        if (spiritManager != null) {
            spiritManager.stop();
        }
        if (successionManager != null) {
            successionManager.stop();
        }
        LoveClansAPI.shutdown();
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MessageService getMessages() {
        return messages;
    }

    public ClanStorage getStorage() {
        return storage;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public RitualManager getRitualManager() {
        return ritualManager;
    }

    public SuccessionManager getSuccessionManager() {
        return successionManager;
    }

    public SpiritManager getSpiritManager() {
        return spiritManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public ArtifactManager getArtifactManager() {
        return artifactManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ShieldColorManager getShieldColorManager() {
        return shieldColorManager;
    }

    public AdvancedClaimsHook getAdvancedClaimsHook() {
        return advancedClaimsHook;
    }

    public ItemsAdderEconomyService getItemsAdderEconomyService() {
        return itemsAdderEconomyService;
    }

    public CompletableFuture<Void> runSync(Runnable runnable) {
        return supplySync(() -> {
            runnable.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public void sendOperationError(CommandSender sender, Throwable throwable) {
        Throwable root = unwrap(throwable);
        if (root instanceof me.lovelace.loveclans.manager.WarCooldownException cooldown) {
            messages.send(sender, "war.cooldown-active", Map.of("seconds", String.valueOf(cooldown.remainingSeconds())));
            return;
        }
        if (root instanceof me.lovelace.loveclans.manager.SpiritAbilityCooldownException cooldown) {
            messages.send(sender, "gui.spirit.ability.cooldown", Map.of("time", me.lovelace.loveclans.util.TimeUtil.formatDuration(cooldown.remainingMillis())));
            return;
        }
        String key = root.getMessage();
        if (key != null && key.contains(".")) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reason", key);
            placeholders.put("min", String.valueOf(getConfig().getInt("clans.tag.min-length", 2)));
            placeholders.put("max", String.valueOf(getConfig().getInt("clans.tag.max-length", 6)));
            messages.send(sender, key, placeholders);
        } else {
            messages.send(sender, "general.error", Map.of("reason", key == null ? root.getClass().getSimpleName() : key));
        }
    }

    private void registerCommands() {
        ClanCommand executor = new ClanCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("loveclan"), "Command /loveclan is missing from plugin.yml");
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        PluginCommand diploCommand = getCommand("diplo");
        if (diploCommand != null) {
            diploCommand.setExecutor(executor);
            diploCommand.setTabCompleter(executor);
        }
        PluginCommand clansCommand = getCommand("loveclans");
        if (clansCommand != null) {
            clansCommand.setExecutor(executor);
            clansCommand.setTabCompleter(executor);
        }
        PluginCommand adminCommand = getCommand("loveclansadmin");
        if (adminCommand != null) {
            adminCommand.setExecutor(executor);
            adminCommand.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(guiManager, this);
        pluginManager.registerEvents(new PlayerConnectionListener(this), this);
        clanProtectionListener = new ClanProtectionListener(this, clanManager, warManager); // Pass clanManager and warManager
        pluginManager.registerEvents(clanProtectionListener, this);
        pluginManager.registerEvents(new CombatListener(this), this);
        pluginManager.registerEvents(new ArtifactListener(this), this);
        pluginManager.registerEvents(new ChatInputListener(this), this);
        pluginManager.registerEvents(new ShieldColorListener(this), this);
        pluginManager.registerEvents(spiritManager, this);
        pluginManager.registerEvents(afkManager, this);
    }

    private void registerIntegrations() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("LoveTrades")) {
            new me.lovelace.loveclans.integration.LoveTradesHook(this).initialize();
            getLogger().info("Клановая интеграция с LoveTrades подключена (враги не торгуют, союзники получают скидку).");
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public void expectChatInput(UUID playerId, BiConsumer<String, Boolean> callback) {
        chatInputListeners.put(playerId, callback);
    }

    public Optional<BiConsumer<String, Boolean>> getChatInputListener(UUID playerId) {
        return Optional.ofNullable(chatInputListeners.remove(playerId));
    }
}