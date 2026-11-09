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
        // A player who still has to log in or register must not see clan notices: LoveAuth announces itself later
        // through PlayerAuthenticatedEvent, which runs the same check.
        if (isAuthenticated(event.getPlayer())) {
            scheduleReminder(event.getPlayer(), 60L);
        }
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        scheduleReminder(event.player(), 40L);
    }

    private void scheduleReminder(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isAuthenticated(player)) return;
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
                plugin.getUpgradeOffers().topUp(clan);
                plugin.getUpgradeOffers().remindIfPending(player, clan);
            });
        }, delay);
    }

    /** Not cached: LoveAuth may register its oracle later. Without LoveAuth everybody counts as logged in. */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConflictResolved(ClanConflictResolvedEvent event) {
        if (event.kind() == ConflictKind.SIEGE && event.outcome() == ConflictOutcome.DEFENDER_WIN) {
            plugin.getClanManager().getClanById(event.defenderClanId())
                    .ifPresent(clan -> plugin.getGuildRewards().grant(clan, GuildReward.DEFENDER));
        }
    }
}
