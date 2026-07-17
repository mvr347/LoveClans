package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.war.ClanWar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class PlayerConnectionListener implements Listener {
    private final LoveClansPlugin plugin;

    public PlayerConnectionListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getClanManager().updateLastSeen(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        plugin.getClanManager().updateLastSeen(playerId, System.currentTimeMillis());

        plugin.getChatInputListener(playerId);

        plugin.getGuiManager().clearPlayerCache(playerId);

        // Deliberately not gated on the player still being in a clan: they may have been
        // kicked/left while carrying a captured banner (see CombatListener.onDeath for the
        // matching death-path fix).
        for (ClanWar war : plugin.getWarManager().activeWars()) {
            if (war.capturedBannerBy() != null && war.capturedBannerBy().equals(playerId)) {
                plugin.getWarManager().resetBannerCapture(war.id());
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (plugin.getClanManager().getClanItemFactory().isCapturedBanner(item, war.id())) {
                        player.getInventory().setItem(i, null);
                    }
                }
                plugin.getMessages().send(player, "war.banner-dropped");
                break;
            }
        }
    }
}