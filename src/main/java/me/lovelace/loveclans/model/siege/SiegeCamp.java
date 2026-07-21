package me.lovelace.loveclans.model.siege;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Один осадный лагерь (§3.1). {@code standingSince} - момент, с которого лагерь непрерывно стоит
 * неразрушенным (используется для проверки условия победы атакующих); {@code broken}/{@code respawnAt} -
 * состояние временно уничтоженного защитниками лагеря, ожидающего восстановления.
 */
public record SiegeCamp(int index, String world, int x, int y, int z, long standingSince, boolean broken, long respawnAt) {

    public SiegeCamp brokenNow(long now, long respawnDelayMs) {
        return new SiegeCamp(index, world, x, y, z, 0L, true, now + respawnDelayMs);
    }

    public SiegeCamp respawned(long now) {
        return new SiegeCamp(index, world, x, y, z, now, false, 0L);
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x + 0.5, y, z + 0.5);
    }
}
