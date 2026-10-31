package me.lovelace.loveclans.siege;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure block-space placement. C is the camp objective, A is the furniture floor anchor. */
public final class CampLayout {
  private CampLayout() {}

  public record Offset(int x, int y, int z) {
    public Offset plus(Offset other) {
      return new Offset(x + other.x, y + other.y, z + other.z);
    }
  }

  public enum Direction {
    NORTH(180),
    EAST(270),
    SOUTH(0),
    WEST(90);
    private final float yaw;

    Direction(float yaw) {
      this.yaw = yaw;
    }

    public float yaw() {
      return yaw;
    }

    public Offset rotate(Offset p) {
      return switch (this) {
        case NORTH -> p;
        case EAST -> new Offset(-p.z(), p.y(), p.x());
        case SOUTH -> new Offset(-p.x(), p.y(), -p.z());
        case WEST -> new Offset(p.z(), p.y(), -p.x());
      };
    }

    public static Direction toward(double dx, double dz) {
      if (Math.abs(dx) > Math.abs(dz)) return dx >= 0 ? EAST : WEST;
      return dz >= 0 ? SOUTH : NORTH;
    }
  }

  public static Offset anchor(Direction direction) {
    return direction.rotate(new Offset(0, 0, 1));
  }

  /**
   * Keys relative to A, unrotated. AIR cells reserve traversable headroom, not just solid blocks.
   */
  public static Map<Offset, String> blocks(int level, boolean broken) {
    Map<Offset, String> result = new LinkedHashMap<>();
    if (level == 4 && !broken) {
      // Floors at 1.5, 4 and 6.5 blocks. Every inhabited storey has >= 2 blocks headroom.
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++) {
          result.put(new Offset(x, 1, z), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
          result.put(new Offset(x, 3, z), "minecraft:spruce_slab[type=top,waterlogged=false]");
          result.put(new Offset(x, 6, z), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
        }
      // Rear ladder with landing balconies: enter either storey from the side of the ladder.
      for (int y = 0; y <= 6; y++) {
        result.put(new Offset(0, y, 1), "minecraft:spruce_planks");
        result.put(new Offset(0, y, 2), "minecraft:ladder[facing=south,waterlogged=false]");
      }
      for (int x : new int[] {-1, 1}) {
        result.put(new Offset(x, 1, 2), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
        result.put(new Offset(x, 3, 2), "minecraft:spruce_slab[type=top,waterlogged=false]");
        result.put(new Offset(x, 6, 2), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
      }
      for (int y = 1; y <= 6; y++)
        for (int z = -1; z <= 1; z++) {
          result.put(new Offset(-2, y, z), "minecraft:barrier");
          result.put(new Offset(2, y, z), "minecraft:barrier");
        }
      // Front entrance and windows stay open. Upper bridge is a walkable continuation.
      for (int x : new int[] {-1, 1})
        for (int y = 1; y <= 5; y++)
          if (y != 2 && y != 4) result.put(new Offset(x, y, -2), "minecraft:barrier");
      for (int z = -2; z >= -3; z--)
        for (int x = -1; x <= 1; x++)
          result.put(new Offset(x, 6, z), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
      result.put(
          new Offset(0, 0, -3),
          "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
      result.put(new Offset(0, 1, -2), "minecraft:spruce_slab[type=bottom,waterlogged=false]");
      result.put(new Offset(3, 0, 0), "minecraft:red_banner[rotation=8]");
    } else {
      result.put(
          new Offset(0, 0, -1),
          "minecraft:campfire[facing=north,lit="
              + !broken
              + ",signal_fire=false,waterlogged=false]");
      result.put(
          new Offset(1, 0, 1), "minecraft:" + (broken ? "black" : "red") + "_banner[rotation=8]");
      if (!broken) result.put(new Offset(-1, 0, -2), "minecraft:barrel[facing=up,open=false]");
    }
    return Map.copyOf(result);
  }

  public static String model(int level, boolean broken) {
    if (broken) return "siege_camp_destroyed";
    return level == 4 ? "siege_tower" : level == 0 ? "siege_camp" : "siege_camp_fortified_" + level;
  }
}
