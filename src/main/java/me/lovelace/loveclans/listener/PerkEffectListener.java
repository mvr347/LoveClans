package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPerk;
import me.lovelace.loveclans.model.quest.objective.MineAnyOreObjective;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Игровые эффекты клановых перков (§7), завязанные на события блоков/сущностей, а не на
 * периодический тик (см. {@link me.lovelace.loveclans.manager.PerkManager} для "постоянных"
 * баффов): ускорение роста и урожайность культур клана ЗЕМЛЕДЕЛИЕ, бонус к добыче руды и
 * частицы клана РЕСУРСОДОБЫЧА, бонусный дроп с боссов.
 */
public final class PerkEffectListener implements Listener {
    private static final Set<Material> HARVEST_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );

    private final LoveClansPlugin plugin;

    public PerkEffectListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable)) {
            return;
        }
        Optional<Clan> owner = plugin.getClanManager().getClanAt(event.getBlock().getLocation());
        if (owner.isEmpty() || !hasPerk(owner.get(), ClanPerk.HARVESTER)) {
            return;
        }
        int bonusPercent = plugin.getConfig().getInt("perks.harvester.crop-growth-bonus-percent", 40);
        if (Math.random() * 100.0 >= bonusPercent) {
            return;
        }
        BlockData data = event.getNewState().getBlockData();
        if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(ageable.getAge() + 1);
            event.getNewState().setBlockData(data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) {
            return;
        }
        Clan clan = clanOpt.get();
        Block block = event.getBlock();

        if (hasPerk(clan, ClanPerk.MINER) && MineAnyOreObjective.isOre(block.getType())) {
            spawnOreParticle(block.getLocation().add(0.5, 0.5, 0.5));
            grantBonusDrop(block, player, block.getLocation(),
                    plugin.getConfig().getInt("perks.miner.resource-yield-bonus-percent", 25));
            return;
        }

        if (hasPerk(clan, ClanPerk.HARVESTER) && HARVEST_CROPS.contains(block.getType())
                && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            grantBonusDrop(block, player, block.getLocation(),
                    plugin.getConfig().getInt("perks.harvester.harvest-yield-bonus-percent", 10));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        String bossType = plugin.getConfig().getString("perks.miner.rare-drop-boss-type", "WITHER");
        if (bossType == null || !bossType.equalsIgnoreCase(entity.getType().name())) {
            return;
        }
        plugin.getClanManager().getPlayerClan(killer.getUniqueId())
                .filter(clan -> hasPerk(clan, ClanPerk.MINER))
                .ifPresent(clan -> {
                    int bonusPercent = plugin.getConfig().getInt("perks.miner.rare-drop-bonus-percent", 15);
                    if (event.getDrops().isEmpty() || Math.random() * 100.0 >= bonusPercent) {
                        return;
                    }
                    ItemStack sample = event.getDrops().get((int) (Math.random() * event.getDrops().size()));
                    event.getDrops().add(sample.clone());
                });
    }

    private void grantBonusDrop(Block block, Player player, Location location, int bonusPercent) {
        if (Math.random() * 100.0 >= bonusPercent) {
            return;
        }
        java.util.List<ItemStack> drops = new java.util.ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        if (drops.isEmpty()) {
            return;
        }
        ItemStack sample = drops.get((int) (Math.random() * drops.size()));
        block.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), sample.clone());
    }

    private void spawnOreParticle(Location location) {
        String particleName = plugin.getConfig().getString("perks.miner.ore-break-particle", "HAPPY_VILLAGER");
        try {
            Particle particle = Particle.valueOf(particleName);
            location.getWorld().spawnParticle(particle, location, 8, 0.3, 0.3, 0.3, 0.01);
        } catch (IllegalArgumentException ignored) {
            // Invalid particle name in config; skip the cosmetic effect.
        }
    }

    private boolean hasPerk(Clan clan, ClanPerk perk) {
        return clan.perk().map(p -> p == perk).orElse(false);
    }
}
