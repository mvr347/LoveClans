package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanClaimEvent;
import me.lovelace.loveclans.api.events.ClanConflictResolvedEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.spirit.DefeatPowerLoss;
import me.lovelace.loveclans.model.spirit.MasteryLedger;
import me.lovelace.loveclans.model.spirit.SpiritBonus;
import me.lovelace.loveclans.model.spirit.SpiritBonusFormula;
import me.lovelace.loveclans.model.spirit.SpiritBonusFormula.Side;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The clan spirit: Power (levels 0-10, fed by activity, fading when the clan is idle) and Mastery (-100 defence ..
 * +100 offence, moved only by actions). Bonuses need both and apply only in PvP against other clans. All maths lives in
 * the pure classes of {@code model.spirit}; this class wires them to events, config and storage.
 */
public final class SpiritManager implements Listener {
    private static final int MAX_POWER = 10;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final String KNOCKBACK_KEY = "clans_spirit_kb";
    private static final String LEGACY_PREFIX = "clans_spirit_";

    private final LoveClansPlugin plugin;
    private final SpiritSettings settings;
    private BukkitTask task;
    private BukkitTask decayTask;

    private final Map<UUID, Long> lastClaimExp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hourlyKills = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastKillReset = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pursuitReadyAt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> damageTaken = new ConcurrentHashMap<>();

