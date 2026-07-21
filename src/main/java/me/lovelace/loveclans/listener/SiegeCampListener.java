package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.SiegeManager;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

/**
 * Осадные лагеря (§3.1) - физические CAMPFIRE-блоки, помеченные ID осады и индексом лагеря
 * (см. {@link me.lovelace.loveclans.util.ClanItemFactory#SIEGE_ID_KEY}). Только защищающийся
 * клан может их разрушать (сбивая отсчёт этого лагеря); сама замена блока на воздух и
 * последующее восстановление делает {@link SiegeManager}, здесь только разрешение/запрет и
 * обратная связь игроку.
 */
public final class SiegeCampListener implements Listener {
    private final LoveClansPlugin plugin;

    public SiegeCampListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.CAMPFIRE) {
            return;
        }
        SiegeManager siegeManager = plugin.getSiegeManager();
        Optional<SiegeManager.SiegeCampRef> campOpt = siegeManager.findCampAt(event.getBlock().getLocation());
        if (campOpt.isEmpty()) {
            return; // Not a siege camp - a regular player-placed campfire.
        }
        event.setCancelled(true); // We handle removal ourselves via SiegeManager#breakCamp.

        SiegeManager.SiegeCampRef camp = campOpt.get();
        Player player = event.getPlayer();
        Optional<Clan> breakerClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (breakerClan.isEmpty() || !breakerClan.get().id().equals(camp.defenderClanId())) {
            plugin.getMessages().send(player, "siege.camp.not-your-camp");
            return;
        }

        if (!siegeManager.breakCamp(camp.siegeId(), camp.campIndex())) {
            plugin.getMessages().send(player, "siege.camp.not-your-camp");
        }
    }
}
