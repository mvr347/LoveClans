package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.siege.ClanSiege;
import me.lovelace.loveclans.model.siege.SiegeCamp;
import me.lovelace.loveclans.model.siege.SiegeResult;
import me.lovelace.loveclans.model.siege.SiegeState;
import me.lovelace.loveclans.util.ClanItemFactory;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.CoinFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер осад кланов (newsieges.md & siege-justify-rewards.md).
 * Включает:
 * - Требования Casus Belli, уровней кланов (atk >= 5, def >= 4, gap <= 8), онлайн с фильтром AFK > 15м
 * - Фазы PREPARING (15 мин) и ACTIVE (22 мин)
 * - Осадные лагеря с укреплением (GUI / клик / команда) и защитой от замуровывания
 * - Таймер удержания (10 мин): пауза при сносе лагеря, возобновление при респавне, сброс при полном вайпе лагерей > 30с
 * - Босс-бар удержания и состояния лагерей, боевые компасы, партиклы и звуки
 * - Мгновенный трофей (~25% казны + предметы), 3-дневные репарации, дебафф siege_pressure, бафф siege_repelled
 */
public final class SiegeManager {
    private final LoveClansPlugin plugin;
  private final me.lovelace.loveclans.siege.SiegeRepository repository;
  private final me.lovelace.loveclans.siege.SiegeScenery scenery;
  private final Set<UUID> pending = new HashSet<>();
  private final Set<UUID> checkpointing = new HashSet<>();
  private final Map<UUID, Long> revisions = new HashMap<>();
  private final Map<UUID, String> outcomes = new ConcurrentHashMap<>();
  private final Set<UUID> storagePaused = new HashSet<>();
  private final Set<UUID> settled = new HashSet<>();
  private long previousTick = System.currentTimeMillis();
  private final Map<UUID, Long> fees = new HashMap<>();
  private final Map<UUID, String> refundScrolls = new HashMap<>();
  private long parcelCheck;
  private boolean parcelBusy;
  private final Map<UUID, UpgradeOperation> upgrades = new HashMap<>();
  private boolean closing;
  private boolean ready;

    private final Map<UUID, ClanSiege> activeSieges = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> siegeCooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, BossBar> pendingBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private final Map<UUID, Long> holdProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Long> allBrokenSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHoldTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCompassTick = new ConcurrentHashMap<>();

    private final Set<UUID> oneMinuteWarned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> holdHalfWarned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> holdNearWarned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> wipedWarned = ConcurrentHashMap.newKeySet();

    /** Уровень укрепления лагерей: id осады -> индекс лагеря -> уровень. */
    private final Map<UUID, Map<Integer, Integer>> campFortification = new ConcurrentHashMap<>();
    /** Накопленные удары по лагерю: id осады -> индекс лагеря -> удары. */
    private final Map<UUID, Map<Integer, Integer>> campHits = new ConcurrentHashMap<>();

