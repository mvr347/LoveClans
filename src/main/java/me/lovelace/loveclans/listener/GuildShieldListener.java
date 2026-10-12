package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class GuildShieldListener implements Listener {
    private final LoveClansPlugin plugin;

    public GuildShieldListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemFactory().isGuildShieldItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = clanOpt.get();

        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }

        if (plugin.getClanManager().inAnyConflict(clan.id())) {
            plugin.getMessages().send(player, "deny.guild-shield.in-conflict");
            return;
        }

        if (plugin.getDiplomacyManager() != null && plugin.getDiplomacyManager().isBlockaded(clan.id())) {
            plugin.getMessages().send(player, "deny.guild-shield.is-blockaded");
            return;
        }

        if (plugin.getModifierManager().hasGuildShield(clan.id())) {
            plugin.getMessages().send(player, "deny.guild-shield.already-active");
            return;
        }

        if (plugin.getModifierManager().hasGuildShieldCooldown(clan.id())) {
            long remaining = plugin.getModifierManager().getGuildShieldCooldownRemaining(clan.id());
            plugin.getMessages().send(player, "deny.guild-shield.cooldown", Map.of("time", TimeUtil.formatDuration(remaining)));
            return;
        }

        // Consume item
        item.setAmount(item.getAmount() - 1);

        long shieldHours = plugin.getConfig().getLong("diplomacy.guild-shield.duration-hours", 24L);
        long cooldownDays = plugin.getConfig().getLong("diplomacy.guild-shield.cooldown-days", 7L);

        plugin.getModifierManager().grantGuildShield(clan.id(), shieldHours);
        plugin.getModifierManager().grantGuildShieldCooldown(clan.id(), cooldownDays);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 35, 0.5, 0.5, 0.5, 0.1);

        clan.members().keySet().stream()
                .map(org.bukkit.Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .forEach(p -> plugin.getMessages().send(p, "diplomacy.guild-shield.activated", Map.of("player", player.getName())));
    }
}
