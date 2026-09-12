package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanCreateMenu;
import me.lovelace.loveclans.integration.CitizensIntegration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * The clan-founding NPC: right-clicking it sells a Foundation Banner (see
 * util.ClanItemFactory#createFoundationBanner) instead of the old "/clan create" command. Mirrors
 * ContractListener#onNpcInteract's binding pattern exactly, just against a separate
 * clans.founder.npc-id (see command.ClansAdminCommand#createNpc, "founder" type).
 */
public final class ClanFounderNpcListener implements Listener {

    private final LoveClansPlugin plugin;
    private final CitizensIntegration citizens;

    public ClanFounderNpcListener(LoveClansPlugin plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        int boundNpcId = plugin.getConfig().getInt("clans.founder.npc-id", -1);
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.already-in-clan");
            return;
        }
        new ClanCreateMenu(plugin, player).open();
    }
}
