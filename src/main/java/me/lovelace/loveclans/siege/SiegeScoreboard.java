package me.lovelace.loveclans.siege;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.lovelace.loveclans.LoveClansPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Temporary siege sidebar for both sides, shown through LoveEssential's ScoreboardService (a Bukkit
 * service). It is reached by reflection through LoveEssential's own class loader, so LoveClans needs no
 * compile-time dependency on the API and silently does nothing when the service is missing.
 */
public final class SiegeScoreboard {
  private static final int PRIORITY = 600;
  private static final Duration TTL = Duration.ofSeconds(60);
  /** A lease is re-created before its TTL runs out (update() does not extend it). */
  private static final long RENEW_MS = 45_000L;

  private record Held(Object lease, long since) {}

  private final LoveClansPlugin plugin;
  private final Map<UUID, Held> held = new HashMap<>();
  private boolean resolved;
  private boolean failed;
  private Object service;
  private Constructor<?> boardCtor;
  private Method show, update, close;

  public SiegeScoreboard(LoveClansPlugin plugin) {
    this.plugin = plugin;
  }

  private boolean resolve() {
    if (resolved) return !failed;
    resolved = true;
    try {
      Plugin essential = Bukkit.getPluginManager().getPlugin("LoveEssential");
      if (essential == null || !essential.isEnabled()) throw new IllegalStateException("LoveEssential is not enabled");
      ClassLoader loader = essential.getClass().getClassLoader();
      Class<?> serviceClass = Class.forName("dev.lovelace.loveessential.api.ScoreboardService", true, loader);
      var registration = Bukkit.getServicesManager().getRegistration(serviceClass);
      if (registration == null) throw new IllegalStateException("ScoreboardService is not registered");
      service = registration.getProvider();
      Class<?> boardClass = Class.forName("dev.lovelace.loveessential.api.ScoreboardService$Board", true, loader);
      Class<?> leaseClass = Class.forName("dev.lovelace.loveessential.api.ScoreboardService$Lease", true, loader);
      boardCtor = boardClass.getConstructor(Component.class, List.class);
      show = serviceClass.getMethod("show", Plugin.class, UUID.class, int.class, Duration.class, boolean.class, boardClass);
      update = leaseClass.getMethod("update", boardClass);
      close = leaseClass.getMethod("close");
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
      failed = true;
      plugin.getLogger().info("Siege scoreboard is unavailable: " + error.getMessage());
      return false;
    }
  }

  /** Shows or refreshes the sidebar of one player. Main thread only. */
  public void show(Player player, Component title, List<Component> lines) {
    if (lines.isEmpty() || lines.size() > 15 || !resolve()) return;
    long now = System.currentTimeMillis();
    try {
      Object board = boardCtor.newInstance(title, lines);
      Held current = held.get(player.getUniqueId());
      if (current != null && now - current.since() < RENEW_MS) {
        update.invoke(current.lease(), board);
        return;
      }
      if (current != null) close.invoke(current.lease());
      Object lease = show.invoke(service, plugin, player.getUniqueId(), PRIORITY, TTL, true, board);
      held.put(player.getUniqueId(), new Held(lease, now));
    } catch (ReflectiveOperationException | RuntimeException error) {
      held.remove(player.getUniqueId());
    }
  }

  public void clear(UUID playerId) {
    Held current = held.remove(playerId);
    if (current == null || failed) return;
    try {
      close.invoke(current.lease());
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // the lease expired or the player left: nothing to release
    }
  }

  public void clearAll() {
    for (UUID id : List.copyOf(held.keySet())) clear(id);
  }
}
