package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.ClanContractsMenu;
import me.lovelace.loveclans.integration.CitizensIntegration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.UUID;

/** Feeds clan contract progress from every objective type used in the weekly/daily pools (§1), and opens the Marshal NPC menu. */
public class ContractListener implements Listener {

    private final LoveClansPlugin plugin;
    private final CitizensIntegration citizens;

    public ContractListener(LoveClansPlugin plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    private void record(UUID playerId, Map<String, Object> eventData) {
        plugin.getClanManager().getPlayerClan(playerId).ifPresent(clan ->
                plugin.getContractManager().recordProgress(clan.id(), playerId, eventData));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Forwarded regardless of block type - MINE_ANY_ORE filters to ores itself, MINE_BLOCK matches its configured block.
        record(event.getPlayer().getUniqueId(), Map.of("block_type", event.getBlock().getType()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        record(killer.getUniqueId(), Map.of("entity_type", event.getEntityType()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        record(killer.getUniqueId(), Map.of("killer_id", killer.getUniqueId(), "victim_is_player", Boolean.TRUE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Recipe recipe = event.getRecipe();
        if (recipe == null || recipe.getResult().getType().isAir()) {
            return;
        }
        ItemStack result = recipe.getResult();
        record(player.getUniqueId(), Map.of("crafted_item_type", result.getType(), "amount", result.getAmount()));
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        ItemStack stack = caught.getItemStack();
        record(event.getPlayer().getUniqueId(), Map.of("fished_item_type", stack.getType(), "amount", stack.getAmount()));
    }

    @EventHandler
    public void onEntityBreed(EntityBreedEvent event) {
        LivingEntity breeder = event.getBreeder();
        if (!(breeder instanceof Player player)) {
            return;
        }
        record(player.getUniqueId(), Map.of("bred_animal_type", event.getEntityType()));
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        for (var enchantment : event.getEnchantsToAdd().keySet()) {
            record(player.getUniqueId(), Map.of("enchantment_type", enchantment));
        }
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
