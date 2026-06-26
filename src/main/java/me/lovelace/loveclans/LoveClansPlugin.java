package me.lovelace.loveclans;

import me.lovelace.loveclans.api.LoveClansAPI;
import me.lovelace.loveclans.command.ClanCommand;
import me.lovelace.loveclans.manager.GuiManager;
import me.lovelace.loveclans.integration.AdvancedClaimsHook;
import me.lovelace.loveclans.integration.PlaceholderAPIHook;
import me.lovelace.loveclans.listener.ArtifactListener;
import me.lovelace.loveclans.listener.ChatInputListener;
import me.lovelace.loveclans.listener.ClanProtectionListener;
import me.lovelace.loveclans.listener.CombatListener;
import me.lovelace.loveclans.listener.PlayerConnectionListener;
import me.lovelace.loveclans.manager.ArtifactManager;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.manager.RitualManager;
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
    private DatabaseManager databaseManager;
    private ClanStorage storage;
    private MessageService messages;
    private ClanManager clanManager;
    private WarManager warManager;
    private RitualManager ritualManager;
    private SuccessionManager successionManager;
    private SpiritManager spiritManager;
    private ArtifactManager artifactManager;
    private GuiManager guiManager;
    private AdvancedClaimsHook advancedClaimsHook;
    private ClanProtectionListener clanProtectionListener;
    private BukkitTask heartbeatTask;
    // private BukkitTask glowingEffectTask; // Temporarily disabled
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
        artifactManager = new ArtifactManager(this);
        guiManager = new GuiManager(this);
        advancedClaimsHook = new AdvancedClaimsHook(this);

        clanManager.loadAsync().thenRunAsync(() -> {
            runSync(() -> {
                LoveClansAPI.init(this);

                registerCommands();
                registerListeners();
                registerIntegrations();

                try {
                    if (Bukkit.getPluginManager().isPluginEnabled("LoveClaims")) {
                        advancedClaimsHook.initialize();
                        getLogger().info("Успешная интеграция с LoveClaimsAPI!");
                    }
                } catch (Throwable t) {
                    getLogger().warning("Не удалось инициализировать хук LoveClaims: " + t.getMessage());
                }

                spiritManager.start();
                successionManager.start();

                heartbeatTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    try {
                        warManager.tick();
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.SEVERE, "War tick failed", t);
                    }
                    try {
                        ritualManager.tick();
                    } catch (Throwable t) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Ritual tick failed", t);
                    }
                }, 20L * 60L, 20L * 60L);

                // glowingEffectTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                //     if (clanProtectionListener != null) {
                //         clanProtectionListener.updateGlowingPlayers();
                //     }
                // }, 20L, 20L); // Temporarily disabled
            });
        }).exceptionally(throwable -> {
            getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА ЗАГРУЗКИ ПЛАГИНА: " + throwable.getMessage());
            throwable.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return null;
        });
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        // if (glowingEffectTask != null) {
        //     glowingEffectTask.cancel();
        // } // Temporarily disabled
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

    public ArtifactManager getArtifactManager() {
        return artifactManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public AdvancedClaimsHook getAdvancedClaimsHook() {
        return advancedClaimsHook;
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
        pluginManager.registerEvents(spiritManager, this);
    }

    private void registerIntegrations() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
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