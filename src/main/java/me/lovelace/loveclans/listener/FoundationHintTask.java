package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.FoundationPhase;
import me.lovelace.loveclans.manager.FoundationSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Placement hints for the clan foundation banner: while it is held, the borders of neighbouring
 * claims are shown; once the first placement has been previewed, particles mark the exact block
 * where the banner has to be placed again.
 */
public final class FoundationHintTask implements Runnable {
    private static final long BORDER_TICKS = 120L;
    private static final double RANGE = 120.0D;
    private static final int REFRESH_EVERY = 5; // runs of the task (1 s each)

    private final LoveClansPlugin plugin;
    private int tick;

    public FoundationHintTask(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            var manager = plugin.getClanManager();
            boolean holding = manager.isCreationBanner(player.getInventory().getItemInMainHand())
                    || manager.isCreationBanner(player.getInventory().getItemInOffHand());
            if (!holding) continue;
            var hook = plugin.getAdvancedClaimsHook();
            if (tick % REFRESH_EVERY == 0) {
                for (var claim : hook.claimsNear(player.getLocation(), RANGE)) {
                    hook.showClaimBorder(player, claim.getBoundingBox(), BORDER_TICKS, claim.getId());
                }
            }
            FoundationSession session = manager.getFoundationSession(player.getUniqueId()).orElse(null);
            if (session != null && session.phase() == FoundationPhase.PREVIEWED && session.previewLocation() != null) {
                Location spot = session.previewLocation();
                if (spot.getWorld() != null && spot.getWorld().equals(player.getWorld())
                        && spot.distanceSquared(player.getLocation()) < 90 * 90) {
                    Location center = spot.clone().add(0.5, 0, 0.5);
                    for (int i = 0; i < 6; i++) {
                        player.spawnParticle(Particle.END_ROD, center.clone().add(0, i * 0.4, 0), 1, 0.05, 0, 0.05, 0);
                    }
                }
            }
        }
    }
}
