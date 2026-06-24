package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArtifactListener implements Listener {
    private final LoveClansPlugin plugin;
    private final Map<UUID, Long> warHornCooldowns = new ConcurrentHashMap<>();

    public ArtifactListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Optional<ArtifactType> artifact = plugin.getArtifactManager().readArtifact(event.getItem());
        if (artifact.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Clan> clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clan.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        switch (artifact.get()) {
            case WAR_HORN -> {
                long cooldownMillis = plugin.getConfig().getLong("mechanics.artifacts.war-horn-cooldown-seconds", 300L) * 1000L;
                long now = System.currentTimeMillis();
                Long lastUse = warHornCooldowns.get(clan.get().id());
                if (lastUse != null && now - lastUse < cooldownMillis) {
                    long remainingSeconds = (cooldownMillis - (now - lastUse)) / 1000L;
                    plugin.getMessages().send(player, "ritual.cooldown", Map.of("time", String.valueOf(remainingSeconds)));
                    return;
                }
                warHornCooldowns.put(clan.get().id(), now);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0F, 0.8F);
                clan.get().members().keySet().stream()
                        .map(org.bukkit.Bukkit::getPlayer)
                        .filter(java.util.Objects::nonNull)
                        .filter(member -> member.getWorld().equals(player.getWorld()) && member.getLocation().distanceSquared(player.getLocation()) <= 40 * 40)
                        .forEach(member -> member.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 20, 0, true, true, true)));
            }
            case AEGIS_BANNER -> {
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                clan.get().members().keySet().stream()
                        .map(org.bukkit.Bukkit::getPlayer)
                        .filter(java.util.Objects::nonNull)
                        .filter(member -> member.getWorld().equals(player.getWorld()) && member.getLocation().distanceSquared(player.getLocation()) <= 25 * 25)
                        .forEach(member -> member.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 15, 1, true, true, true)));
            }
        }
    }
}
