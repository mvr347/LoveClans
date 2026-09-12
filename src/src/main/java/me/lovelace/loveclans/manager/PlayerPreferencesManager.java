package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight per-player preference storage (e.g. whether a player accepts
 * incoming clan invites). Persisted to a small YAML file so preferences
 * survive server restarts.
 */
public final class PlayerPreferencesManager {

    private final LoveClansPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private final Map<UUID, Boolean> invitesEnabledCache = new ConcurrentHashMap<>();

    public PlayerPreferencesManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/player-settings.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            config = new YamlConfiguration();
            return;
        }
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (section.isSet(key + ".invites-enabled")) {
                    invitesEnabledCache.put(uuid, section.getBoolean(key + ".invites-enabled", true));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить player-settings.yml: " + e.getMessage());
        }
    }

    /**
     * Whether this player currently accepts incoming clan invites. Defaults to true.
     */
    public boolean isInvitesEnabled(UUID uuid) {
        return invitesEnabledCache.getOrDefault(uuid, true);
    }

    public void setInvitesEnabled(UUID uuid, boolean enabled) {
        invitesEnabledCache.put(uuid, enabled);
        config.set("players." + uuid + ".invites-enabled", enabled);
        save();
    }

    /**
     * Flips the player's invites-enabled state and returns the new value.
     */
    public boolean toggleInvitesEnabled(UUID uuid) {
        boolean newState = !isInvitesEnabled(uuid);
        setInvitesEnabled(uuid, newState);
        return newState;
    }
}
