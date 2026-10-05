package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.GuildmasterMenu;
import me.lovelace.loveclans.integration.CitizensIntegration;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Right click on the Guildmaster NPC (Citizens NPC bound by id: {@code /loveclansadmin createnpc guildmaster}).
 * Without a clan, or in a clan without the vows permission and without anything to recognize, the player simply
 * gets the clan list ({@code /clans}); otherwise {@link GuildmasterMenu} with the buttons that apply to them.
 */
public final class GuildmasterListener implements Listener {

    private final LoveClansPlugin plugin;
    private final CitizensIntegration citizens;

    public GuildmasterListener(LoveClansPlugin plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        // Cheapest checks first: this fires for every entity right click on the server.
        int boundNpcId = plugin.getConfig().getInt("clans.guildmaster.npc-id", -1);
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }
        event.setCancelled(true);
        // One right click fires the event for both hands; react only to the main one or the menu opens twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Clan> clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clan.isPresent() && GuildmasterMenu.hasMenu(clan.get(), player.getUniqueId())) {
            plugin.getGuiManager().openGuildmaster(player, clan.get());
        } else {
            plugin.getGuiManager().openClanList(player);
        }
    }
}