    public SiegeManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    this.repository =
        new me.lovelace.loveclans.siege.SiegeRepository(plugin.getDatabaseManager().dataSource());
    this.scenery = new me.lovelace.loveclans.siege.SiegeScenery(plugin);
    }

    public void loadCooldowns() {
        plugin.getConflictCooldownStore().loadInto(me.lovelace.loveclans.storage.ConflictCooldownStore.SIEGE, siegeCooldowns);
    }

    private Duration preStartDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.pre-start-minutes", 15));
    }

    private Duration siegeDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.duration-minutes", 22));
    }

    /** The attacker's own bonuses do not change how long the siege lasts any more (only its cooldown does). */
    private Duration siegeDuration(Clan attacker) {
        return siegeDuration();
    }

    private Duration campHoldDuration() {
        long holdMinutes = plugin.getConfig().getLong("siege.camp-hold-minutes", 10);
        long totalMinutes = plugin.getConfig().getLong("siege.duration-minutes", 22);
        return Duration.ofMinutes(Math.max(1, Math.min(holdMinutes, totalMinutes - 1)));
    }

    /** A tier III Military clan is harder to besiege: the attackers must hold the camps longer. */
    private Duration campHoldDuration(UUID defenderClanId) {
        double multiplier = plugin.getClanManager().getClanById(defenderClanId)
                .map(plugin.getClanBonuses()::siegeHoldMultiplier).orElse(1.0D);
        return Duration.ofMillis(Math.round(campHoldDuration().toMillis() * multiplier));
    }

    private Duration campRespawnDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.camp-respawn-minutes", 2));
    }

    private Duration campWipeResetDuration() {
        return Duration.ofSeconds(plugin.getConfig().getLong("siege.camp-wipe-reset-seconds", 30));
    }

    private Duration cooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("siege.cooldown-hours", 36));
    }

    /** The cooldown as the attacking clan sees it: the Military direction shortens it. */
    private Duration cooldownDuration(Clan attacker) {
        double reduction = Math.min(90.0D, plugin.getClanBonuses().cooldownReductionPercent(attacker));
        return Duration.ofMillis(Math.round(cooldownDuration().toMillis() * (1.0D - reduction / 100.0D)));
    }

    public int hitsRequired(UUID siegeId, int campIndex) {
        int perLevel = Math.max(1, plugin.getConfig().getInt("siege.fortification.hits-per-level", 1));
        return 1 + fortificationLevel(siegeId, campIndex) * perLevel;
    }

    public int fortificationLevel(UUID siegeId, int campIndex) {
        return campFortification.getOrDefault(siegeId, Map.of()).getOrDefault(campIndex, 0);
    }

    /** What the next fortification level of this camp costs; the GUI and the command both charge exactly this. */
    public long fortifyCost(UUID siegeId, int campIndex) {
        long perLevel = plugin.getConfig().getLong("siege.fortification.cost-per-level", 200L);
        return perLevel * (fortificationLevel(siegeId, campIndex) + 1L);
    }

    /** True when {@link #fortifyCamp} would succeed: siege running, camp standing, level below the maximum. */
    public boolean canFortify(UUID siegeId, int campIndex) {
        ClanSiege siege = activeSieges.get(siegeId);
    if (closing
        || outcomes.containsKey(siegeId)
        || pending.contains(siegeId)
        || storagePaused.contains(siegeId)
        || siege == null
        || siege.state() != SiegeState.ACTIVE
        || campIndex < 0
        || campIndex >= siege.camps().size()) {
            return false;
        }
        if (siege.camps().get(campIndex).broken()) {
            return false;
        }
    int max = maxFortificationLevel();
        return fortificationLevel(siegeId, campIndex) < max;
    }

    public boolean fortifyCamp(UUID siegeId, int campIndex) {
        ClanSiege siege = activeSieges.get(siegeId);
        if (!canFortify(siegeId, campIndex)) {
            return false;
        }
    int max = maxFortificationLevel();
        Map<Integer, Integer> levels = campFortification.computeIfAbsent(siegeId, id -> new ConcurrentHashMap<>());
        int current = levels.getOrDefault(campIndex, 0);
        if (current >= max) {
            return false;
        }
    var plan = scenery.site(siegeId, campIndex);
    if (current + 1 == 4 && !scenery.towerAvailable()) return false;
    if (plan != null && !scenery.loaded(plan)) return false;
        levels.put(campIndex, current + 1);
    if (plan != null) scenery.render(plan, current + 1, false);
    checkpoint(siegeId);
        return true;
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID clan1, UUID clan2) {
        return clan1.compareTo(clan2) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(clan1, clan2)
                : new AbstractMap.SimpleImmutableEntry<>(clan2, clan1);
    }

    public CompletableFuture<ClanSiege> startSiegeAsync(Clan attacker, Clan defender, TerritoryKey territoryKey) {
        return startSiegeAsync(attacker, defender, territoryKey, false);
    }

    public CompletableFuture<ClanSiege> startSiegeAsync(Clan attacker, Clan defender, TerritoryKey territoryKey, boolean force) {
        if (force) {
      return plugin
          .supplySync(() -> startSiegeInternal(attacker, defender, territoryKey, true))
          .thenCompose(this::persistDeclaration);
        }

    // Асинхронно проверяем недельные лимиты осад из архива
    return plugin
        .getConflictArchive()
        .historyOf(attacker.id(), 50)
        .thenCompose(
            atkHistory ->
                plugin
                    .getConflictArchive()
                    .historyOf(defender.id(), 50)
                    .thenCompose(
                        defHistory ->
                            plugin.supplySync(
                                () -> {
                                  long now = System.currentTimeMillis();
                                  long weekAgo = now - 7L * 24 * 3600_000L;
                                  int maxAttacks =
                                      plugin
                                          .getConfig()
                                          .getInt("siege.max-sieges-per-clan-per-week", 2);
                                  int maxDefends =
                                      plugin
                                          .getConfig()
                                          .getInt("siege.max-times-sieged-per-week", 2);

                                  long recentAttacks =
                                      atkHistory.stream()
                                          .filter(
                                              r ->
                                                  r.kind() == ConflictKind.SIEGE
                                                      && r.attackerClanId().equals(attacker.id())
                                                      && r.endedAt() >= weekAgo)
                                          .count();
                                  if (recentAttacks >= maxAttacks) {
                                    throw new IllegalStateException("siege.weekly-attacker-limit");
                                  }

                                  long recentDefends =
                                      defHistory.stream()
                                          .filter(
                                              r ->
                                                  r.kind() == ConflictKind.SIEGE
                                                      && r.defenderClanId().equals(defender.id())
                                                      && r.endedAt() >= weekAgo)
                                          .count();
                                  if (recentDefends >= maxDefends) {
                                    throw new IllegalStateException("siege.weekly-defender-limit");
                                  }

                                  return startSiegeInternal(
                                      attacker, defender, territoryKey, false);
                                })))
        .thenCompose(this::persistDeclaration);
    }

    private ClanSiege startSiegeInternal(Clan attacker, Clan defender, TerritoryKey territoryKey, boolean force) {
        AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(attacker.id(), defender.id());
        long now = System.currentTimeMillis();

        if (attacker.id().equals(defender.id())) {
            throw new IllegalStateException("war.cannot-target-self");
        }
    if (!ready || closing) throw new IllegalStateException("siege.models.loading");
        if (!force) {
            if (!attacker.hasCapital()) {
                throw new IllegalStateException("siege.attacker-no-capital");
            }
            if (!defender.hasCapital()) {
                throw new IllegalStateException("siege.defender-no-capital");
            }
            if (activeSieges.size() >= plugin.getConfig().getInt("siege.max-concurrent", 3)) {
                throw new IllegalStateException("siege.max-sieges-reached");
            }
            if (isInSiege(attacker.id()) || isInSiege(defender.id())) {
                throw new IllegalStateException("siege.already-in-siege");
            }
            if (plugin.getWarManager().isAtWar(attacker.id()) || plugin.getWarManager().isAtWar(defender.id())) {
                throw new IllegalStateException("siege.war-in-progress");
            }
            if (plugin.getRaidManager().isInRaid(attacker.id()) || plugin.getRaidManager().isInRaid(defender.id())) {
                throw new IllegalStateException("raid.already-in-raid");
            }
            if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                throw new IllegalStateException("war.cannot-declare-on-ally");
            }
            if (plugin.getDiplomacyManager() != null && plugin.getDiplomacyManager().hasPact(attacker.id(), defender.id(), me.lovelace.loveclans.model.diplomacy.PactType.NAP)) {
                throw new IllegalStateException("deny.nap.active");
            }
            if (plugin.getDiplomacyManager() != null) {
                plugin.getDiplomacyManager().requireAllowed(me.lovelace.loveclans.manager.diplomacy.DiplomacyActions.Action.CONFLICT, attacker, defender);
            }
            if (plugin.getModifierManager() != null && plugin.getModifierManager().hasGuildShield(defender.id())) {
                throw new IllegalStateException("deny.guild-shield.active");
            }

            // Уровни кланов (siege-justify-rewards.md §1.2)
            int minAttackerLevel = plugin.getConfig().getInt("siege.min-attacker-clan-level", 5);
            int minDefenderLevel = plugin.getConfig().getInt("siege.min-defender-clan-level", 4);
            int maxLevelGap = plugin.getConfig().getInt("siege.level-gap-max", 8);

            if (attacker.level() < minAttackerLevel) {
                throw new IllegalStateException("siege.attacker-level-too-low");
            }
            if (defender.level() < minDefenderLevel) {
                throw new IllegalStateException("siege.defender-level-too-low");
            }
            if (attacker.level() - defender.level() > maxLevelGap) {
                throw new IllegalStateException("siege.level-gap-too-large");
            }

            Long lastSiegeTime = siegeCooldowns.get(cooldownKey);
            if (lastSiegeTime != null && (now - lastSiegeTime < cooldownDuration(attacker).toMillis())) {
                long remainingSeconds = (cooldownDuration(attacker).toMillis() - (now - lastSiegeTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            int afkIgnore = plugin.getConfig().getInt("siege.afk-ignore-minutes", 15);
            int attackerOnline = countActiveOnline(attacker, afkIgnore);
            int defenderOnline = countActiveOnline(defender, afkIgnore);
            if (attackerOnline < plugin.getConfig().getInt("siege.min-attacker-online", 2) + plugin.getClanBonuses().extraAttackersOnline(defender)) {
                throw new IllegalStateException("siege.not-enough-attackers");
            }
            if (defenderOnline < plugin.getConfig().getInt("siege.min-defender-online", 3)) {
                throw new IllegalStateException("siege.not-enough-defenders");
            }

            // Сбор с казны клана атакующего (siege-justify-rewards.md §1.1)
            long declareFee = plugin.getConfig().getLong("siege.declare-treasury-fee", 500L);
            if (declareFee > 0) {
                if (attacker.chestMoney() < declareFee) {
                    throw new IllegalStateException("siege.not-enough-treasury");
                }
                attacker.addChestMoney(-declareFee);
                plugin.getStorage().updateClanChestMoney(attacker.id(), attacker.chestMoney());
            }
        }

        ClanSiege siege = new ClanSiege(UUID.randomUUID(), attacker.id(), defender.id(), territoryKey,
                now, now + preStartDuration().toMillis(), SiegeState.PREPARING, List.of());
        activeSieges.put(siege.id(), siege);
    fees.put(
        siege.id(),
        force ? 0 : Math.max(0, plugin.getConfig().getLong("siege.declare-treasury-fee", 500)));
        siegeCooldowns.put(cooldownKey, now);
        plugin.getConflictCooldownStore().saveAsync(me.lovelace.loveclans.storage.ConflictCooldownStore.SIEGE, cooldownKey, now);

        beginPendingPhase(siege, attacker, defender);
        me.lovelace.loveclans.activity.ConflictEvents.declared(me.lovelace.loveclans.model.history.ConflictKind.SIEGE,
                siege.id(), attacker.id(), defender.id());
        return siege;
    }

    public int countActiveOnline(Clan clan, int afkIgnoreMinutes) {
        int online = 0;
        for (UUID memberId : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                if (afkIgnoreMinutes <= 0 || !plugin.getAfkManager().isAfkMinutes(memberId, afkIgnoreMinutes)) {
                    online++;
                }
            }
        }
        return online;
    }

    private record SiegeClans(Clan attacker, Clan defender) {}

    private Optional<SiegeClans> resolveClans(ClanSiege siege) {
        Optional<Clan> attacker = plugin.getClanManager().getClanById(siege.attackerClanId());
        Optional<Clan> defender = plugin.getClanManager().getClanById(siege.defenderClanId());
        if (attacker.isEmpty() || defender.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SiegeClans(attacker.get(), defender.get()));
    }

    private void beginPendingPhase(ClanSiege siege, Clan attacker, Clan defender) {
        String time = formatDuration(siege.endsAt() - System.currentTimeMillis());
        Component title = plugin.getMessages().component("siege.pending.bossbar", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(), "time", time));
        BossBar bar = BossBar.bossBar(title, 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        pendingBossBars.put(siege.id(), bar);

        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "siege.pending.declared",
                Map.of("tag", defender.tag(), "color", defender.tagColor(), "time", time)));
        onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "siege.pending.declared",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor(), "time", time)));
    }

    private void clearPendingPhase(UUID siegeId, SiegeClans clans) {
        BossBar bar = pendingBossBars.remove(siegeId);
        oneMinuteWarned.remove(siegeId);
        if (bar != null && clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> p.hideBossBar(bar));
        }
    }

    private void clearActiveBossBar(UUID siegeId, SiegeClans clans) {
        BossBar bar = activeBossBars.remove(siegeId);
        if (bar != null && clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> p.hideBossBar(bar));
        }
    }

    private void activateSiege(ClanSiege siege) {
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        clearPendingPhase(siege.id(), clansOpt.orElse(null));
        if (clansOpt.isEmpty()) {
            activeSieges.remove(siege.id());
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        Optional<ClanTerritory> territoryOpt = resolveContestedTerritory(siege);
        Location center = territoryOpt.map(this::territoryCenter).orElse(null);
        if (center == null) {
            plugin.getLogger().warning("Siege " + siege.id() + " has no resolvable territory - cancelling.");
            activeSieges.remove(siege.id());
            return;
        }

        long now = System.currentTimeMillis();
    List<SiegeCamp> camps;
    try {
      camps = spawnCamps(siege.id(), center, now);
    } catch (RuntimeException failure) {
      plugin.getLogger().warning("Осада не размещена: " + failure.getMessage());
      cancelPlacement(siege, attacker);
      return;
    }
        ClanSiege activated = siege.activate(now + siegeDuration(attacker).toMillis(), camps);
        activeSieges.put(activated.id(), activated);

        holdProgress.put(activated.id(), 0L);
        lastHoldTick.put(activated.id(), now);
        allBrokenSince.remove(activated.id());

    pending.add(activated.id());
    repository
        .save(snapshot(activated.id()))
        .whenComplete(
            (ignored, failure) ->
                onMain(
                    () -> {
                      pending.remove(activated.id());
                      if (failure != null) {
                        storageFailure(activated.id(), failure);
                        return;
                      }
                      if (!activeSieges.containsKey(activated.id())) return;
                      for (var plan : scenery.sites(activated.id())) scenery.render(plan, 0, false);
                      // Создаём активный BossBar
                      BossBar activeBar =
                          BossBar.bossBar(
                              formatActiveBossBarTitle(
                                  attacker,
                                  defender,
                                  camps.size(),
                                  camps.size(),
                                  false,
                                  0L,
                                  siegeDuration(attacker).toMillis()),
                              0.0f,
                              BossBar.Color.YELLOW,
                              BossBar.Overlay.PROGRESS);
                      activeBossBars.put(activated.id(), activeBar);
                      java.util.stream.Stream.concat(
                              onlineMembers(attacker), onlineMembers(defender))
                          .forEach(p -> p.showBossBar(activeBar));

                      // Раздаём компасы
                      distributeSiegeCompasses(activated, attacker, defender, center);

                      // Звуки и оповещения
                      onlineMembers(attacker)
                          .forEach(
                              p -> {
                                p.playSound(
                                    p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.2f, 1.0f);
                                plugin
                                    .getMessages()
                                    .sendImportantTitle(
                                        p,
                                        "siege.start.attacker-title",
                                        "siege.start.attacker-subtitle",
                                        Map.of(
                                            "tag", defender.tag(), "color", defender.tagColor()));
                              });
                      onlineMembers(defender)
                          .forEach(
                              p -> {
                                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 0.8f);
                                plugin
                                    .getMessages()
                                    .sendImportantTitle(
                                        p,
                                        "siege.start.defender-title",
                                        "siege.start.defender-subtitle",
                                        Map.of(
                                            "tag", attacker.tag(), "color", attacker.tagColor()));
                              });
                    }));
    }

    private Location territoryCenter(ClanTerritory territory) {
        World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            return null;
        }
        if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
            return new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());
        }
        var box = plugin.getAdvancedClaimsHook().boundingBoxOf(territory).orElse(null);
        if (box == null) {
            return null;
        }
        return new Location(world, box.getCenterX(), box.getCenterY(), box.getCenterZ());
    }

    private List<SiegeCamp> spawnCamps(UUID siegeId, Location center, long now) {
    int min = Math.max(1, plugin.getConfig().getInt("siege.camp-count-min", 3));
        int max = Math.max(min, plugin.getConfig().getInt("siege.camp-count-max", 4));
    int count = ThreadLocalRandom.current().nextInt(min, max + 1);
    int distance = Math.max(10, plugin.getConfig().getInt("siege.camp-distance-blocks", 50));
    var plans = scenery.plan(siegeId, center, count, distance, towerEnabled());
    plans.forEach(scenery::register);
    return plans.stream().map(me.lovelace.loveclans.siege.SiegeScenery.Plan::camp).toList();
    }

    private SiegeCamp placeCamp(UUID siegeId, int index, World world, int x, int y, int z, long standingSince) {
    var plan = scenery.site(siegeId, index);
    if (plan != null) scenery.render(plan, fortificationLevel(siegeId, index), false);
        return new SiegeCamp(index, world.getName(), x, y, z, standingSince, false, 0L);
    }

    private void removeCampBlock(SiegeCamp camp) {
        World world = Bukkit.getWorld(camp.world());
        if (world == null) {
            return;
        }
        var block = world.getBlockAt(camp.x(), camp.y(), camp.z());
        if (block.getType() == Material.CAMPFIRE) {
            block.setType(Material.AIR);
        }
    }

    public boolean breakCamp(UUID siegeId, int campIndex) {
        ClanSiege siege = activeSieges.get(siegeId);
    if (closing
        || outcomes.containsKey(siegeId)
        || pending.contains(siegeId)
        || storagePaused.contains(siegeId)
        || siege == null
        || siege.state() != SiegeState.ACTIVE
        || campIndex < 0
        || campIndex >= siege.camps().size()) {
            return false;
        }
        SiegeCamp camp = siege.camps().get(campIndex);
        if (camp.broken()) {
            return false;
        }

        int required = hitsRequired(siegeId, campIndex);
        Map<Integer, Integer> hits = campHits.computeIfAbsent(siegeId, id -> new ConcurrentHashMap<>());
        int accumulated = hits.merge(campIndex, 1, Integer::sum);
        if (accumulated < required) {
            resolveClans(siege).ifPresent(clans -> onlineMembers(clans.defender())
                    .forEach(p -> {
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                        plugin.getMessages().hud(p, Component.text("§eСнос лагеря #" + (campIndex + 1) + " · §f" + accumulated + "/" + required + " §eудара"));
                        plugin.getMessages().send(p, "siege.camp.weakened", Map.of(
                                "index", String.valueOf(campIndex + 1),
                                "hits", String.valueOf(accumulated),
                                "required", String.valueOf(required)));
                    }));
      checkpoint(siegeId);
            return true;
        }

        hits.remove(campIndex);
        // Сброс укрепления лагеря до 0 при сносе (newsieges.md §3.5)
        campFortification.computeIfPresent(siegeId, (k, map) -> {
            map.remove(campIndex);
            return map;
        });

        long now = System.currentTimeMillis();
        ClanSiege updated = siege.withCamp(camp.brokenNow(now, campRespawnDuration().toMillis()));
        activeSieges.put(siegeId, updated);
    var plan = scenery.site(siegeId, campIndex);
    if (plan != null) {
      scenery.evacuateIfSupported(plan);
      scenery.render(plan, 0, true);
    } else removeCampBlock(camp);
    checkpoint(siegeId);

        // Взрывной визуальный и аудио эффект сноса
        Location campLoc = camp.toLocation();
        if (campLoc != null && campLoc.getWorld() != null) {
            campLoc.getWorld().spawnParticle(Particle.EXPLOSION, campLoc, 1);
            campLoc.getWorld().playSound(campLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }

        resolveClans(siege).ifPresent(clans -> {
            onlineMembers(clans.attacker()).forEach(p -> {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
                plugin.getMessages().send(p, "siege.camp.broken-attacker", Map.of("index", String.valueOf(campIndex + 1)));
            });
            onlineMembers(clans.defender()).forEach(p -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                plugin.getMessages().send(p, "siege.camp.broken-defender", Map.of("index", String.valueOf(campIndex + 1)));
            });
        });

        int campScore = plugin.getConfig().getInt("war.objectives.camp-break-score", 3);
        if (campScore > 0) {
            plugin.getWarManager().addScore(siege.defenderClanId(), siege.attackerClanId(), campScore);
        }
        return true;
    }

    public Optional<ClanTerritory> resolveContestedTerritory(ClanSiege siege) {
        if (siege.contestedTerritory() == null) {
            return Optional.empty();
        }
        return plugin.getClanManager().getClanById(siege.defenderClanId())
                .flatMap(defender -> defender.territories().stream()
                        .filter(t -> t.key().equals(siege.contestedTerritory()))
                        .findFirst());
    }

    public record SiegeCampRef(UUID siegeId, UUID defenderClanId, int campIndex) {}

    public Optional<SiegeCampRef> findCampAt(Location location) {
        for (ClanSiege siege : activeSieges.values()) {
            if (siege.state() != SiegeState.ACTIVE) continue;
            for (SiegeCamp camp : siege.camps()) {
                if (!camp.broken() && camp.world().equals(location.getWorld().getName())
                        && camp.x() == location.getBlockX() && camp.y() == location.getBlockY() && camp.z() == location.getBlockZ()) {
                    return Optional.of(new SiegeCampRef(siege.id(), siege.defenderClanId(), camp.index()));
                }
            }
        }
        return Optional.empty();
    }

    public boolean isNearAnyCamp(Location loc, double radius) {
        if (loc == null || loc.getWorld() == null) return false;
        for (ClanSiege siege : activeSieges.values()) {
            if (siege.state() != SiegeState.ACTIVE) continue;
            for (SiegeCamp camp : siege.camps()) {
                if (!camp.broken() && camp.world().equals(loc.getWorld().getName())) {
                    double dx = Math.abs(camp.x() - loc.getBlockX());
                    double dy = Math.abs(camp.y() - loc.getBlockY());
                    double dz = Math.abs(camp.z() - loc.getBlockZ());
                    if (dx <= radius && dy <= radius && dz <= radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean areInSiege(UUID first, UUID second) {
        return activeSieges.values().stream().anyMatch(s -> s.between(first, second));
    }

    public boolean isInSiege(UUID clanId) {
        return activeSieges.values().stream().anyMatch(s -> s.involves(clanId));
    }

    public Optional<ClanSiege> findSiege(UUID first, UUID second) {
        return activeSieges.values().stream().filter(s -> s.between(first, second)).findFirst();
    }

    public Optional<ClanSiege> getActiveSiege(UUID clanId) {
        return activeSieges.values().stream().filter(s -> s.involves(clanId)).findFirst();
    }

    public Optional<ClanSiege> findSiegeById(UUID id) {
        return Optional.ofNullable(activeSieges.get(id));
    }

    public Optional<UUID> activeSiegeIdForAttacker(UUID clanId) {
        if (clanId == null) {
            return Optional.empty();
        }
        return activeSieges.values().stream()
                .filter(siege -> siege.state() == SiegeState.ACTIVE && clanId.equals(siege.attackerClanId()))
                .map(ClanSiege::id)
                .findFirst();
    }

    public Collection<ClanSiege> activeSieges() {
        return List.copyOf(activeSieges.values());
    }

    public CompletableFuture<Void> peaceAsync(Clan source, Clan target) {
        return plugin.supplySync(() -> {
            Optional<ClanSiege> siegeOpt = findSiege(source.id(), target.id());
            if (siegeOpt.isEmpty()) {
                throw new IllegalStateException("war.not-at-war");
            }
            endSiege(siegeOpt.get(), SiegeResult.CANCELLED);
            plugin.getDiplomacyManager().onConflictPeace(source, target);
            for (UUID memberId : source.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", target.tag(), "color", target.tagColor()));
            }
            for (UUID memberId : target.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", source.tag(), "color", source.tagColor()));
            }
            return null;
        });
    }

    private void endSiege(ClanSiege siege, SiegeResult result) {
    if (outcomes.containsKey(siege.id()) || pending.contains(siege.id())) return;
    outcomes.put(siege.id(), result.name());
    pending.add(siege.id());
    repository
        .save(snapshot(siege.id()))
        .whenComplete(
            (v, failure) ->
                onMain(
                    () -> {
                      pending.remove(siege.id());
                      if (failure != null) {
                        outcomes.remove(siege.id());
                        storageFailure(siege.id(), failure);
                        return;
                      }
                      settle(siege, result);
                    }));
  }

  private void finishWorld(ClanSiege siege) {
    scenery.remove(siege.id());
    clearPendingPhase(siege.id(), resolveClans(siege).orElse(null));
    clearActiveBossBar(siege.id(), resolveClans(siege).orElse(null));
        removeSiegeCompasses(siege);
    }

  private void settle(ClanSiege siege, SiegeResult result) {
    if (!pending.add(siege.id())) return;
    if (result == SiegeResult.CANCELLED || resolveClans(siege).isEmpty()) {
      pending.remove(siege.id());
      settled.add(siege.id());
      finishWorld(siege);
      plugin.getConflictParticipants().forget(siege.id());
      return;
        }
    var clans = resolveClans(siege).orElseThrow();
    double fraction =
        Math.min(
            1.0,
            holdProgress.getOrDefault(siege.id(), 0L)
                / (double) Math.max(1, campHoldDuration(siege.defenderClanId()).toMillis()));
    new me.lovelace.loveclans.siege.SiegeRewards(plugin, repository)
        .apply(siege, result, clans.attacker(), clans.defender())
        .whenComplete(
            (ignored, failure) ->
                onMain(
                    () -> {
                      pending.remove(siege.id());
                      if (failure != null) {
                        storageFailure(siege.id(), failure);
                        return;
                      }
                      settled.add(siege.id());
                      storagePaused.remove(siege.id());
                      finishWorld(siege);
                      repository
                          .once(siege.id(), "events", c -> null)
                          .whenComplete(
                              (first, error) ->
                                  onMain(
                                      () -> {
                                        if (error != null) {
                                          plugin
                                              .getLogger()
                                              .log(
                                                  java.util.logging.Level.WARNING,
                                                  "Не удалось записать уведомление осады",
                                                  error);
                                          return;
                                        }
                                        if (!first) return;
                                        me.lovelace.loveclans.activity.ConflictEvents.resolved(
                                            plugin,
                                            ConflictKind.SIEGE,
                                            siege.id(),
                                            siege.attackerClanId(),
                                            siege.defenderClanId(),
                                            result == SiegeResult.ATTACKER_WIN
                                                ? me.lovelace.loveclans.api.events.ConflictOutcome
                                                    .ATTACKER_WIN
                                                : me.lovelace.loveclans.api.events.ConflictOutcome
                                                    .DEFENDER_WIN,
                                            result == SiegeResult.ATTACKER_WIN ? 1 : 1 - fraction);
                                        Clan winner =
                                            result == SiegeResult.ATTACKER_WIN
                                                ? clans.attacker()
                                                : clans.defender();
                                        Clan loser =
                                            result == SiegeResult.ATTACKER_WIN
                                                ? clans.defender()
                                                : clans.attacker();
                                        plugin.getDiplomacyManager().onSiegeVictory(winner, loser);
                                        onlineMembers(winner)
                                            .forEach(
                                                p ->
                                                    plugin
                                                        .getMessages()
                                                        .sendImportantTitle(
                                                            p,
                                                            result == SiegeResult.ATTACKER_WIN
                                                                ? "siege.end.victory-title"
                                                                : "siege.end.repelled-title",
                                                            result == SiegeResult.ATTACKER_WIN
                                                                ? "siege.end.victory-subtitle"
                                                                : "siege.end.repelled-subtitle",
                                                            Map.of(
                                                                "tag",
                                                                loser.tag(),
                                                                "color",
                                                                loser.tagColor())));
                                        onlineMembers(loser)
                                            .forEach(
                                                p ->
                                                    plugin
                                                        .getMessages()
                                                        .sendImportantTitle(
                                                            p,
                                                            result == SiegeResult.ATTACKER_WIN
                                                                ? "siege.end.defeat-title"
                                                                : "siege.end.failed-title",
                                                            result == SiegeResult.ATTACKER_WIN
                                                                ? "siege.end.defeat-subtitle"
                                                                : "siege.end.failed-subtitle",
                                                            Map.of(
                                                                "tag",
                                                                winner.tag(),
                                                                "color",
                                                                winner.tagColor())));
                                      }));
                    }));
    }

    public void purgeClan(UUID clanId) {
        siegeCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
        plugin.getConflictCooldownStore().deleteClanAsync(clanId);
    }

    public void endActiveSiegesInvolvingClan(UUID clanId) {
        for (ClanSiege siege : activeSieges()) {
            if (!siege.involves(clanId)) continue;
            endSiege(siege, SiegeResult.CANCELLED);
        }
    }

    public void shutdown() {
    closing = true;
    for (UpgradeOperation op : List.copyOf(upgrades.values())) {
      Throwable failure = null;
      try {
        op.write.get(12, java.util.concurrent.TimeUnit.SECONDS);
      } catch (Throwable e) {
        failure = e;
      }
      if (op.write.isDone()) completeUpgrade(op, failure);
      else
        op.answer.completeExceptionally(
            new java.util.concurrent.CancellationException(
                "Сохранение будет проверено при запуске"));
    }
        for (ClanSiege siege : activeSieges()) {
      if (!upgrades.containsKey(siege.id())) repository.save(snapshot(siege.id()));
            clearPendingPhase(siege.id(), resolveClans(siege).orElse(null));
            clearActiveBossBar(siege.id(), resolveClans(siege).orElse(null));
            removeSiegeCompasses(siege);
        }
    scenery.detachDisplays();
    repository.close();
    activeSieges.clear();
    }

    public void tick() {
    if (closing || !ready) return;
        long now = System.currentTimeMillis();
    long delta = Math.max(0L, now - previousTick);
    previousTick = now;
    scenery.tickCleanup();
    if (!parcelBusy && now - parcelCheck >= 60000) {
      parcelBusy = true;
      parcelCheck = now;
      new me.lovelace.loveclans.siege.SiegeRewards(plugin, repository)
          .deliverPending()
          .whenComplete(
              (v, e) ->
                  onMain(
                      () -> {
                        parcelBusy = false;
                        if (e != null)
                          plugin
                              .getLogger()
                              .log(
                                  java.util.logging.Level.WARNING,
                                  "Не удалось доставить осадную награду",
                                  e);
                      }));
    }
    for (UUID id : List.copyOf(outcomes.keySet())) {
      if (!settled.contains(id) && !pending.contains(id)) {
        settle(activeSieges.get(id), SiegeResult.valueOf(outcomes.get(id)));
        continue;
      }
      if (settled.contains(id) && !pending.contains(id) && scenery.cleaned(id)) {
        pending.add(id);
        repository
            .finish(id)
            .whenComplete(
                (v, e) ->
                    onMain(
                        () -> {
                          pending.remove(id);
                          if (e == null) {
                            activeSieges.remove(id);
                            outcomes.remove(id);
                            revisions.remove(id);
                            settled.remove(id);
                            fees.remove(id);
                            refundScrolls.remove(id);
                            campFortification.remove(id);
                            campHits.remove(id);
                            holdProgress.remove(id);
                            allBrokenSince.remove(id);
                            lastHoldTick.remove(id);
                            storagePaused.remove(id);
                          } else storageFailure(id, e);
                        }));
      }
    }
        long cooldownMillis = cooldownDuration().toMillis();
        siegeCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

    for (ClanSiege original : List.copyOf(activeSieges.values())) {
      if (outcomes.containsKey(original.id())) continue;
      ClanSiege siege = original;
      boolean unavailable =
          siege.camps().stream()
              .anyMatch(
                  c -> {
                    var p = scenery.site(siege.id(), c.index());
                    return p != null && !scenery.loaded(p);
                  });
      if (pending.contains(siege.id()) || storagePaused.contains(siege.id()) || unavailable) {
        freeze(siege, delta);
        if (storagePaused.contains(siege.id())) checkpoint(siege.id());
        continue;
      }
      ensureActivePresentation(siege);
      for (var plan : scenery.sites(siege.id()))
        scenery.render(
            plan,
            fortificationLevel(siege.id(), plan.key().index()),
            siege.camps().get(plan.key().index()).broken());
            if (siege.state() == SiegeState.PREPARING) {
                tickPending(siege, now);
        checkpoint(siege.id());
                continue;
            }
            tickActive(siege, now);
      if (activeSieges.containsKey(siege.id())) checkpoint(siege.id());
        }
    }

    private void ensureActivePresentation(ClanSiege siege) {
        if (siege.state() != SiegeState.ACTIVE || activeBossBars.containsKey(siege.id())) return;
        var clans = resolveClans(siege);
        if (clans.isEmpty()) return;
        activeBossBars.put(siege.id(), BossBar.bossBar(Component.empty(), 0,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        Location center = resolveContestedTerritory(siege).map(this::territoryCenter).orElse(null);
        if (center != null) distributeSiegeCompasses(siege, clans.get().attacker(),
                clans.get().defender(), center);
    }

    private void tickPending(ClanSiege siege, long now) {
        long remainingMs = siege.endsAt() - now;
        if (remainingMs <= 0) {
            activateSiege(siege);
            return;
        }
        BossBar bar = pendingBossBars.get(siege.id());
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        if (bar == null || clansOpt.isEmpty()) {
            return;
        }
        long totalMs = preStartDuration().toMillis();
        bar.progress(Math.max(0f, Math.min(1f, (float) remainingMs / (float) totalMs)));
        bar.name(plugin.getMessages().component("siege.pending.bossbar", Map.of(
                "attacker", clansOpt.get().attacker().tag(), "color1", clansOpt.get().attacker().tagColor(),
                "defender", clansOpt.get().defender().tag(), "color2", clansOpt.get().defender().tagColor(),
                "time", formatDuration(remainingMs))));

        if (remainingMs <= 60_000L && oneMinuteWarned.add(siege.id())) {
            java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "siege.pending.one-minute-warning"));
        }
    }

    private void tickActive(ClanSiege siege, long now) {
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        if (clansOpt.isEmpty()) {
            endSiege(siege, SiegeResult.CANCELLED);
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        // Members who are actually at the camps (not AFK, within the assault radius) are the participants
        double presenceRadiusSq = Math.pow(plugin.getConfig().getDouble("siege.participation-radius", 40.0), 2);
        for (Clan side : List.of(attacker, defender)) {
            for (Player p : onlineMembers(side).toList()) {
                for (SiegeCamp camp : siege.camps()) {
                    Location campLoc = camp.toLocation();
                    if (campLoc != null && campLoc.getWorld() != null && campLoc.getWorld().equals(p.getWorld())
                            && campLoc.distanceSquared(p.getLocation()) <= presenceRadiusSq) {
                        me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, siege.id(), p, side.id());
                        break;
                    }
                }
            }
        }

        List<SiegeCamp> updatedCamps = null;
        int unbrokenCount = 0;
        int totalCamps = siege.camps().size();

        for (int i = 0; i < totalCamps; i++) {
            SiegeCamp camp = siege.camps().get(i);
            if (camp.broken()) {
                if (camp.respawnAt() <= now) {
                    World campWorld = Bukkit.getWorld(camp.world());
                    if (campWorld != null) {
                        placeCamp(siege.id(), camp.index(), campWorld, camp.x(), camp.y(), camp.z(), now);
                        if (updatedCamps == null) updatedCamps = new ArrayList<>(siege.camps());
                        SiegeCamp restored = camp.respawned(now);
                        updatedCamps.set(i, restored);
                        unbrokenCount++;

                        campWorld.playSound(restored.toLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.0f);
                        onlineMembers(attacker).forEach(p -> plugin.getMessages().actionBar(p, Component.text("§aОсадный лагерь #" + (camp.index() + 1) + " восстановлен!"), true));
                    }
                }
            } else {
                unbrokenCount++;
            }
        }

        if (updatedCamps != null) {
            ClanSiege updated = new ClanSiege(siege.id(), siege.attackerClanId(), siege.defenderClanId(),
                    siege.contestedTerritory(), siege.startedAt(), siege.endsAt(), siege.state(), List.copyOf(updatedCamps));
            activeSieges.put(updated.id(), updated);
            siege = updated;
        }

        long requiredHoldMs = campHoldDuration(siege.defenderClanId()).toMillis();
        long currentHold = holdProgress.getOrDefault(siege.id(), 0L);

        // Логика удержания:
        // Все живы -> прогресс идёт.
        // Хотя бы один сбит -> ПАУЗА.
        // Все сбиты одновременно > 30с -> ПОЛНЫЙ СБРОС.
        if (unbrokenCount == totalCamps && totalCamps > 0) {
            allBrokenSince.remove(siege.id());
            wipedWarned.remove(siege.id());

            Long lastTick = lastHoldTick.get(siege.id());
            if (lastTick != null) {
                long delta = Math.min(2000L, Math.max(0L, now - lastTick));
                currentHold = holdProgress.merge(siege.id(), delta, Long::sum);
            }
            lastHoldTick.put(siege.id(), now);

            // Оповещения на 50% и 90%
            if (currentHold >= requiredHoldMs * 0.5 && holdHalfWarned.add(siege.id())) {
                onlineMembers(attacker).forEach(p -> p.sendMessage(Component.text("§e[Осада] Прогресс удержания лагерей достиг §650%§e!")));
                onlineMembers(defender).forEach(p -> p.sendMessage(Component.text("§c[Осада] Прогресс удержания лагерей осаждающими достиг §650%§c! Уничтожайте лагеря!")));
            }
            if (currentHold >= requiredHoldMs * 0.9 && holdNearWarned.add(siege.id())) {
                onlineMembers(attacker).forEach(p -> p.sendMessage(Component.text("§a[Осада] Прогресс удержания §a90%§a! Победа близка!")));
                onlineMembers(defender).forEach(p -> p.sendMessage(Component.text("§4[Осада] КРИТИЧЕСКИЙ МОМЕНТ! Удержание 90%! Сбейте любой лагерь немедленно!")));
            }
        } else {
            // Пауза удержания
            lastHoldTick.put(siege.id(), now);

            if (unbrokenCount == 0 && totalCamps > 0) {
                long brokenSince = allBrokenSince.computeIfAbsent(siege.id(), id -> now);
                long wipeMs = campWipeResetDuration().toMillis();
                if (now - brokenSince >= wipeMs) {
                    holdProgress.put(siege.id(), 0L);
                    currentHold = 0L;
                    if (wipedWarned.add(siege.id())) {
                        onlineMembers(attacker).forEach(p -> p.sendMessage(Component.text("§cВсе осадные лагеря уничтожены! Прогресс удержания сброшен до нуля!")));
                        onlineMembers(defender).forEach(p -> p.sendMessage(Component.text("§aВсе вражеские осадные лагеря сметены! Прогресс удержания обнулён!")));
                        holdHalfWarned.remove(siege.id());
                        holdNearWarned.remove(siege.id());
                    }
                }
            } else {
                allBrokenSince.remove(siege.id());
            }
        }

        // Проверка победы атакующих
        if (currentHold >= requiredHoldMs && totalCamps > 0) {
            endSiege(siege, SiegeResult.ATTACKER_WIN);
            return;
        }

        // Проверка таймаута (победа защитников)
        if (siege.endsAt() <= now) {
            endSiege(siege, SiegeResult.DEFENDER_WIN);
            return;
        }

        // Обновление активного BossBar
        BossBar activeBar = activeBossBars.get(siege.id());
        if (activeBar != null) {
            long remainingActive = Math.max(0L, siege.endsAt() - now);
            boolean isPaused = unbrokenCount < totalCamps;
            activeBar.name(formatActiveBossBarTitle(attacker, defender, unbrokenCount, totalCamps, isPaused, currentHold, remainingActive));
            activeBar.progress(Math.max(0f, Math.min(1f, (float) currentHold / (float) requiredHoldMs)));
            activeBar.color(remainingActive <= 180_000L ? BossBar.Color.RED : BossBar.Color.YELLOW);

            java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender))
                    .forEach(p -> p.showBossBar(activeBar));
        }

        // Партиклы, визуализация и Action Bar (каждую секунду)
        tickVisualsAndActionBars(siege, attacker, defender);

        // Обновление компасов (каждые 2 секунды)
        Long lastComp = lastCompassTick.get(siege.id());
        if (lastComp == null || now - lastComp >= 2000L) {
            lastCompassTick.put(siege.id(), now);
            updateCompassTargets(siege, attacker, defender);
        }
    }

    private Component formatActiveBossBarTitle(Clan attacker, Clan defender, int unbroken, int total,
                                               boolean isPaused, long currentHold, long remainingActive) {
        String holdStr = isPaused ? "§cУдержание ПАУЗА§f" : "§fУдержание §e" + formatDuration(currentHold);
        return Component.text("§fОсада §6" + attacker.tag() + "§7→§c" + defender.tag()
                + " §8| §fЛагеря §a" + unbroken + "§7/§f" + total
                + " §8| " + holdStr
                + " §8| §f" + formatDuration(remainingActive));
    }

    private void tickVisualsAndActionBars(ClanSiege siege, Clan attacker, Clan defender) {
        for (SiegeCamp camp : siege.camps()) {
            Location loc = camp.toLocation();
            if (loc == null || loc.getWorld() == null) continue;
            World w = loc.getWorld();

            if (!camp.broken()) {
                // Кольцо пламени вокруг живого лагеря (радиус 2)
                for (int deg = 0; deg < 360; deg += 45) {
                    double rad = Math.toRadians(deg);
                    double px = loc.getX() + 2.0 * Math.cos(rad);
                    double pz = loc.getZ() + 2.0 * Math.sin(rad);
                    w.spawnParticle(Particle.FLAME, px, loc.getY() + 0.2, pz, 1, 0, 0, 0, 0.0);
                }
                w.spawnParticle(Particle.SMOKE, loc.getX(), loc.getY() + 0.8, loc.getZ(), 3, 0.1, 0.1, 0.1, 0.02);

                int fortLevel = fortificationLevel(siege.id(), camp.index());
                if (fortLevel > 0) {
                    w.spawnParticle(Particle.ENCHANT, loc.getX(), loc.getY() + 1.0, loc.getZ(), fortLevel * 4, 0.2, 0.2, 0.2, 0.1);
                }

                // Action Bar рядом с лагерем (радиус 5)
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) <= 25.0) {
                        if (attacker.hasMember(p.getUniqueId())) {
                            int req = hitsRequired(siege.id(), camp.index());
                            int cur = campHits.getOrDefault(siege.id(), Map.of()).getOrDefault(camp.index(), 0);
                            plugin.getMessages().hud(p, Component.text("§6Лагерь #" + (camp.index() + 1)
                                    + " §8· §7Укрепление §e" + fortLevel + "/3"
                                    + " §8· §7Ударов до сноса: §c" + Math.max(1, req - cur)));
                        }
                    }
                }
            } else {
                // Пепел над уничтоженным лагерем
                w.spawnParticle(Particle.ASH, loc.getX(), loc.getY() + 0.2, loc.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    private void distributeSiegeCompasses(ClanSiege siege, Clan attacker, Clan defender, Location defaultTarget) {
        onlineMembers(attacker).forEach(p -> giveSiegeCompass(p, siege, defaultTarget, defender));
        if (plugin.getConfig().getBoolean("siege.defender-compass", true)) {
            onlineMembers(defender).forEach(p -> giveSiegeCompass(p, siege, defaultTarget, attacker));
        }
    }

    private void giveSiegeCompass(Player player, ClanSiege siege, Location targetLocation, Clan enemyClan) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ClanItemFactory.SIEGE_COMPASS_KEY, PersistentDataType.STRING, siege.id().toString());
            meta.displayName(ItemBuilder.upright(plugin.getMessages().component("item.siege-compass.name", Map.of("tag", enemyClan.tag()))));
            meta.lore(plugin.getMessages().components("item.siege-compass.lore", Map.of(), null).stream().map(ItemBuilder::upright).toList());
            compass.setItemMeta(meta);
        }
        player.setCompassTarget(targetLocation);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(compass);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void removeSiegeCompasses(ClanSiege siege) {
        resolveClans(siege).ifPresent(clans -> {
            onlineMembers(clans.attacker()).forEach(p -> clearSiegeCompass(p, siege.id()));
            onlineMembers(clans.defender()).forEach(p -> clearSiegeCompass(p, siege.id()));
        });
    }

    private void clearSiegeCompass(Player player, UUID siegeId) {
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String tagged = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.SIEGE_COMPASS_KEY, PersistentDataType.STRING);
                if (tagged != null && tagged.equals(siegeId.toString())) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    private void updateCompassTargets(ClanSiege siege, Clan attacker, Clan defender) {
        // Each carrier is pointed at the live camp closest to HIM, not at whichever camp is listed first
        onlineMembers(attacker).forEach(p -> {
            Location target = nearestCamp(siege, p.getLocation(), true);
            if (target != null && hasSiegeCompass(p, siege.id())) p.setCompassTarget(target);
        });
        if (plugin.getConfig().getBoolean("siege.defender-compass", true)) {
            onlineMembers(defender).forEach(p -> {
                Location target = nearestCamp(siege, p.getLocation(), false);
                if (target != null && hasSiegeCompass(p, siege.id())) p.setCompassTarget(target);
            });
        }
    }

    /**
     * The closest unbroken camp to {@code from}; with {@code fallbackToBroken} the closest broken one (its respawn
     * point) when none is alive. Camps in another world count as infinitely far.
     */
    private Location nearestCamp(ClanSiege siege, Location from, boolean fallbackToBroken) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int pass = 0; pass < (fallbackToBroken ? 2 : 1) && best == null; pass++) {
            boolean wantBroken = pass == 1;
            for (SiegeCamp camp : siege.camps()) {
                if (camp.broken() != wantBroken) continue;
                Location loc = camp.toLocation();
                if (loc == null || loc.getWorld() == null) continue;
                double distance = loc.getWorld().equals(from.getWorld()) ? loc.distanceSquared(from) : Double.MAX_VALUE / 2;
                if (best == null || distance < bestDistance) {
                    best = loc;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean hasSiegeCompass(Player player, UUID siegeId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String tagged = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.SIEGE_COMPASS_KEY, PersistentDataType.STRING);
                if (tagged != null && tagged.equals(siegeId.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().values().stream()
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull);
    }

    public long getCooldownRemaining(UUID clan1, UUID clan2) {
        AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(clan1, clan2);
        Long last = siegeCooldowns.get(cooldownKey);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long duration = plugin.getClanManager().getClanById(clan1).map(c -> cooldownDuration(c).toMillis())
                .orElse(cooldownDuration().toMillis());
        return elapsed < duration ? (duration - elapsed) : 0;
    }

    public int activeSiegesCount() {
        return activeSieges.size();
    }

  public CompletableFuture<Boolean> upgradeCamp(
      Player player, UUID id, int index, boolean treasuryOnly) {
    if (!Bukkit.isPrimaryThread())
      return plugin
          .supplySync(() -> upgradeCamp(player, id, index, treasuryOnly))
          .thenCompose(f -> f);
    ClanSiege siege = activeSieges.get(id);
    if (siege == null || !canFortify(id, index)) return CompletableFuture.completedFuture(false);
    Clan clan = plugin.getClanManager().getClanById(siege.attackerClanId()).orElse(null);
    if (clan == null || !clan.hasMember(player.getUniqueId()))
      return CompletableFuture.completedFuture(false);
    int old = fortificationLevel(id, index);
    if (old == 3 && !scenery.towerAvailable()) {
      plugin.getMessages().send(player, "siege.models.tower-unavailable");
      return CompletableFuture.completedFuture(false);
    }
    var plan = scenery.site(id, index);
    if (plan == null || !scenery.loaded(plan)) return CompletableFuture.completedFuture(false);
    long cost = fortifyCost(id, index);
    boolean bank =
        clan.hasPermission(player.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.BANK)
            && clan.chestMoney() >= cost;
    Optional<dev.lovelace.lovecore.api.economy.LoveEconomy> economy =
        dev.lovelace.lovecore.api.LoveCore.service(
            dev.lovelace.lovecore.api.economy.LoveEconomy.class);
    if ((treasuryOnly && !bank)
        || (!bank && (economy.isEmpty() || !economy.get().charge(player, cost)))) {
      plugin
          .getMessages()
          .send(
              player,
              "gui.camp-fortify.message.not-enough-money",
              Map.of("cost", CoinFormat.format(cost)));
      return CompletableFuture.completedFuture(false);
    }
    pending.add(id);
    campFortification.computeIfAbsent(id, k -> new HashMap<>()).put(index, old + 1);
    var state = snapshot(id);
    CompletableFuture<Boolean> answer = new CompletableFuture<>();
    CompletableFuture<Boolean> write =
        repository.submit(
            c -> {
              c.setAutoCommit(false);
              try {
                if (bank)
                  try (var ps =
                      c.prepareStatement(
                          "UPDATE clans SET chest_money=chest_money-? WHERE id=? AND chest_money>=?")) {
                    ps.setLong(1, cost);
                    ps.setString(2, clan.id().toString());
                    ps.setLong(3, cost);
                    if (ps.executeUpdate() != 1)
                      throw new java.sql.SQLException("Недостаточно средств в казне");
                  }
                try (var ps =
                    c.prepareStatement(
                        "UPDATE clan_siege_state SET revision=?,payload=? WHERE siege_id=? AND terminal=0 AND revision<?")) {
                  ps.setLong(1, state.revision());
                  ps.setBytes(2, me.lovelace.loveclans.siege.SiegeRepository.encode(state));
                  ps.setString(3, id.toString());
                  ps.setLong(4, state.revision());
                  if (ps.executeUpdate() != 1)
                    throw new java.sql.SQLException("Устаревшее состояние лагеря");
                }
                c.commit();
                return true;
              } catch (Throwable e) {
                c.rollback();
                throw e;
              } finally {
                c.setAutoCommit(true);
              }
            });
    UpgradeOperation op =
        new UpgradeOperation(
            id, index, old, cost, bank, player, clan, economy, plan, write, answer);
    upgrades.put(id, op);
    write.whenComplete(
        (ok, error) -> {
          if (!closing) onMain(() -> completeUpgrade(op, error));
        });
    return answer;
  }

  private record UpgradeOperation(
      UUID id,
      int index,
      int old,
      long cost,
      boolean bank,
      Player player,
      Clan clan,
      Optional<dev.lovelace.lovecore.api.economy.LoveEconomy> economy,
      me.lovelace.loveclans.siege.SiegeScenery.Plan plan,
      CompletableFuture<Boolean> write,
      CompletableFuture<Boolean> answer) {}

  private void completeUpgrade(UpgradeOperation op, Throwable failure) {
    if (!upgrades.remove(op.id(), op)) return;
    pending.remove(op.id());
    if (failure != null) {
      campFortification.get(op.id()).put(op.index(), op.old());
      if (!op.bank()) op.economy().ifPresent(e -> e.give(op.player(), op.cost()));
      storageFailure(op.id(), failure);
      op.answer().completeExceptionally(failure);
      return;
    }
    if (op.bank()) op.clan().addChestMoney(-op.cost());
    plugin.getConflictParticipants().mark(op.id(), op.player().getUniqueId(), op.clan().id());
    if (!closing) {
      try {
        if (op.old() == 3) scenery.evacuate(op.plan());
        scenery.render(op.plan(), op.old() + 1, false);
      } catch (RuntimeException e) {
        plugin
            .getLogger()
            .log(
                java.util.logging.Level.WARNING,
                "Модель укрепления будет восстановлена при следующем обновлении",
                e);
      }
      plugin
          .getMessages()
          .send(
              op.player(),
              "gui.camp-fortify.message.fortified",
              Map.of(
                  "number",
                  String.valueOf(op.index() + 1),
                  "level",
                  String.valueOf(op.old() + 1),
                  "hits",
                  String.valueOf(hitsRequired(op.id(), op.index()))));
    }
    op.answer().complete(true);
  }

  public me.lovelace.loveclans.siege.SiegeScenery scenery() {
    return scenery;
  }

  public boolean isSettling(UUID clan) {
    return activeSieges.values().stream()
        .anyMatch(
            s ->
                s.involves(clan) && (outcomes.containsKey(s.id()) || upgrades.containsKey(s.id())));
  }

  public boolean towerEnabled() {
    return plugin.getConfig().getBoolean("siege.tower.enabled", true)
        && plugin.getConfig().getInt("siege.fortification.max-level", 3) >= 3;
  }

  public int maxFortificationLevel() {
    return towerEnabled()
        ? 4
        : Math.max(1, Math.min(3, plugin.getConfig().getInt("siege.fortification.max-level", 3)));
  }

  public CompletableFuture<List<me.lovelace.loveclans.siege.SiegeSnapshot>> loadAsync() {
    return repository.load();
  }

  public void restore(List<me.lovelace.loveclans.siege.SiegeSnapshot> saved) {
    long now = System.currentTimeMillis();
    for (var state : saved) {
      List<SiegeCamp> camps = new ArrayList<>();
      for (var c : state.camps()) {
        SiegeCamp camp = c.resume(now);
        camps.add(camp);
        scenery.register(
            new me.lovelace.loveclans.siege.SiegeScenery.Plan(
                new me.lovelace.loveclans.siege.SiegeScenery.Key(state.id(), c.index()),
                camp,
                c.direction(),
                c.cells()));
        campFortification
            .computeIfAbsent(state.id(), id -> new HashMap<>())
            .put(c.index(), c.level());
        campHits.computeIfAbsent(state.id(), id -> new HashMap<>()).put(c.index(), c.hits());
      }
      ClanSiege siege = state.resume(now);
      activeSieges.put(siege.id(), siege);
      fees.put(siege.id(), state.paidFee());
      if (state.refundScroll() != null) refundScrolls.put(siege.id(), state.refundScroll());
      revisions.put(siege.id(), state.revision());
      holdProgress.put(siege.id(), state.hold());
      lastHoldTick.put(siege.id(), now);
      if (state.wipeElapsed() > 0) allBrokenSince.put(siege.id(), now - state.wipeElapsed());
      state
          .participants()
          .forEach((p, c) -> plugin.getConflictParticipants().mark(siege.id(), p, c));
      if (state.outcome() != null) {
        outcomes.put(siege.id(), state.outcome());
        settle(siege, SiegeResult.valueOf(state.outcome()));
        continue;
      }
      var clans = resolveClans(siege);
      if (clans.isEmpty() || resolveContestedTerritory(siege).isEmpty()) {
        endSiege(siege, SiegeResult.CANCELLED);
        continue;
      }
      if (siege.state() == SiegeState.PREPARING)
        beginPendingPhase(siege, clans.get().attacker(), clans.get().defender());
      else {
        activeBossBars.put(
            siege.id(),
            BossBar.bossBar(Component.empty(), 0, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        Location center = resolveContestedTerritory(siege).map(this::territoryCenter).orElse(null);
        if (center != null)
          distributeSiegeCompasses(siege, clans.get().attacker(), clans.get().defender(), center);
      }
    }
    previousTick = now;
    ready = true;
  }

  private me.lovelace.loveclans.siege.SiegeSnapshot snapshot(UUID id) {
    ClanSiege siege = activeSieges.get(id);
    long now = System.currentTimeMillis();
    List<me.lovelace.loveclans.siege.SiegeSnapshot.Camp> camps = new ArrayList<>();
    for (SiegeCamp camp : siege.camps()) {
      var plan = scenery.site(id, camp.index());
      camps.add(
          new me.lovelace.loveclans.siege.SiegeSnapshot.Camp(
              camp.index(),
              camp.world(),
              camp.x(),
              camp.y(),
              camp.z(),
              plan == null
                  ? me.lovelace.loveclans.siege.CampLayout.Direction.NORTH
                  : plan.direction(),
              fortificationLevel(id, camp.index()),
              campHits.getOrDefault(id, Map.of()).getOrDefault(camp.index(), 0),
              camp.broken(),
              camp.broken() ? Math.max(0, camp.respawnAt() - now) : 0,
              plan == null ? List.of() : plan.originals()));
    }
    return new me.lovelace.loveclans.siege.SiegeSnapshot(
        1,
        id,
        siege.attackerClanId(),
        siege.defenderClanId(),
        siege.contestedTerritory(),
        siege.startedAt(),
        fees.getOrDefault(id, 0L),
        refundScrolls.get(id),
        siege.state(),
        Math.max(0, siege.endsAt() - now),
        holdProgress.getOrDefault(id, 0L),
        allBrokenSince.containsKey(id) ? Math.max(0, now - allBrokenSince.get(id)) : 0,
        List.copyOf(camps),
        plugin.getConflictParticipants().snapshot(id),
        outcomes.get(id),
        revisions.merge(id, 1L, Long::sum));
  }

  public void rememberDeclarationScroll(UUID id, org.bukkit.inventory.ItemStack scroll) {
    refundScrolls.put(
        id,
        Base64.getEncoder()
            .encodeToString(
                me.lovelace.loveclans.siege.SiegeRewards.bytes(
                    new org.bukkit.inventory.ItemStack[] {scroll.clone()})));
    checkpoint(id);
  }

  private CompletableFuture<ClanSiege> persistDeclaration(ClanSiege siege) {
    return plugin
        .supplySync(() -> snapshot(siege.id()))
        .thenCompose(repository::save)
        .thenApply(v -> siege)
        .exceptionallyCompose(
            failure ->
                plugin
                    .supplySync(
                        () -> {
                          activeSieges.remove(siege.id());
                          siegeCooldowns.remove(
                              pairKey(siege.attackerClanId(), siege.defenderClanId()));
                          clearPendingPhase(siege.id(), resolveClans(siege).orElse(null));
                          long fee = fees.getOrDefault(siege.id(), 0L);
                          plugin
                              .getClanManager()
                              .getClanById(siege.attackerClanId())
                              .ifPresent(
                                  c -> {
                                    c.addChestMoney(fee);
                                    plugin
                                        .getStorage()
                                        .updateClanChestMoney(c.id(), c.chestMoney());
                                  });
                          return (ClanSiege) null;
                        })
                    .thenCompose(v -> CompletableFuture.failedFuture(failure)));
  }

  private void checkpoint(UUID id) {
    if (closing || !activeSieges.containsKey(id) || !checkpointing.add(id)) return;
    repository
        .save(snapshot(id))
        .whenComplete(
            (v, e) ->
                onMain(
                    () -> {
                      checkpointing.remove(id);
                      if (e != null) storageFailure(id, e);
                      else storagePaused.remove(id);
                    }));
  }

  private void storageFailure(UUID id, Throwable error) {
    if (storagePaused.add(id))
      plugin
          .getLogger()
          .log(java.util.logging.Level.SEVERE, "Осада приостановлена: ошибка сохранения", error);
  }

  private void onMain(Runnable action) {
    if (closing) return;
    if (Bukkit.isPrimaryThread()) action.run();
    else if (plugin.isEnabled())
      Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                if (!closing) action.run();
              });
  }

  private void freeze(ClanSiege siege, long elapsed) {
    List<SiegeCamp> camps =
        siege.camps().stream()
            .map(
                c ->
                    new SiegeCamp(
                        c.index(),
                        c.world(),
                        c.x(),
                        c.y(),
                        c.z(),
                        c.standingSince(),
                        c.broken(),
                        c.broken() ? c.respawnAt() + elapsed : c.respawnAt()))
            .toList();
    activeSieges.put(
        siege.id(),
        new ClanSiege(
            siege.id(),
            siege.attackerClanId(),
            siege.defenderClanId(),
            siege.contestedTerritory(),
            siege.startedAt(),
            siege.endsAt() + elapsed,
            siege.state(),
            camps));
    lastHoldTick.put(siege.id(), System.currentTimeMillis());
    allBrokenSince.computeIfPresent(siege.id(), (id, t) -> t + elapsed);
  }

  private void cancelPlacement(ClanSiege siege, Clan attacker) {
    if (!pending.add(siege.id())) return;
    long fee = fees.getOrDefault(siege.id(), 0L);
    String scroll = refundScrolls.get(siege.id());
    var cancelled=snapshot(siege.id()).withOutcome(SiegeResult.CANCELLED.name());
    repository
        .once(
            siege.id(),
            "placement-refund",
            c -> {
              try (var ps =
                  c.prepareStatement("UPDATE clans SET chest_money=chest_money+? WHERE id=?")) {
                ps.setLong(1, fee);
                ps.setString(2, attacker.id().toString());
                ps.executeUpdate();
              }
              if (scroll != null)
                try (var ps =
                    c.prepareStatement(
                        "INSERT INTO clan_siege_artifacts(siege_id,clan_id,payload) VALUES(?,?,?)")) {
                  ps.setString(1, siege.id().toString());
                  ps.setString(2, attacker.id().toString());
                  ps.setBytes(3, Base64.getDecoder().decode(scroll));
                  ps.executeUpdate();
                }
              try (var ps =
                  c.prepareStatement(
                      "DELETE FROM clan_conflict_cooldowns WHERE kind='SIEGE' AND clan_a=? AND clan_b=?")) {
                var pair = pairKey(siege.attackerClanId(), siege.defenderClanId());
                ps.setString(1, pair.getKey().toString());
                ps.setString(2, pair.getValue().toString());
                ps.executeUpdate();
              }
              try(var ps=c.prepareStatement("UPDATE clan_siege_state SET revision=?,payload=? WHERE siege_id=? AND terminal=0")){ps.setLong(1,cancelled.revision());ps.setBytes(2,me.lovelace.loveclans.siege.SiegeRepository.encode(cancelled));ps.setString(3,siege.id().toString());ps.executeUpdate();}
              return null;
            })
        .whenComplete(
            (first, error) ->
                onMain(
                    () -> {
                      pending.remove(siege.id());
                      if (error != null) {
                        storageFailure(siege.id(), error);
                        return;
                      }
                      if (first) attacker.addChestMoney(fee);
                      siegeCooldowns.remove(
                          pairKey(siege.attackerClanId(), siege.defenderClanId()));
                      plugin.getMessages().send(Bukkit.getConsoleSender(), "siege.models.no-space");
                      outcomes.put(siege.id(),SiegeResult.CANCELLED.name());
                      settle(siege, SiegeResult.CANCELLED);
                    }));
  }
}
