package me.lovelace.loveclans.service;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that decides whether a clan may raise its Guild Shield and does it. Both entry points (the capital menu
 * button and right-clicking the item) go through here, so the button never promises what the click would refuse.
 * The shield only blocks NEW declarations; it never cancels a running conflict, which is also why it cannot be raised
 * during one.
 */
public final class GuildShieldService {
    /** A {@code deny.*} lang key with its placeholders. */
    public record Block(String key, Map<String, String> placeholders) {
    }

    private final LoveClansPlugin plugin;

    public GuildShieldService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Block> check(Clan clan, UUID actorId) {
        if (!clan.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
            return Optional.of(new Block("deny.no-permission", Map.of()));
        }
        if (plugin.getClanManager().inAnyConflict(clan.id())) {
            return Optional.of(new Block("deny.guild-shield.in-conflict", Map.of()));
        }
        if (plugin.getDiplomacyManager() != null && plugin.getDiplomacyManager().isBlockaded(clan.id())) {
            return Optional.of(new Block("deny.guild-shield.is-blockaded", Map.of()));
        }
        if (plugin.getModifierManager().hasGuildShield(clan.id())) {
            return Optional.of(new Block("deny.guild-shield.already-active", Map.of()));
        }
        if (plugin.getModifierManager().hasGuildShieldCooldown(clan.id())) {
            long remaining = plugin.getModifierManager().getGuildShieldCooldownRemaining(clan.id());
            return Optional.of(new Block("deny.guild-shield.cooldown", Map.of("time", TimeUtil.formatDuration(remaining))));
        }
        return Optional.empty();
    }

    /** Checks, raises the shield and announces it. Returns false (after telling the player why) if it was refused. */
    public boolean activate(Player player, Clan clan) {
        Optional<Block> block = check(clan, player.getUniqueId());
        if (block.isPresent()) {
            plugin.getMessages().sendDeny(player, block.get().key(), block.get().placeholders());
            return false;
        }
        long shieldHours = plugin.getConfig().getLong("diplomacy.guild-shield.duration-hours", 24L);
        long cooldownDays = plugin.getConfig().getLong("diplomacy.guild-shield.cooldown-days", 7L);
        plugin.getModifierManager().grantGuildShield(clan.id(), shieldHours);
        plugin.getModifierManager().grantGuildShieldCooldown(clan.id(), cooldownDays);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.1);
        plugin.getSoundService().playClan(clan, "guild-shield-on");

        clan.members().keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .forEach(p -> plugin.getMessages().send(p, "diplomacy.guild-shield.activated",
                        Map.of("player", player.getName(), "hours", String.valueOf(shieldHours))));
        return true;
    }
}
