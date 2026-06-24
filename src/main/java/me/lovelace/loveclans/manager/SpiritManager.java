package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanClaimEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.SpiritHistoryEntry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SpiritManager implements Listener {
    private final LoveClansPlugin plugin;
    private BukkitTask task;
    private static final String SPEED_MODIFIER_NAME = "clans_spirit_speed";

    private final Map<UUID, Long> lastClaimExp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hourlyKills = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastKillReset = new ConcurrentHashMap<>();

    public SpiritManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("mechanics.spirit.enabled", true)) {
            return;
        }
        long period = 20L * Math.max(1, plugin.getConfig().getInt("mechanics.spirit.tick-seconds", 6));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        Bukkit.getScheduler().runTaskTimer(plugin, this::decayCheck, 20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void purgeClan(UUID clanId) {
        lastClaimExp.remove(clanId);
        hourlyKills.remove(clanId);
        lastKillReset.remove(clanId);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeSpeedAttribute(player);
        }
    }

    public void addSpiritExperience(Clan clan, long amount, String reason) {
        int currentLevel = clan.spirit().level();

        if (currentLevel >= 10) return;

        long newExp = clan.spirit().energy() + amount;
        int newLevel = currentLevel;

        while (newLevel < 10 && newExp >= getExpForNextLevel(newLevel)) {
            newExp -= getExpForNextLevel(newLevel);
            newLevel++;
            plugin.getLogger().info("Clan " + clan.tag() + " spirit leveled up to " + newLevel + "!");
            for (UUID memberId : clan.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    plugin.getMessages().send(p, "spirit.level-up", Map.of("level", String.valueOf(newLevel)));
                }
            }
        }

        clan.setSpirit(clan.spirit().withLevel(newLevel).addEnergy(newExp - clan.spirit().energy()));
        plugin.getClanManager().updateClanAsync(clan).thenRun(() -> {
            logHistory(clan.id(), reason, amount);
        });
    }

    private void logHistory(UUID clanId, String action, long amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseManager().dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_spirit_history (clan_id, action, amount, timestamp) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, clanId.toString());
                ps.setString(2, action);
                ps.setLong(3, amount);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to log spirit history", e);
            }
        });
    }

    public long getExpForNextLevel(int level) {
        return level * 1000L;
    }

    public CompletableFuture<List<SpiritHistoryEntry>> getHistoryAsync(UUID clanId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<SpiritHistoryEntry> entries = new ArrayList<>();
            try (Connection connection = plugin.getDatabaseManager().dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "SELECT action, amount, timestamp FROM clan_spirit_history WHERE clan_id = ? ORDER BY timestamp DESC LIMIT ?")) {
                ps.setString(1, clanId.toString());
                ps.setInt(2, limit);
                try (ResultSet result = ps.executeQuery()) {
                    while (result.next()) {
                        entries.add(new SpiritHistoryEntry(
                                result.getString("action"),
                                result.getLong("amount"),
                                result.getLong("timestamp")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to load spirit history", exception);
            }
            return entries;
        }, plugin.getDatabaseManager().executor());
    }

    private void tick() {
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            int onlineCount = 0;
            for (UUID memberId : clan.members().keySet()) {
                if (Bukkit.getPlayer(memberId) != null) {
                    onlineCount++;
                }
            }

            if (onlineCount > 0) {
                long newOnlineTime = clan.spirit().onlineTimeWeekly() + 6000L;
                clan.setSpirit(clan.spirit().withOnlineTimeWeekly(newOnlineTime));

                int effectivePlayers = Math.min(6, onlineCount);
                double expPerTick = (12.0 / 600.0) * effectivePlayers;
                if (Math.random() < expPerTick) {
                    addSpiritExperience(clan, 1, "Онлайн: " + effectivePlayers + " игр.");
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<Clan> playerClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            if (playerClan.isEmpty()) {
                removeSpeedAttribute(player);
                continue;
            }

            Clan clan = playerClan.get();
            int spiritLevel = clan.spirit().level();

            if (spiritLevel < 1) {
                removeSpeedAttribute(player);
                continue;
            }

            Optional<Clan> territoryClan = plugin.getClanManager().getClanAt(player.getLocation());
            boolean onOwnTerritory = territoryClan.isPresent() && territoryClan.get().id().equals(clan.id());
            boolean invalidWorld = player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL;

            if (!onOwnTerritory || invalidWorld) {
                removeSpeedAttribute(player);
                continue;
            }

            double multiplier = spiritLevel >= 9 ? 1.2 : 1.0;

            applySpeedAttribute(player, 0.05 * multiplier);

            if (spiritLevel >= 4) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, 0, true, false, false));
            }

            if (spiritLevel >= 6) {
                AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttribute != null) {
                    double currentHealth = player.getHealth();
                    double maxHealth = maxHealthAttribute.getValue();
                    if (currentHealth < maxHealth) {
                        player.setHealth(Math.min(maxHealth, currentHealth + 1.0));
                    }
                }
            }
        }
    }

    private void decayCheck() {
        long now = System.currentTimeMillis();
        long weekInMillis = 7L * 24L * 60L * 60L * 1000L;
        long targetOnlineTime = 20L * 60L * 60L * 1000L;

        for (Clan clan : plugin.getClanManager().getAllClans()) {
            if (now - clan.spirit().lastDecayCheck() > weekInMillis) {
                long decayAmount = 0;
                if (clan.spirit().onlineTimeWeekly() < targetOnlineTime) {
                    long energy = clan.spirit().energy();
                    decayAmount = (long) (energy * 0.08);
                }

                if (decayAmount > 0) {
                    clan.setSpirit(clan.spirit().addEnergy(-decayAmount).withLastDecayCheck(now).withOnlineTimeWeekly(0L));
                } else {
                    clan.setSpirit(clan.spirit().withLastDecayCheck(now).withOnlineTimeWeekly(0L));
                }

                long finalDecayAmount = decayAmount;
                plugin.getClanManager().updateClanAsync(clan).thenRun(() -> {
                    if (finalDecayAmount > 0) {
                        logHistory(clan.id(), "Упадок Духа (-8%)", -finalDecayAmount);
                    }
                });
            }
        }
    }

    private void applySpeedAttribute(Player player, double amount) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        NamespacedKey key = new NamespacedKey(plugin, SPEED_MODIFIER_NAME);

        for (AttributeModifier mod : attribute.getModifiers()) {
            if (mod.getKey().equals(key)) {
                if (mod.getAmount() == amount) return;
                attribute.removeModifier(mod);
                break;
            }
        }

        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR);
        attribute.addModifier(modifier);
    }

    private void removeSpeedAttribute(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        NamespacedKey key = new NamespacedKey(plugin, SPEED_MODIFIER_NAME);

        for (AttributeModifier mod : attribute.getModifiers()) {
            if (mod.getKey().equals(key)) {
                attribute.removeModifier(mod);
            }
        }
    }

    @EventHandler
    public void onClanClaim(ClanClaimEvent event) {
        long now = System.currentTimeMillis();
        long dayInMillis = 24L * 60L * 60L * 1000L;
        Clan clan = event.clan();

        Long last = lastClaimExp.get(clan.id());
        if (last == null || now - last > dayInMillis) {
            lastClaimExp.put(clan.id(), now);
            addSpiritExperience(clan, 120, "Захват территории");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        Optional<Clan> killerClanOpt = plugin.getClanManager().getPlayerClan(killer.getUniqueId());
        Optional<Clan> victimClanOpt = plugin.getClanManager().getPlayerClan(victim.getUniqueId());

        if (killerClanOpt.isPresent() && victimClanOpt.isPresent() && !killerClanOpt.get().id().equals(victimClanOpt.get().id())) {
            Clan killerClan = killerClanOpt.get();
            Optional<Clan> territoryClanOpt = plugin.getClanManager().getClanAt(killer.getLocation());

            if (territoryClanOpt.isPresent() && territoryClanOpt.get().id().equals(killerClan.id())) {
                long now = System.currentTimeMillis();
                long hourInMillis = 60L * 60L * 1000L;

                Long lastReset = lastKillReset.getOrDefault(killerClan.id(), 0L);
                if (now - lastReset > hourInMillis) {
                    hourlyKills.put(killerClan.id(), 0);
                    lastKillReset.put(killerClan.id(), now);
                }

                int kills = hourlyKills.getOrDefault(killerClan.id(), 0);
                if (kills < 8) {
                    hourlyKills.put(killerClan.id(), kills + 1);
                    addSpiritExperience(killerClan, 18, "Защита территории (убийство)");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
            if (clan.spirit().level() >= 2) {
                Optional<Clan> territoryClan = plugin.getClanManager().getClanAt(player.getLocation());
                if (territoryClan.isPresent() && territoryClan.get().id().equals(clan.id())) {
                    double multiplier = clan.spirit().level() >= 9 ? 1.2 : 1.0;
                    double bonus = 0.07 * multiplier;
                    event.setAmount((int) (event.getAmount() * (1.0 + bonus)));
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
            int level = clan.spirit().level();
            if (level < 8) return;

            Optional<Clan> territoryClan = plugin.getClanManager().getClanAt(player.getLocation());
            if (territoryClan.isPresent() && territoryClan.get().id().equals(clan.id())) {
                double multiplier = level >= 9 ? 1.2 : 1.0;

                if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    double reduction = 0.15 * multiplier;
                    event.setDamage(event.getDamage() * (1.0 - reduction));
                } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    double reduction = 0.10 * multiplier;
                    event.setDamage(event.getDamage() * (1.0 - reduction));
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && !(event.getDamager() instanceof Player)) {
            if (victim.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
            plugin.getClanManager().getPlayerClan(victim.getUniqueId()).ifPresent(clan -> {
                int level = clan.spirit().level();
                if (level >= 3) {
                    Optional<Clan> territoryClan = plugin.getClanManager().getClanAt(victim.getLocation());
                    if (territoryClan.isPresent() && territoryClan.get().id().equals(clan.id())) {
                        double multiplier = level >= 9 ? 1.2 : 1.0;
                        double reduction = 0.08 * multiplier;
                        event.setDamage(event.getDamage() * (1.0 - reduction));
                    }
                }
            });
        }

        if (event.getDamager() instanceof Player attacker) {
            if (attacker.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
            plugin.getClanManager().getPlayerClan(attacker.getUniqueId()).ifPresent(clan -> {
                int level = clan.spirit().level();

                Optional<Clan> territoryClanOpt = plugin.getClanManager().getClanAt(attacker.getLocation());
                if (territoryClanOpt.isEmpty() || !territoryClanOpt.get().id().equals(clan.id())) return;

                double multiplier = level >= 9 ? 1.2 : 1.0;

                if (!(event.getEntity() instanceof Player) && level >= 5) {
                    double bonus = 0.12 * multiplier;
                    event.setDamage(event.getDamage() * (1.0 + bonus));
                }

                if (event.getEntity() instanceof Player victim && level >= 7) {
                    Optional<Clan> victimClan = plugin.getClanManager().getPlayerClan(victim.getUniqueId());
                    if (victimClan.isEmpty() || !victimClan.get().id().equals(clan.id())) {
                        boolean isHome = false;
                        Optional<ClanTerritory> firstTerritory = clan.territories().stream().findFirst();
                        if (firstTerritory.isPresent()) {
                            ClanTerritory home = firstTerritory.get();
                            if (home.bannerX() != null) {
                                BoundingBox box = new BoundingBox(home.bannerX() - 12, attacker.getWorld().getMinHeight(), home.bannerZ() - 12, home.bannerX() + 12, attacker.getWorld().getMaxHeight(), home.bannerZ() + 12);
                                isHome = box.contains(attacker.getLocation().toVector());
                            } else {
                                isHome = home.key().chunkX() == attacker.getLocation().getChunk().getX() && home.key().chunkZ() == attacker.getLocation().getChunk().getZ();
                            }
                        }

                        if (isHome) {
                            double bonus = 0.15 * multiplier;
                            event.setDamage(event.getDamage() * (1.0 + bonus));
                        }
                    }
                }
            });
        }
    }
}