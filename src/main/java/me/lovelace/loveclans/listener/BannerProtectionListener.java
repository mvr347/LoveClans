package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.util.ClanItemFactory;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Keeps a clan banner block (and the block that carries it) intact against everything that is not a player
 * breaking it: explosions, fire, flowing liquids, pistons and mobs that change blocks. A player's attack on the
 * banner during a war is handled by {@link ClanProtectionListener}; nothing here touches that path. The banner is
 * the clan's territory, and losing it costs the clan dearly, so it must not depend on luck.
 * Only blocks of a banner material are inspected, so the cost on ordinary events is a material comparison.
 */
public final class BannerProtectionListener implements Listener {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private static boolean isBannerMaterial(Material material) {
        return material.name().endsWith("_BANNER");
    }

    private static boolean isClanBanner(Block block) {
        if (!isBannerMaterial(block.getType())) return false;
        return block.getState() instanceof Banner banner
                && banner.getPersistentDataContainer().has(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);
    }

    /** The banner itself, or the block that holds it: the one below a standing banner, behind a wall banner. */
    static boolean isProtected(Block block) {
        if (isClanBanner(block)) return true;
        Block above = block.getRelative(BlockFace.UP);
        if (isBannerMaterial(above.getType()) && !above.getType().name().endsWith("_WALL_BANNER") && isClanBanner(above)) {
            return true;
        }
        for (BlockFace side : SIDES) {
            Block neighbour = block.getRelative(side);
            if (!neighbour.getType().name().endsWith("_WALL_BANNER")) continue;
            if (neighbour.getBlockData() instanceof Directional directional
                    && directional.getFacing().getOppositeFace() == side.getOppositeFace()
                    && isClanBanner(neighbour)) {
                return true;
            }
        }
        return false;
    }

    private static void keepProtected(List<Block> blocks) {
        blocks.removeIf(BannerProtectionListener::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        keepProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        keepProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (isProtected(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block moved : event.getBlocks()) {
            if (isProtected(moved)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block moved : event.getBlocks()) {
            if (isProtected(moved)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }
}
