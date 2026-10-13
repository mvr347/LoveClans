package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanConflictResolvedEvent;
import me.lovelace.loveclans.api.events.ClanLevelUpEvent;
import me.lovelace.loveclans.api.events.ConflictOutcome;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.upgrade.GuildReward;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Level-ups create upgrade offers; a join reminds the owner; a successful defence unlocks a reward. */
public final class UpgradeListener implements Listener {

    private final LoveClansPlugin plugin;

    public UpgradeListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(ClanLevelUpEvent event) {
        plugin.getUpgradeOffers().onLevelUp(event.clan());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
                plugin.getUpgradeOffers().topUp(clan);
                plugin.getUpgradeOffers().remindIfPending(player, clan);
            });
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConflictResolved(ClanConflictResolvedEvent event) {
        if (event.kind() == ConflictKind.SIEGE && event.outcome() == ConflictOutcome.DEFENDER_WIN) {
            plugin.getClanManager().getClanById(event.defenderClanId())
                    .ifPresent(clan -> plugin.getGuildRewards().grant(clan, GuildReward.DEFENDER));
        }
    }
}
