package me.lovelace.loveclans.siege;

import java.util.*;
import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;

/** Protects temporary scenery without treating decorative flags as territorial banners. */
public final class SiegeSceneryListener implements Listener {
  private final LoveClansPlugin plugin;
  private final Map<UUID, Long> clicks = new HashMap<>();

  public SiegeSceneryListener(LoveClansPlugin plugin) {
    this.plugin = plugin;
  }

  private SiegeScenery scene() {
    return plugin.getSiegeManager().scenery();
  }

  private boolean owned(Block b) {
    return b != null && scene().at(b).isPresent();
  }

  private void interact(Player p, SiegeScenery.Key key, boolean attack) {
    var plan = scene().site(key.siege(), key.index());
    if (plan == null) return;
    var at = plan.camp().toLocation();
    if (at == null || !at.getWorld().equals(p.getWorld())) return;
    // Clicking tower blocks can be above the old ground objective. Bound distance to the scenery,
    // not C.
    if (Math.abs(at.getX() - p.getX()) > 8
        || Math.abs(at.getZ() - p.getZ()) > 8
        || Math.abs(at.getY() - p.getY()) > 10) return;
    var siege = plugin.getSiegeManager().findSiegeById(key.siege());
    if (siege.isEmpty()) return;
    var clan = plugin.getClanManager().getPlayerClan(p.getUniqueId());
    if (clan.isEmpty()) return;
    long now = System.currentTimeMillis();
    if (now - clicks.getOrDefault(p.getUniqueId(), 0L) < 500) return;
    clicks.put(p.getUniqueId(), now);
    if (attack) {
      if (clan.get().id().equals(siege.get().defenderClanId()))
        plugin.getSiegeManager().breakCamp(key.siege(), key.index());
      else plugin.getMessages().send(p, "siege.camp.not-your-camp");
    } else if (clan.get().id().equals(siege.get().attackerClanId()))
      plugin.getGuiManager().openCampFortify(p, key.siege(), key.index());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void click(PlayerInteractEvent e) {
    if (e.getClickedBlock() == null) return;
    scene()
        .at(e.getClickedBlock())
        .ifPresent(
            key -> {
              e.setCancelled(true);
              if (e.getHand() != EquipmentSlot.HAND) return;
              if (e.getAction() == Action.LEFT_CLICK_BLOCK) interact(e.getPlayer(), key, true);
              else if (e.getAction() == Action.RIGHT_CLICK_BLOCK)
                interact(e.getPlayer(), key, false);
            });
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void entity(PlayerInteractEntityEvent e) {
    scene()
        .entity(e.getRightClicked())
        .ifPresent(
            key -> {
              e.setCancelled(true);
              if (e.getHand() == EquipmentSlot.HAND) interact(e.getPlayer(), key, false);
            });
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void changeBlock(EntityChangeBlockEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void damage(EntityDamageEvent e) {
    scene()
        .entity(e.getEntity())
        .ifPresent(
            key -> {
              e.setCancelled(true);
              if (e instanceof EntityDamageByEntityEvent hit
                  && hit.getDamager() instanceof Player p) interact(p, key, true);
            });
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void broken(BlockBreakEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void placed(BlockPlaceEvent e) {
    if (owned(e.getBlock()) || scene().reserved(e.getBlock().getLocation())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void bucket(PlayerBucketEmptyEvent e) {
    if (scene().reserved(e.getBlock().getLocation())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void bucketFill(PlayerBucketFillEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void fluid(BlockFromToEvent e) {
    if (scene().reserved(e.getToBlock().getLocation()) || owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void physics(BlockPhysicsEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void burn(BlockBurnEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void ignite(BlockIgniteEvent e) {
    if (owned(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void piston(BlockPistonExtendEvent e) {
    if (e.getBlocks().stream()
        .anyMatch(b -> owned(b) || scene().reserved(b.getRelative(e.getDirection()).getLocation())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void pistonBack(BlockPistonRetractEvent e) {
    if (e.getBlocks().stream().anyMatch(this::owned)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void explode(EntityExplodeEvent e) {
    e.blockList().removeIf(b -> scene().reserved(b.getLocation()));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void blockExplosion(BlockExplodeEvent e) {
    e.blockList().removeIf(b -> scene().reserved(b.getLocation()));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void move(InventoryMoveItemEvent e) {
    if ((e.getSource().getLocation() != null && scene().reserved(e.getSource().getLocation()))
        || (e.getDestination().getLocation() != null
            && scene().reserved(e.getDestination().getLocation()))) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void pickup(InventoryPickupItemEvent e) {
    if (e.getInventory().getLocation() != null && scene().reserved(e.getInventory().getLocation()))
      e.setCancelled(true);
  }

  @EventHandler
  public void loaded(ChunkLoadEvent e) {
    scene().purgeUntracked(e.getChunk());
  }

  @EventHandler
  public void entities(EntitiesLoadEvent e) {
    scene().purgeUntracked(e.getChunk());
  }

  @EventHandler
  public void unloaded(ChunkUnloadEvent e) {
    scene().unload(e.getChunk());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    clicks.remove(e.getPlayer().getUniqueId());
  }
}
