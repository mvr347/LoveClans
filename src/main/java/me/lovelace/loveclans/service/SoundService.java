package me.lovelace.loveclans.service;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Plays configurable cue sounds to the online members of a clan (never server-wide). Each event key lives under
 * {@code sounds.<key>} with {@code sound}, {@code volume} and {@code pitch}; {@code sounds.enabled} switches all of it off.
 */
public final class SoundService {
    private final LoveClansPlugin plugin;

    public SoundService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void playClan(Clan clan, String key) {
        if (clan == null) return;
        for (UUID memberId : clan.members().keySet()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) play(player, key);
        }
    }

    public void play(Player player, String key) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (section == null) return;
        String name = section.getString("sound", "");
        if (name.isBlank()) return;
        Sound sound = Registry.SOUNDS.match(name);
        if (sound == null) {
            plugin.getLogger().warning("sounds." + key + ".sound: unknown sound '" + name + "'");
            return;
        }
        player.playSound(player.getLocation(), sound, (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
    }
}
