package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Feeds clan contract progress from every objective type used in the weekly/monthly pools (§1). These events fire
 * constantly for every player, so the common case - no clan has an active contract, or this player's clan has
 * none - returns before any clan lookup and before the event data is built.
 */
public class ContractListener implements Listener {

    private final LoveClansPlugin plugin;

    public ContractListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private void record(UUID playerId, Supplier<Map<String, Object>> eventData) {
        if (!plugin.getContractManager().hasAnyActive()) {
            return;
        }
        plugin.getClanManager().getPlayerClan(playerId).ifPresent(clan ->
                plugin.getContractManager().recordProgress(clan.id(), playerId, eventData));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Forwarded regardless of block type - MINE_ANY_ORE filters to ores itself, MINE_BLOCK matches its configured block.
        record(event.getPlayer().getUniqueId(), () -> Map.of("block_type", event.getBlock().getType()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        record(killer.getUniqueId(), () -> Map.of("entity_type", event.getEntityType()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        record(killer.getUniqueId(), () -> Map.of("killer_id", killer.getUniqueId(), "victim_is_player", Boolean.TRUE));
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
        record(player.getUniqueId(), () -> Map.of("crafted_item_type", result.getType(), "amount", result.getAmount()));
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        ItemStack stack = caught.getItemStack();
        record(event.getPlayer().getUniqueId(), () -> Map.of("fished_item_type", stack.getType(), "amount", stack.getAmount()));
    }

    @EventHandler
    public void onEntityBreed(EntityBreedEvent event) {
        LivingEntity breeder = event.getBreeder();
        if (!(breeder instanceof Player player)) {
            return;
        }
        record(player.getUniqueId(), () -> Map.of("bred_animal_type", event.getEntityType()));
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        for (var enchantment : event.getEnchantsToAdd().keySet()) {
            record(player.getUniqueId(), () -> Map.of("enchantment_type", enchantment));
        }
    }
}