    public SpiritManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.settings = new SpiritSettings(plugin::getConfig);
    }

    public SpiritSettings settings() {
        return settings;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("mechanics.spirit.enabled", true)) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            removeLegacyModifiers(online);
        }
        long period = 20L * settings.tickSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        long decayPeriod = Math.max(20L * 60L, Math.round(settings.decayIntervalHours() * 72_000L));
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::decayCheck, decayPeriod, decayPeriod);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (decayTask != null) decayTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            setKnockback(player, 0.0);
        }
    }

    public void purgeClan(UUID clanId) {
        lastClaimExp.remove(clanId);
        hourlyKills.remove(clanId);
        lastKillReset.remove(clanId);
        damageTaken.remove(clanId);
    }

    // --- Power ---

    /** Experience needed to leave {@code level}; level 0 costs the same as level 1 so a drained spirit can recover. */
    public long getExpForNextLevel(int level) {
        return Math.max(1, level) * 3000L;
    }

    public void addSpiritExperience(Clan clan, long amount, String reason) {
        int currentLevel = clan.spirit().level();
        if (currentLevel >= MAX_POWER || amount <= 0) return;

        long newExp = clan.spirit().energy() + amount;
        int newLevel = currentLevel;
        while (newLevel < MAX_POWER && newExp >= getExpForNextLevel(newLevel)) {
            newExp -= getExpForNextLevel(newLevel);
            newLevel++;
            plugin.getLogger().info("Clan " + clan.tag() + " spirit power rose to " + newLevel + "!");
        }
        if (newLevel >= MAX_POWER) newExp = 0L;
        boolean leveled = newLevel > currentLevel;

        clan.setSpirit(clan.spirit().withLevel(newLevel).withEnergy(newExp));
        if (leveled) {
            notifyLevelChange(clan, newLevel, true);
            plugin.getSoundService().playClan(clan, "spirit-level-up");
        }
        plugin.getClanManager().updateClanAsync(clan).thenRun(() -> logHistory(clan.id(), reason, amount));
    }

    private void notifyLevelChange(Clan clan, int level, boolean increased) {
        for (UUID memberId : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                plugin.getMessages().sendClickableSpiritLevelChange(p, level, increased);
            }
        }
    }

    // --- Activity (drives how fast an idle spirit fades) ---

    public void recordActivity(Clan clan, double points) {
        if (points <= 0) return;
        long now = System.currentTimeMillis();
        ClanSpirit spirit = clan.spirit();
        double decayed = SpiritDecayPolicy.decayedScore(spirit.activityScore(), spirit.activityAt(), now, settings.activityHalfLifeHours());
        clan.setSpirit(spirit.withActivity(decayed + points, now));
    }

    /** Current activity score with the decay since the last event applied. */
    public double activityScore(Clan clan) {
        ClanSpirit spirit = clan.spirit();
        return SpiritDecayPolicy.decayedScore(spirit.activityScore(), spirit.activityAt(), System.currentTimeMillis(), settings.activityHalfLifeHours());
    }

    public SpiritDecayPolicy.Tier activityTier(Clan clan) {
        return SpiritDecayPolicy.tier(activityScore(clan), isIdle(clan, System.currentTimeMillis()), settings.decay());
    }

    private boolean isIdle(Clan clan, long now) {
        long window = settings.noneAfterDays() * DAY_MS;
        for (ClanMember member : clan.members().values()) {
            if (Bukkit.getPlayer(member.playerId()) != null || now - member.lastSeen() <= window) {
                return false;
            }
        }
        return true;
    }

    // --- Mastery ---

    /** Positive = offence, negative = defence; capped per side per day. */
    public void addMastery(Clan clan, int delta, String reason) {
        if (delta == 0) return;
        MasteryLedger.State before = clan.spirit().masteryState();
        MasteryLedger.State after = MasteryLedger.apply(before, delta, today(), settings.masteryCaps());
        if (after.equals(before)) return;
        clan.setSpirit(clan.spirit().withMastery(after));
        plugin.getClanManager().updateClanAsync(clan).thenRun(() -> logHistory(clan.id(), reason, after.mastery() - before.mastery()));
    }

    private static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    // --- Bonuses ---

    public int bonusStep(Clan clan) {
        return SpiritBonusFormula.step(clan.spirit().level(), clan.spirit().mastery());
    }

    public Side side(Clan clan) {
        return SpiritBonusFormula.side(clan.spirit().mastery());
    }

    public boolean overloaded(Clan clan) {
        return SpiritBonusFormula.overloaded(clan.spirit().mastery(), settings.overloadThreshold());
    }

    /** Value of a bonus for this clan right now: 0 when it belongs to the other side or is still locked. */
    public double bonusValue(Clan clan, SpiritBonus bonus) {
        if (bonus.side() != side(clan)) return 0.0;
        return settings.value(bonus, bonusStep(clan));
    }

    private boolean onOwnLand(Player player, Clan clan) {
        Optional<Clan> at = plugin.getClanManager().getClanAt(player.getLocation());
        return at.isPresent() && at.get().id().equals(clan.id());
    }

    private void tick() {
        double onlinePoints = settings.activityPoints("online-tick", 0.05);
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            for (UUID memberId : clan.members().keySet()) {
                if (Bukkit.getPlayer(memberId) != null && !plugin.getAfkManager().isAfk(memberId)) {
                    recordActivity(clan, onlinePoints);
                    break;
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            if (clanOpt.isEmpty()) {
                setKnockback(player, 0.0);
                continue;
            }
            Clan clan = clanOpt.get();
            boolean own = onOwnLand(player, clan);
            double roots = own ? bonusValue(clan, SpiritBonus.ROOTS) : 0.0;
            if (roots > 0 && player.getHealth() > 0) {
                double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
                if (player.getHealth() < max) {
                    player.setHealth(Math.min(max, player.getHealth() + roots * 2.0));
                }
            }
            setKnockback(player, own ? bonusValue(clan, SpiritBonus.UNYIELDING) / 100.0 : 0.0);
        }
    }

    // --- PvP bonuses ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        Optional<Clan> attackerClanOpt = plugin.getClanManager().getPlayerClan(attacker.getUniqueId());
        Optional<Clan> victimClanOpt = plugin.getClanManager().getPlayerClan(victim.getUniqueId());
        if (attackerClanOpt.isEmpty() || victimClanOpt.isEmpty()) return;
        Clan attackerClan = attackerClanOpt.get();
        Clan victimClan = victimClanOpt.get();
        if (attackerClan.id().equals(victimClan.id())) return;

        double damage = event.getDamage();
        boolean victimOnOwnLand = onOwnLand(victim, victimClan);

        // Attacking side
        damage *= 1.0 + bonusValue(attackerClan, SpiritBonus.PRESSURE) / 100.0;
        if (overloaded(attackerClan) && side(attackerClan) == Side.DEFENSE) {
            damage *= 1.0 - settings.overloadOutgoingPercent() / 100.0;
        }
        // Defending side
        damage *= 1.0 - bonusValue(victimClan, SpiritBonus.FORTITUDE) / 100.0;
        if (overloaded(victimClan) && side(victimClan) == Side.OFFENSE) {
            damage *= 1.0 + settings.overloadIncomingPercent() / 100.0;
        }
        // Development direction (Military) and guild rewards: one combined step, so the sources
        // cannot be applied twice or in a different order than the spirit bonuses above.
        var bonuses = plugin.getClanBonuses();
        boolean attackerOnOwnLand = onOwnLand(attacker, attackerClan);
        damage *= 1.0 + bonuses.damagePercent(attackerClan, attackerOnOwnLand) / 100.0;
        damage *= 1.0 - Math.min(80.0, bonuses.resistancePercent(victimClan, victimOnOwnLand)) / 100.0;
        damage = Math.max(0.0, damage);
        event.setDamage(damage);

        double bloodlust = bonusValue(attackerClan, SpiritBonus.BLOODLUST);
        if (bloodlust > 0) {
            double max = attacker.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(max, attacker.getHealth() + damage * bloodlust / 100.0));
        }
        double pursuit = bonusValue(attackerClan, SpiritBonus.PURSUIT);
        long now = System.currentTimeMillis();
        if (pursuit > 0 && now >= pursuitReadyAt.getOrDefault(attacker.getUniqueId(), 0L)) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) Math.round(pursuit * 20), 0, true, false, false));
            pursuitReadyAt.put(attacker.getUniqueId(), now + 10_000L);
        }
        double intimidate = bonusValue(attackerClan, SpiritBonus.INTIMIDATE);
        if (intimidate > 0 && Math.random() * 100.0 < intimidate) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false, false));
        }

        if (victimOnOwnLand) {
            double reflect = bonusValue(victimClan, SpiritBonus.RETRIBUTION);
            if (reflect > 0) {
                attacker.damage(damage * reflect / 100.0);
            }
            double perPoint = settings.damageTakenPerMasteryPoint();
            double total = damageTaken.merge(victimClan.id(), damage, Double::sum);
            int points = (int) (total / perPoint);
            if (points > 0) {
                damageTaken.put(victimClan.id(), total - points * perPoint);
                addMastery(victimClan, -points * settings.masteryGain("damage-taken", 1), "Владение: урон на своей земле");
            }
        }
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    // --- Power / mastery sources ---

    @EventHandler
    public void onClanClaim(ClanClaimEvent event) {
        long now = System.currentTimeMillis();
        Clan clan = event.clan();
        Long last = lastClaimExp.get(clan.id());
        if (last == null || now - last > DAY_MS) {
            lastClaimExp.put(clan.id(), now);
            addSpiritExperience(clan, settings.claimExp(), "Захват территории");
            recordActivity(clan, settings.activityPoints("claim", 10));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        Optional<Clan> killerClanOpt = plugin.getClanManager().getPlayerClan(killer.getUniqueId());
        Optional<Clan> victimClanOpt = plugin.getClanManager().getPlayerClan(victim.getUniqueId());
        if (killerClanOpt.isEmpty() || victimClanOpt.isEmpty() || killerClanOpt.get().id().equals(victimClanOpt.get().id())) {
            return;
        }
        Clan killerClan = killerClanOpt.get();
        Clan victimClan = victimClanOpt.get();
        if (killerClan.relationTo(victimClan.id()) == DiplomacyRelation.ALLY) return;

        recordActivity(killerClan, settings.activityPoints("kill", 3));
        addMastery(killerClan, settings.masteryGain("kill", 2), "Владение: убийство врага");

        if (onOwnLand(killer, killerClan)) {
            long now = System.currentTimeMillis();
            long lastReset = lastKillReset.getOrDefault(killerClan.id(), 0L);
            if (now - lastReset > 60L * 60L * 1000L) {
                hourlyKills.put(killerClan.id(), 0);
                lastKillReset.put(killerClan.id(), now);
            }
            int kills = hourlyKills.getOrDefault(killerClan.id(), 0);
            if (kills < settings.killsPerHour()) {
                hourlyKills.put(killerClan.id(), kills + 1);
                addSpiritExperience(killerClan, settings.killExp(), "Защита территории (убийство)");
            }
        }
    }

    @EventHandler
    public void onConflictResolved(ClanConflictResolvedEvent event) {
        // Everyone who took part feeds their clan's spirit, up to a cap so a mass brawl is not a farm.
        Map<UUID, Integer> perClan = new HashMap<>();
        event.participants().values().forEach(clanId -> perClan.merge(clanId, 1, Integer::sum));
        perClan.forEach((clanId, count) -> plugin.getClanManager().getClanById(clanId).ifPresent(clan -> {
            int counted = Math.min(count, settings.participationCap());
            addSpiritExperience(clan, settings.participationExp() * counted, "Участие в конфликте");
            recordActivity(clan, settings.activityPoints("conflict", 15));
        }));

        UUID winnerId = event.winnerClanId();
        UUID loserId = event.loserClanId();
        if (winnerId == null || loserId == null) return;
        String kind = event.kind().name().toLowerCase(java.util.Locale.ROOT);
        boolean attackerWon = winnerId.equals(event.attackerClanId());
        plugin.getClanManager().getClanById(winnerId).ifPresent(winner -> {
            if (attackerWon) {
                addMastery(winner, settings.masteryGain(kind + "-win-attacker", defaultOffenceGain(event.kind())), "Владение: победа в нападении");
            } else {
                addMastery(winner, -settings.masteryGain(kind + "-defended", defaultOffenceGain(event.kind())), "Владение: отражённое нападение");
            }
        });
        if (event.kind() != ConflictKind.RAID) {
            plugin.getClanManager().getClanById(loserId).ifPresent(loser -> applyDefeat(loser, event.margin()));
        }
    }

    private static int defaultOffenceGain(ConflictKind kind) {
        return kind == ConflictKind.WAR ? 6 : 8;
    }

    private void applyDefeat(Clan loser, double margin) {
        int power = loser.spirit().level();
        int loss = DefeatPowerLoss.compute(power, margin, settings.defeat());
        if (loss <= 0) return;
        int newLevel = Math.max(settings.decay().minPowerLevel(), power - loss);
        if (newLevel == power) return;
        long energyCap = Math.max(0L, getExpForNextLevel(newLevel) - 1L);
        loser.setSpirit(loser.spirit().withLevel(newLevel).withEnergy(Math.min(loser.spirit().energy(), energyCap)));
        notifyLevelChange(loser, newLevel, false);
        plugin.getSoundService().playClan(loser, "spirit-power-loss");
        plugin.getClanManager().updateClanAsync(loser).thenRun(() -> logHistory(loser.id(), "Поражение: потеря Силы", -loss));
    }

    // --- Decay ---

    private void decayCheck() {
        long now = System.currentTimeMillis();
        SpiritDecayPolicy.Config cfg = settings.decay();
        long today = today();
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            ClanSpirit spirit = clan.spirit();
            if (spirit.activityAt() == 0L) {
                // First run after the upgrade: start at the "medium" line so nobody is punished for a missing history.
                clan.setSpirit(spirit.withActivity(cfg.medium().minScore(), now).withLastDecayCheck(now));
                plugin.getClanManager().updateClanAsync(clan);
                continue;
            }
            // A long server downtime must not burn the spirit: at most one day of decay per check.
            double hours = Math.min(24.0, Math.max(0.0, (now - spirit.lastDecayCheck()) / 3_600_000.0));
            double score = SpiritDecayPolicy.decayedScore(spirit.activityScore(), spirit.activityAt(), now, settings.activityHalfLifeHours());
            SpiritDecayPolicy.Tier tier = SpiritDecayPolicy.tier(score, isIdle(clan, now), cfg);
            boolean newDay = spirit.masteryDay() != today;

            SpiritDecayPolicy.Result result = SpiritDecayPolicy.apply(
                    new SpiritDecayPolicy.State(spirit.level(), spirit.energy(), spirit.lossProgress(), spirit.mastery()),
                    tier, hours, newDay, cfg, this::getExpForNextLevel);
            ClanSpirit updated = spirit.withDecay(result.state()).withActivity(score, now).withLastDecayCheck(now);
            if (newDay) {
                updated = updated.withMastery(new MasteryLedger.State(updated.mastery(), today, 0, 0));
            }
            boolean notify = result.levelLost() && settings.notifyOnPowerLoss() && now - spirit.lastLossNotice() > DAY_MS;
            if (notify) {
                updated = updated.withLastLossNotice(now);
            }
            clan.setSpirit(updated);
            if (result.levelLost()) {
                int level = updated.level();
                if (notify) notifyLevelChange(clan, level, false);
                plugin.getSoundService().playClan(clan, "spirit-power-loss");
                logHistory(clan.id(), "Упадок: потеря Силы", -1);
            }
            plugin.getClanManager().updateClanAsync(clan);
        }
    }

    // --- Player modifiers ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeLegacyModifiers(event.getPlayer());
    }

    private void setKnockback(Player player, double amount) {
        AttributeInstance attribute = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attribute == null) return;
        NamespacedKey key = new NamespacedKey(plugin, KNOCKBACK_KEY);
        AttributeModifier current = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getKey().equals(key)) current = modifier;
        }
        if (current != null) {
            if (amount > 0 && Math.abs(current.getAmount() - amount) < 1e-9) return;
            attribute.removeModifier(current);
        }
        if (amount > 0) {
            attribute.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /**
     * The old level ladder and abilities stored their attribute modifiers on the player; they outlive the plugin code
     * that applied them, so strip every {@code clans_spirit_*} modifier except the knockback one still in use.
     */
    private void removeLegacyModifiers(Player player) {
        for (Attribute attribute : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED, Attribute.ARMOR, Attribute.MAX_HEALTH}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : java.util.List.copyOf(instance.getModifiers())) {
                NamespacedKey key = modifier.getKey();
                if (key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))
                        && key.getKey().startsWith(LEGACY_PREFIX) && !key.getKey().equals(KNOCKBACK_KEY)) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    // --- History ---

    private void logHistory(UUID clanId, String action, long amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getClanManager().getClanById(clanId).isEmpty()) {
                return; // rollback / race — не писать history для удалённого или несохранённого клана
            }
            try (Connection connection = plugin.getDatabaseManager().dataSource().getConnection();
                 PreparedStatement ps = connection.prepareStatement("INSERT INTO clan_spirit_history (clan_id, action, amount, timestamp) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, clanId.toString());
                ps.setString(2, action);
                ps.setLong(3, amount);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                String msg = e.getMessage();
                if (msg != null && msg.toUpperCase().contains("FOREIGN KEY")) {
                    plugin.getLogger().fine("Skip spirit history for missing clan " + clanId);
                    return;
                }
                plugin.getLogger().log(Level.WARNING, "Failed to log spirit history", e);
            }
        });
    }

    /** For the GUI: every bonus of the clan's current side with its value now and at the next step. */
    public Map<SpiritBonus, double[]> bonusPreview(Clan clan) {
        Map<SpiritBonus, double[]> result = new EnumMap<>(SpiritBonus.class);
        Side side = side(clan);
        int step = bonusStep(clan);
        for (SpiritBonus bonus : SpiritBonus.of(side == Side.NONE ? Side.OFFENSE : side)) {
            result.put(bonus, new double[]{settings.value(bonus, side == Side.NONE ? 0 : step),
                    settings.value(bonus, Math.min(SpiritBonusFormula.MAX_STEP, (side == Side.NONE ? 0 : step) + 1))});
        }
        return result;
    }
}
