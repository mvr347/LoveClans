package me.lovelace.loveclans.listener;

import java.util.Optional;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.war.WarState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * During an active war or siege a dead member does not respawn at a bed or the world spawn but near the
 * clan: in a war both sides return to their own clan home; in a siege the attackers return to one of their
 * camps and the defenders to their own home.
 */
public final class ConflictRespawnListener implements Listener {
    private final LoveClansPlugin plugin;

    public ConflictRespawnListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) return;
        Clan clan = clanOpt.get();

        var siege = plugin.getSiegeManager().getActiveSiege(clan.id())
                .filter(s -> s.state() == me.lovelace.loveclans.model.siege.SiegeState.ACTIVE);
        if (siege.isPresent()) {
            Optional<Location> spot = siege.get().attackerClanId().equals(clan.id())
                    ? plugin.getSiegeManager().attackerRespawn(clan.id(), player.getLocation())
                    : plugin.getClanManager().ensureHome(clan);
            if (spot.isPresent()) {
                event.setRespawnLocation(spot.get());
                return;
            }
        }
        var war = plugin.getWarManager().warOf(clan.id());
        if (war.isPresent() && war.get().state() == WarState.ACTIVE) {
            plugin.getClanManager().ensureHome(clan).ifPresent(event::setRespawnLocation);
        }
    }
}
