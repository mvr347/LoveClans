package me.lovelace.loveclans.siege;

import java.util.*;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.siege.SiegeCamp;
import me.lovelace.loveclans.util.ItemsAdderHook;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread-owned camp scenery. No tick-by-tick entity reconstruction and no chunk tickets. */
public final class SiegeScenery {
  public record Key(UUID siege, int index) {}

  public record Position(String world, int x, int y, int z) {
    public static Position of(Location at) {
      return new Position(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    public Location location() {
      World w = Bukkit.getWorld(world);
      return w == null ? null : new Location(w, x, y, z);
    }
  }

  public record Plan(
      Key key,
      SiegeCamp camp,
      CampLayout.Direction direction,
      List<SiegeSnapshot.Cell> originals) {}

  private record Visual(UUID model, UUID interaction, String fingerprint) {}

  private final LoveClansPlugin plugin;
  private final NamespacedKey owner;
  private final Map<Key, Plan> sites = new HashMap<>();
  private final Map<Position, Key> blocks = new HashMap<>();
  private final Map<Key, Visual> visuals = new HashMap<>();
  private final Map<Key, String> applied = new HashMap<>();
  private final Set<Key> cleanup = new HashSet<>();
  private final Map<Key, Long> retries = new HashMap<>();
  private boolean warned;
  private final Map<String, Map<CampLayout.Offset, String>> profiles = new HashMap<>();
  private final Set<Material> profileMaterials = new HashSet<>();

  public SiegeScenery(LoveClansPlugin plugin) {
    this.plugin = plugin;
    owner = new NamespacedKey(plugin, "siege_scenery");
    loadProfiles();
  }

  private static void main() {
    if (!Bukkit.isPrimaryThread())
      throw new IllegalStateException("Siege world access off main thread");
  }

  public boolean enabled() {
    return plugin.getConfig().getBoolean("siege.models.enabled", true);
  }

  public boolean towerAvailable() {
    return enabled() && ItemsAdderHook.createCustomStack(modelId(4, false)) != null;
  }

  public String modelId(int level, boolean broken) {
    return plugin
        .getConfig()
        .getString(
            "siege.models.states." + CampLayout.model(level, broken),
            "loveclans:" + CampLayout.model(level, broken));
  }

  /** Reserve all states, including tower headroom, before touching any world block. */
  public List<Plan> plan(UUID siege, Location center, int count, int distance, boolean tower) {
    main();
    List<Plan> result = new ArrayList<>();
    Set<Position> reserved = new HashSet<>();
    double base = ThreadLocalRandomHolder.angle();
    for (int i = 0; i < count; i++) {
      Plan found = null;
      for (int attempt = 0; attempt < 24 && found == null; attempt++) {
        double angle = base + Math.PI * 2 * i / count + (attempt % 3 - 1) * 0.12;
        int radius = distance + (attempt / 3) * 3;
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
        World w = center.getWorld();
        int y = w.getHighestBlockYAt(x, z) + 1;
        CampLayout.Direction dir = direction(center.getX() - x, center.getZ() - z);
        SiegeCamp camp =
            new SiegeCamp(i, w.getName(), x, y, z, System.currentTimeMillis(), false, 0);
        List<SiegeSnapshot.Cell> cells = inspect(camp, dir, tower, reserved);
        if (cells != null) found = new Plan(new Key(siege, i), camp, dir, cells);
      }
      if (found == null) throw new IllegalStateException("siege.models.no-space");
      result.add(found);
      for (var cell : found.originals())
        reserved.add(new Position(cell.world(), cell.x(), cell.y(), cell.z()));
    }
    return List.copyOf(result);
  }

  private List<SiegeSnapshot.Cell> inspect(
      SiegeCamp camp, CampLayout.Direction dir, boolean tower, Set<Position> reserved) {
    Location c = camp.toLocation();
    if (c == null) return null;
    World w = c.getWorld();
    CampLayout.Offset a = CampLayout.anchor(dir);
    int height = tower ? 9 : 4;
    // Physical footprint and empty interior are checked independently of item_display bounds.
    for (int x = -3; x <= 3; x++)
      for (int z = -4; z <= 3; z++)
        for (int y = 0; y < height; y++) {
          var p = dir.rotate(new CampLayout.Offset(x, y, z));
          int bx = camp.x() + a.x() + p.x(), by = camp.y() + p.y(), bz = camp.z() + a.z() + p.z();
          if (by < w.getMinHeight()
              || by >= w.getMaxHeight()
              || reserved.contains(new Position(camp.world(), bx, by, bz))) return null;
          Block b = w.getBlockAt(bx, by, bz);
          if (!replaceable(b)) return null;
        }
    Set<CampLayout.Offset> needed = new LinkedHashSet<>();
    for (int level = 0; level <= (tower ? 4 : 3); level++)
      needed.addAll(profileBlocks(level, false).keySet());
    needed.addAll(profileBlocks(0, true).keySet());
    List<SiegeSnapshot.Cell> cells = new ArrayList<>();
    for (var local : needed) {
      var p = dir.rotate(local);
      Block b = w.getBlockAt(camp.x() + a.x() + p.x(), camp.y() + p.y(), camp.z() + a.z() + p.z());
      if (local.y() == 0 && !b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid())
        return null;
      cells.add(
          new SiegeSnapshot.Cell(
              camp.world(), b.getX(), b.getY(), b.getZ(), b.getBlockData().getAsString()));
    }
    return List.copyOf(cells);
  }

  private static boolean replaceable(Block b) {
    return b.getType().isAir()
        || Set.of(
                Material.SHORT_GRASS,
                Material.TALL_GRASS,
                Material.FERN,
                Material.DEAD_BUSH,
                Material.SNOW)
            .contains(b.getType());
  }

  public void register(Plan plan) {
    main();
    sites.put(plan.key(), plan);
    for (var cell : plan.originals())
      blocks.put(new Position(cell.world(), cell.x(), cell.y(), cell.z()), plan.key());
  }

  public Plan site(UUID siege, int index) {
    return sites.get(new Key(siege, index));
  }

  public Collection<Plan> sites(UUID siege) {
    return sites.values().stream().filter(p -> p.key().siege().equals(siege)).toList();
  }

  public boolean reserved(Location at) {
    if (at == null || at.getWorld() == null) return false;
    return sites.values().stream()
        .anyMatch(
            p -> {
              var c = p.camp();
              return c.world().equals(at.getWorld().getName())
                  && Math.abs(at.getBlockX() - c.x()) <= 5
                  && Math.abs(at.getBlockZ() - c.z()) <= 5
                  && at.getBlockY() >= c.y() - 1
                  && at.getBlockY() <= c.y() + 9;
            });
  }

  public Optional<Key> at(Block b) {
    return Optional.ofNullable(blocks.get(Position.of(b.getLocation())));
  }

  public Optional<Key> entity(Entity e) {
    String value = e.getPersistentDataContainer().get(owner, PersistentDataType.STRING);
    if (value == null) return Optional.empty();
    try {
      int split = value.lastIndexOf(':');
      Key k =
          new Key(
              UUID.fromString(value.substring(0, split)),
              Integer.parseInt(value.substring(split + 1)));
      return sites.containsKey(k) ? Optional.of(k) : Optional.empty();
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  public boolean loaded(Plan plan) {
    World w = Bukkit.getWorld(plan.camp().world());
    if (w == null) return false;
    for (var cell : plan.originals())
      if (!w.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) return false;
    return true;
  }

  public void render(Plan plan, int level, boolean broken) {
    main();
    if (!loaded(plan)) return;
    String fingerprint = level + ":" + broken + ":" + enabled();
    if (fingerprint.equals(applied.get(plan.key()))) {
      updateDisplay(plan, level, broken);
      return;
    }
    World w = Bukkit.getWorld(plan.camp().world());
    var a = CampLayout.anchor(plan.direction());
    Map<Position, BlockData> wanted = new HashMap<>();
    if (enabled() || level == 4) {
      for (var entry : profileBlocks(level, broken).entrySet()) {
        var p = plan.direction().rotate(entry.getKey());
        BlockData data = Bukkit.createBlockData(entry.getValue());
        data.rotate(
            switch (plan.direction()) {
              case NORTH -> StructureRotation.NONE;
              case EAST -> StructureRotation.CLOCKWISE_90;
              case SOUTH -> StructureRotation.CLOCKWISE_180;
              case WEST -> StructureRotation.COUNTERCLOCKWISE_90;
            });
        wanted.put(
            new Position(
                plan.camp().world(),
                plan.camp().x() + a.x() + p.x(),
                plan.camp().y() + p.y(),
                plan.camp().z() + a.z() + p.z()),
            data);
      }
    } else
      wanted.put(
          new Position(plan.camp().world(), plan.camp().x(), plan.camp().y(), plan.camp().z()),
          Bukkit.createBlockData("minecraft:campfire[lit=" + !broken + "]"));
    // Apply supports before ladders. Blocks in the reserved set cannot be modified by players.
    for (var cell : plan.originals()) {
      Position p = new Position(cell.world(), cell.x(), cell.y(), cell.z());
      Block b = w.getBlockAt(cell.x(), cell.y(), cell.z());
      BlockData data = wanted.getOrDefault(p, Bukkit.createBlockData(cell.original()));
      if (!b.getBlockData().matches(data)) b.setBlockData(data, false);
    }
    // A siege flag is decor, never a territory banner. Retain the clan emblem's patterns.
    plugin
        .getSiegeManager()
        .findSiegeById(plan.key().siege())
        .flatMap(s -> plugin.getClanManager().getClanById(s.attackerClanId()))
        .ifPresent(
            clan -> {
              if (broken) return;
              for (var p : wanted.keySet()) {
                Block b = w.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof Banner banner) {
                  banner
                      .getPersistentDataContainer()
                      .set(owner, PersistentDataType.STRING, tag(plan.key()));
                  banner.update(true, false);
                }
              }
            });
    applied.put(plan.key(), fingerprint);
    updateDisplay(plan, level, broken);
  }

  private void updateDisplay(Plan plan, int level, boolean broken) {
    Key key = plan.key();
    if (System.currentTimeMillis() < retries.getOrDefault(key, 0L)) return;
    String id = modelId(level, broken);
    String fingerprint =
        id
            + ":"
            + level
            + ":"
            + broken
            + ":"
            + correction("x")
            + ":"
            + correction("y")
            + ":"
            + correction("z")
            + ":"
            + plugin.getConfig().getDouble("siege.models.yaw-offset", 0);
    Visual old = visuals.get(key);
    if (old != null
        && old.fingerprint().equals(fingerprint)
        && Bukkit.getEntity(old.model()) != null
        && Bukkit.getEntity(old.interaction()) != null) return;
    removeDisplays(key);
    if (!enabled()) return;
    ItemStack stack = ItemsAdderHook.createCustomStack(id);
    if (stack == null) {
      retries.put(key, System.currentTimeMillis() + 10000);
      if (!warned) {
        plugin
            .getLogger()
            .warning(
                "Осадные модели ItemsAdder недоступны. Логика осады и блоки продолжают работать.");
        warned = true;
      }
      return;
    }
    var a = CampLayout.anchor(plan.direction());
    Location at = plan.camp().toLocation().add(a.x() + 0.5, 0, a.z() + 0.5);
    at.add(correction("x"), correction("y"), correction("z"));
    at.setYaw(
        plan.direction().yaw()
            + (float) plugin.getConfig().getDouble("siege.models.yaw-offset", 0));
    List<Entity> created = new ArrayList<>();
    try {
      ItemDisplay model =
          at.getWorld()
              .spawn(
                  at,
                  ItemDisplay.class,
                  e -> {
                    created.add(e);
                    e.setPersistent(false);
                    e.setItemStack(stack.clone());
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    e.setDisplayWidth(level == 4 ? 9 : 6);
                    e.setDisplayHeight(level == 4 ? 10 : 5);
                    e.setViewRange(1);
                    e.getPersistentDataContainer().set(owner, PersistentDataType.STRING, tag(key));
                  });
      // One objective target, not a giant invisible box filling the tent or tower interior.
      Location target = plan.camp().toLocation().add(0.5, 0, 0.5);
      Interaction interaction =
          target
              .getWorld()
              .spawn(
                  target,
                  Interaction.class,
                  e -> {
                    created.add(e);
                    e.setPersistent(false);
                    e.setInteractionWidth(1);
                    e.setInteractionHeight(1);
                    e.setResponsive(true);
                    e.getPersistentDataContainer().set(owner, PersistentDataType.STRING, tag(key));
                  });
      retries.remove(key);
      visuals.put(key, new Visual(model.getUniqueId(), interaction.getUniqueId(), fingerprint));
    } catch (RuntimeException failure) {
      created.forEach(Entity::remove);
      retries.put(key, System.currentTimeMillis() + 10000);
      plugin
          .getLogger()
          .warning(
              "Осадная модель будет создана повторно через 10 секунд: "
                  + failure.getClass().getSimpleName());
    }
  }

  private static String tag(Key key) {
    return key.siege() + ":" + key.index();
  }

  private void removeDisplays(Key key) {
    Visual v = visuals.remove(key);
    if (v == null) return;
    for (UUID id : List.of(v.model(), v.interaction())) {
      Entity e = Bukkit.getEntity(id);
      if (e != null) e.remove();
    }
  }

  public void remove(UUID siege) {
    main();
    for (Plan plan : List.copyOf(sites.values()))
      if (plan.key().siege().equals(siege)) {
        removeDisplays(plan.key());
        cleanup.add(plan.key());
        clean(plan);
      }
  }

  public boolean cleaned(UUID siege) {
    return sites.keySet().stream().noneMatch(k -> k.siege().equals(siege));
  }

  public void tickCleanup() {
    main();
    for (Key key : List.copyOf(cleanup)) {
      Plan p = sites.get(key);
      if (p != null) clean(p);
    }
  }

  private void clean(Plan plan) {
    if (!loaded(plan)) return;
    evacuateIfSupported(plan);
    for (var cell : plan.originals()) {
      Location loc = new Position(cell.world(), cell.x(), cell.y(), cell.z()).location();
      Block b = loc.getBlock();
      // Never delete a block placed by an external administrator after our last shutdown.
      if (ownedMaterial(b.getType()) || profileMaterials.contains(b.getType()) || replaceable(b))
        b.setBlockData(Bukkit.createBlockData(cell.original()), false);
      else
        plugin
            .getLogger()
            .warning(
                "Осадная уборка: посторонний блок оставлен на "
                    + cell.x()
                    + " "
                    + cell.y()
                    + " "
                    + cell.z());
      blocks.remove(Position.of(loc), plan.key());
    }
    retries.remove(plan.key());
    sites.remove(plan.key());
    cleanup.remove(plan.key());
    applied.remove(plan.key());
  }

  private static boolean ownedMaterial(Material m) {
    return Set.of(
            Material.CAMPFIRE,
            Material.RED_BANNER,
            Material.BLACK_BANNER,
            Material.BARREL,
            Material.SPRUCE_SLAB,
            Material.SPRUCE_STAIRS,
            Material.SPRUCE_PLANKS,
            Material.LADDER,
            Material.BARRIER)
        .contains(m);
  }

  public void evacuateIfSupported(Plan plan) {
    main();
    if (!loaded(plan)) return;
    for (var cell : plan.originals()) {
      Block block = Bukkit.getWorld(cell.world()).getBlockAt(cell.x(), cell.y(), cell.z());
      Material type = block.getType();
      if (type == Material.LADDER || type == Material.BARRIER ||
          (type.isSolid() && type != Material.BARREL &&
              type != Material.CAMPFIRE &&
              !block.getBlockData().getAsString().equals(cell.original()))) {
        evacuate(plan);
        return;
      }
    }
  }

  public void evacuate(Plan plan) {
    main();
    Location c = plan.camp().toLocation();
    if (c == null) return;
    for (Player player : c.getWorld().getPlayers()) {
      Location p = player.getLocation();
      if (Math.abs(p.getX() - c.getX()) > 4
          || Math.abs(p.getZ() - c.getZ()) > 5
          || p.getY() < c.getY()
          || p.getY() > c.getY() + 10) continue;
      for (int r = 6; r <= 10; r++) {
        int x = c.getBlockX() + r, z = c.getBlockZ();
        int y = c.getWorld().getHighestBlockYAt(x, z) + 1;
        Location safe = new Location(c.getWorld(), x + 0.5, y, z + 0.5, p.getYaw(), p.getPitch());
        if (safe.getBlock().isPassable()
            && safe.clone().add(0, 1, 0).getBlock().isPassable()
            && !safe.clone().add(0, -1, 0).getBlock().isLiquid()) {
          player.teleport(safe);
          player.setFallDistance(0);
          break;
        }
      }
    }
  }

  public void unload(org.bukkit.Chunk chunk) {
    main();
    for (Entity entity : chunk.getEntities())
      if (entity.getPersistentDataContainer().has(owner, PersistentDataType.STRING))
        entity.remove();
    applied.clear();
    visuals.entrySet().removeIf(e -> Bukkit.getEntity(e.getValue().model()) == null);
  }

  public void detachDisplays() {
    main();
    for (Key key : List.copyOf(visuals.keySet())) removeDisplays(key);
  }

  public void purgeUntracked(org.bukkit.Chunk chunk) {
    main();
    for (Entity e : chunk.getEntities())
      if (e.getPersistentDataContainer().has(owner, PersistentDataType.STRING)) {
        boolean tracked =
            visuals.values().stream()
                .anyMatch(
                    v ->
                        v.model().equals(e.getUniqueId())
                            || v.interaction().equals(e.getUniqueId()));
        if (!tracked) e.remove();
      }
  }

  private double correction(String axis) {
    double value = plugin.getConfig().getDouble("siege.models.model-offset." + axis, 0);
    return Double.isFinite(value) ? Math.max(-2, Math.min(2, value)) : 0;
  }

  private Map<CampLayout.Offset, String> profileBlocks(int level, boolean broken) {
    return profiles.getOrDefault(CampLayout.model(level, broken), CampLayout.blocks(level, broken));
  }

  private CampLayout.Direction direction(double dx, double dz) {
    String requested = plugin.getConfig().getString("siege.models.direction", "toward-target");
    if (requested.equalsIgnoreCase("toward-target")) return CampLayout.Direction.toward(dx, dz);
    try {
      return CampLayout.Direction.valueOf(requested.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return CampLayout.Direction.toward(dx, dz);
    }
  }

  private void loadProfiles() {
    java.io.File file = new java.io.File(plugin.getDataFolder(), "siege-layouts.yml");
    if (!file.exists()) plugin.saveResource("siege-layouts.yml", false);
    var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    var section = yaml.getConfigurationSection("profiles");
    if (section == null) return;
    for (String state : section.getKeys(false)) {
      try {
        Map<CampLayout.Offset, String> parsed = new LinkedHashMap<>();
        for (Map<?, ?> entry : section.getMapList(state)) {
          if (!(entry.get("offset") instanceof List<?> xyz) || xyz.size() != 3)
            throw new IllegalArgumentException("offset");
          var offset =
              new CampLayout.Offset(
                  ((Number) xyz.get(0)).intValue(),
                  ((Number) xyz.get(1)).intValue(),
                  ((Number) xyz.get(2)).intValue());
          if (Math.abs(offset.x()) > 3
              || offset.z() < -4
              || offset.z() > 3
              || offset.y() < 0
              || offset.y() > 8) throw new IllegalArgumentException("bounds");
          String data = String.valueOf(entry.get("block"));
          BlockData block = Bukkit.createBlockData(data);
          if (block.getMaterial() == Material.WATER || block.getMaterial() == Material.LAVA)
            throw new IllegalArgumentException("fluid");
          profileMaterials.add(block.getMaterial());
          parsed.put(offset, block.getAsString());
        }
        if (!parsed.isEmpty()) profiles.put(state, Map.copyOf(parsed));
      } catch (RuntimeException error) {
        plugin
            .getLogger()
            .warning(
                "Неверная схема осадной модели "
                    + state
                    + ": используется встроенная схема. "
                    + error.getMessage());
      }
    }
  }

  private static final class ThreadLocalRandomHolder {
    static double angle() {
      return java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI * 2);
    }
  }
}
