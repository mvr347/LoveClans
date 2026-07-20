package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanContractsMenu;
import me.lovelace.loveclans.integration.CitizensIntegration;
import me.lovelace.loveclans.model.quest.objective.MineAnyOreObjective;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;

/** Feeds clan contract progress from ore mining and boss kills, and opens the Marshal NPC menu. */
public class ContractListener implements Listener {

    private final LoveClansPlugin plugin;
    private final CitizensIntegration citizens;

    public ContractListener(LoveClansPlugin plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!MineAnyOreObjective.isOre(event.getBlock().getType())) {
            return;
        }
        plugin.getClanManager().getPlayerClan(event.getPlayer().getUniqueId()).ifPresent(clan ->
                plugin.getContractManager().recordProgress(clan.id(), event.getPlayer().getUniqueId(),
                        Map.of("block_type", event.getBlock().getType())));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.getClanManager().getPlayerClan(killer.getUniqueId()).ifPresent(clan ->
                plugin.getContractManager().recordProgress(clan.id(), killer.getUniqueId(),
                        Map.of("entity_type", event.getEntityType())));
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        int boundNpcId = plugin.getConfig().getInt("clans.contracts.npc-id", -1);
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresentOrElse(
                clan -> new ClanContractsMenu(plugin).open(player, clan),
                () -> plugin.getMessages().send(player, "clan.not-in-clan"));
    }
}
