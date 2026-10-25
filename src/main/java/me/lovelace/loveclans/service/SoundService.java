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

    /** Accepts a Bukkit enum-style name (ENTITY_PLAYER_LEVELUP) or a namespaced key (minecraft:item.goat_horn.sound.0). */
    static Sound resolve(String name) {
        if (name.contains(":") || name.contains(".")) {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
            return key == null ? null : Registry.SOUNDS.get(key);
        }
        Sound matched = Registry.SOUNDS.match(name);
        if (matched != null) return matched;
        // Registry.match can miss a name that is still a constant of Sound (BLOCK_BELL_USE was reported unknown on 26.2),
        // so fall back to the constant itself before giving up.
        try {
            Object constant = Sound.class.getField(name.trim().toUpperCase(java.util.Locale.ROOT)).get(null);
            return constant instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException | RuntimeException notAConstant) {
            return null;
        }
    }

    /** Ticks of the last GUI sound per player: lets the click sound step aside for a result sound and keeps "open" quiet. */
    private final java.util.Map<UUID, Integer> lastAnyTick = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<UUID, Integer> lastSpecificTick = new java.util.concurrent.ConcurrentHashMap<>();

    /** Plays a GUI sound to the player only (never to others) and remembers it for {@link #guiClickSilenced}. */
    public void playGui(Player player, GuiSounds.Kind kind) {
        if (player == null) return;
        int now = Bukkit.getCurrentTick();
        lastAnyTick.put(player.getUniqueId(), now);
        if (GuiSounds.isSpecific(kind)) lastSpecificTick.put(player.getUniqueId(), now);
        play(player, GuiSounds.keyFor(kind));
    }

    /** True when a result sound (success/denied/error/toggle) already played for this player in the current tick. */
    public boolean guiClickSilenced(Player player) {
        return GuiSounds.suppressClick(lastSpecificTick.get(player.getUniqueId()), Bukkit.getCurrentTick());
    }

    /** True when the "menu opened" sound should be skipped because another GUI sound just played. */
    public boolean guiOpenSilenced(Player player) {
        return GuiSounds.suppressOpen(lastAnyTick.get(player.getUniqueId()), Bukkit.getCurrentTick());
    }

    public void forget(UUID playerId) {
        lastAnyTick.remove(playerId);
        lastSpecificTick.remove(playerId);
    }

    public void play(Player player, String key) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (section == null || !section.isString("sound")) {
            // A config.yml written before this key existed is never merged with the jar: fall back to the bundled default.
            // To mute one cue an admin sets its "sound" to an empty string.
            var defaults = plugin.getConfig().getDefaults();
            section = defaults == null ? null : defaults.getConfigurationSection("sounds." + key);
        }
        if (section == null) return;
        String name = section.getString("sound", "");
        if (name.isBlank()) return;
        Sound sound = resolve(name);
        if (sound == null) {
            plugin.getLogger().warning("sounds." + key + ".sound: unknown sound '" + name + "'");
            return;
        }
        player.playSound(player.getLocation(), sound, (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
    }
}
