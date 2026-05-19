package me.lovelace.clans.model;

import org.bukkit.Location;
import org.bukkit.World;

public record TerritoryKey(
        String world,
        int chunkX,
        int chunkZ
) {

    public TerritoryKey(World world, int chunkX, int chunkZ) {
        this(world.getName(), chunkX, chunkZ);
    }

    public static TerritoryKey fromLocation(Location location) {
        return new TerritoryKey(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}