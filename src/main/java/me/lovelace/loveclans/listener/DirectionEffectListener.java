package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanDirection;
import me.lovelace.loveclans.model.quest.objective.MineAnyOreObjective;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Effects of the Farming and Mining development directions (the Military one lives in the damage
 * pipeline, see SpiritManager#onPvp). All percentages come from ClanBonusService.
 *
 * <p>Mining speed uses the player's BLOCK_BREAK_SPEED attribute, which is set when the player starts
 * digging an ore or a log and kept until they dig something else, let go, switch slots or leave.
 * It is deliberately not removed on the break itself: anti-cheat reads the attribute when the block
 * breaks, and a bonus that vanished a tick earlier would make every honest fast break look like
 * a FastBreak.
 */
public final class DirectionEffectListener implements Listener {
    private static final Set<Material> HARVEST_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );

    private final LoveClansPlugin plugin;
    private final NamespacedKey speedKey;

    public DirectionEffectListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "direction_mining_speed");
    }

    private Optional<Clan> clanOf(Player player) {
        return plugin.getClanManager().getPlayerClan(player.getUniqueId());
    }

    // --- Farming ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable)) {
            return;
        }
        Optional<Clan> owner = plugin.getClanManager().getClanAt(event.getBlock().getLocation());
        if (owner.isEmpty()) {
            return;
        }
        double chance = plugin.getClanBonuses().directionValue(owner.get(), ClanDirection.FARMING, "crop-growth-percent");
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() * 100.0D >= chance) {
            return;
        }
        BlockData data = event.getNewState().getBlockData();
        if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(ageable.getAge() + 1);
            event.getNewState().setBlockData(data);
        }
    }

    // --- Extra drops (Farming harvest, Mining ore and logs) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<Clan> clanOpt = clanOf(player);
        if (clanOpt.isEmpty()) {
            return;
        }
        Clan clan = clanOpt.get();
        if (isMiningTarget(block.getType())) {
            grantBonusDrop(block, player, plugin.getClanBonuses().directionValue(clan, ClanDirection.MINING, "extra-drop-percent"));
        } else if (HARVEST_CROPS.contains(block.getType())
                && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            grantBonusDrop(block, player, plugin.getClanBonuses().directionValue(clan, ClanDirection.FARMING, "harvest-yield-percent"));
        }
    }

    private void grantBonusDrop(Block block, Player player, double chancePercent) {
        if (chancePercent <= 0.0D || ThreadLocalRandom.current().nextDouble() * 100.0D >= chancePercent) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand(), player));
        if (drops.isEmpty()) {
            return;
        }
        ItemStack sample = drops.get(ThreadLocalRandom.current().nextInt(drops.size()));
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), sample.clone());
    }

    // --- Mining speed ---

    private static boolean isMiningTarget(Material material) {
        return MineAnyOreObjective.isOre(material) || Tag.LOGS.isTagged(material);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        double percent = 0.0D;
        if (isMiningTarget(event.getBlock().getType())) {
            percent = clanOf(player)
                    .map(clan -> plugin.getClanBonuses().directionValue(clan, ClanDirection.MINING, "break-speed-percent"))
                    .orElse(0.0D);
        }
        setSpeed(player, percent);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        setSpeed(event.getPlayer(), 0.0D);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        setSpeed(event.getPlayer(), 0.0D);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setSpeed(event.getPlayer(), 0.0D);
    }

    private void setSpeed(Player player, double percent) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(speedKey);
        double wanted = percent / 100.0D;
        if (current != null && Math.abs(current.getAmount() - wanted) < 1e-9) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(current);
        }
        if (wanted > 0.0D) {
            attribute.addModifier(new AttributeModifier(speedKey, wanted, AttributeModifier.Operation.ADD_SCALAR));
        }
    }
}
