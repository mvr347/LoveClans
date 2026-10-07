package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Automatic cleanup of orphan clan banners (owner's decision, 2026-10-07): a CAPITAL/TERRITORY banner
 * block that no territory points at is removed when its chunk loads, and once at startup for chunks that
 * are already loaded. See ClanManager#cleanOrphanBanners.
 */
public final class OrphanBannerListener implements Listener {
    private final LoveClansPlugin plugin;

    public OrphanBannerListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
        // Registered after clans are loaded (registerListeners runs in the load callback), so the
        // territory index is complete; sweep the chunks that loaded before we were listening.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    plugin.getClanManager().cleanOrphanBanners(chunk);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return; // freshly generated chunks cannot hold clan banners
        Chunk chunk = event.getChunk();
        // Changing blocks from inside the load event is unsafe; do it on the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                plugin.getClanManager().cleanOrphanBanners(chunk);
            }
        });
    }
}
