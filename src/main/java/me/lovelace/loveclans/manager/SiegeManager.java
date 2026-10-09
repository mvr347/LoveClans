package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.history.ConflictRecord;
import me.lovelace.loveclans.model.siege.ClanSiege;
import me.lovelace.loveclans.model.siege.SiegeCamp;
import me.lovelace.loveclans.model.siege.SiegeResult;
import me.lovelace.loveclans.model.siege.SiegeState;
import me.lovelace.loveclans.util.ClanItemFactory;
import me.lovelace.loveclans.util.CoinFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Campfire;
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

    private Duration campHoldDuration() {
        long holdMinutes = plugin.getConfig().getLong("siege.camp-hold-minutes", 10);
        long totalMinutes = plugin.getConfig().getLong("siege.duration-minutes", 22);
        return Duration.ofMinutes(Math.max(1, Math.min(holdMinutes, totalMinutes - 1)));
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
        if (siege == null || siege.state() != SiegeState.ACTIVE || campIndex < 0 || campIndex >= siege.camps().size()) {
            return false;
        }
        if (siege.camps().get(campIndex).broken()) {
            return false;
        }
        int max = Math.max(1, plugin.getConfig().getInt("siege.fortification.max-level", 3));
        return fortificationLevel(siegeId, campIndex) < max;
    }

    public boolean fortifyCamp(UUID siegeId, int campIndex) {
        ClanSiege siege = activeSieges.get(siegeId);
        if (!canFortify(siegeId, campIndex)) {
            return false;
        }
        int max = Math.max(1, plugin.getConfig().getInt("siege.fortification.max-level", 3));
        Map<Integer, Integer> levels = campFortification.computeIfAbsent(siegeId, id -> new ConcurrentHashMap<>());
        int current = levels.getOrDefault(campIndex, 0);
        if (current >= max) {
            return false;
        }
        levels.put(campIndex, current + 1);
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
            return plugin.supplySync(() -> startSiegeInternal(attacker, defender, territoryKey, true));
        }

        // Асинхронно проверяем недельные лимиты осад из архива
        return plugin.getConflictArchive().historyOf(attacker.id(), 50).thenCompose(atkHistory ->
                plugin.getConflictArchive().historyOf(defender.id(), 50).thenCompose(defHistory ->
                        plugin.supplySync(() -> {
                            long now = System.currentTimeMillis();
                            long weekAgo = now - 7L * 24 * 3600_000L;
                            int maxAttacks = plugin.getConfig().getInt("siege.max-sieges-per-clan-per-week", 2);
                            int maxDefends = plugin.getConfig().getInt("siege.max-times-sieged-per-week", 2);

                            long recentAttacks = atkHistory.stream()
                                    .filter(r -> r.kind() == ConflictKind.SIEGE && r.attackerClanId().equals(attacker.id()) && r.endedAt() >= weekAgo)
                                    .count();
                            if (recentAttacks >= maxAttacks) {
                                throw new IllegalStateException("siege.weekly-attacker-limit");
                            }

                            long recentDefends = defHistory.stream()
                                    .filter(r -> r.kind() == ConflictKind.SIEGE && r.defenderClanId().equals(defender.id()) && r.endedAt() >= weekAgo)
                                    .count();
                            if (recentDefends >= maxDefends) {
                                throw new IllegalStateException("siege.weekly-defender-limit");
                            }

                            return startSiegeInternal(attacker, defender, territoryKey, false);
                        })
                )
        );
    }

    private ClanSiege startSiegeInternal(Clan attacker, Clan defender, TerritoryKey territoryKey, boolean force) {
        AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(attacker.id(), defender.id());
        long now = System.currentTimeMillis();

        if (attacker.id().equals(defender.id())) {
            throw new IllegalStateException("war.cannot-target-self");
        }
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
            if (lastSiegeTime != null && (now - lastSiegeTime < cooldownDuration().toMillis())) {
                long remainingSeconds = (cooldownDuration().toMillis() - (now - lastSiegeTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            int afkIgnore = plugin.getConfig().getInt("siege.afk-ignore-minutes", 15);
            int attackerOnline = countActiveOnline(attacker, afkIgnore);
            int defenderOnline = countActiveOnline(defender, afkIgnore);
            if (attackerOnline < plugin.getConfig().getInt("siege.min-attacker-online", 2)) {
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
        List<SiegeCamp> camps = spawnCamps(siege.id(), center, now);
        ClanSiege activated = siege.activate(now + siegeDuration().toMillis(), camps);
        activeSieges.put(activated.id(), activated);

        holdProgress.put(activated.id(), 0L);
        lastHoldTick.put(activated.id(), now);
        allBrokenSince.remove(activated.id());

        // Создаём активный BossBar
        BossBar activeBar = BossBar.bossBar(
                formatActiveBossBarTitle(attacker, defender, camps.size(), camps.size(), false, 0L, siegeDuration().toMillis()),
                0.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        activeBossBars.put(activated.id(), activeBar);
        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender))
                .forEach(p -> p.showBossBar(activeBar));

        // Раздаём компасы
        distributeSiegeCompasses(activated, attacker, defender, center);

        // Звуки и оповещения
        onlineMembers(attacker).forEach(p -> {
            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.2f, 1.0f);
            plugin.getMessages().sendTitle(p, "siege.start.attacker-title", "siege.start.attacker-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor()));
        });
        onlineMembers(defender).forEach(p -> {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 0.8f);
            plugin.getMessages().sendTitle(p, "siege.start.defender-title", "siege.start.defender-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
        });
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
        int min = plugin.getConfig().getInt("siege.camp-count-min", 3);
        int max = Math.max(min, plugin.getConfig().getInt("siege.camp-count-max", 4));
        int count = min + (max > min ? ThreadLocalRandom.current().nextInt(max - min + 1) : 0);
        double distance = plugin.getConfig().getInt("siege.camp-distance-blocks", 50);

        List<SiegeCamp> camps = new ArrayList<>(count);
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, 360);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(baseAngle + (360.0 / count) * i + ThreadLocalRandom.current().nextDouble(-10, 10));
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            World world = center.getWorld();
            int y = world.getHighestBlockYAt(x, z) + 1;
            camps.add(placeCamp(siegeId, i, world, x, y, z, now));
        }
        return camps;
    }

    private SiegeCamp placeCamp(UUID siegeId, int index, World world, int x, int y, int z, long standingSince) {
        var block = world.getBlockAt(x, y, z);
        block.setType(Material.CAMPFIRE);
        if (block.getState() instanceof Campfire campfire) {
            campfire.getPersistentDataContainer().set(ClanItemFactory.SIEGE_ID_KEY, PersistentDataType.STRING, siegeId.toString());
            campfire.getPersistentDataContainer().set(ClanItemFactory.SIEGE_CAMP_INDEX_KEY, PersistentDataType.INTEGER, index);
            campfire.update(true);
        }
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
        if (siege == null || siege.state() != SiegeState.ACTIVE || campIndex < 0 || campIndex >= siege.camps().size()) {
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
                        p.sendActionBar(Component.text("§eСнос лагеря #" + (campIndex + 1) + " · §f" + accumulated + "/" + required + " §eудара"));
                        plugin.getMessages().send(p, "siege.camp.weakened", Map.of(
                                "index", String.valueOf(campIndex + 1),
                                "hits", String.valueOf(accumulated),
                                "required", String.valueOf(required)));
                    }));
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
        removeCampBlock(camp);

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
        activeSieges.remove(siege.id());
        campFortification.remove(siege.id());
        campHits.remove(siege.id());
        holdProgress.remove(siege.id());
        allBrokenSince.remove(siege.id());
        lastHoldTick.remove(siege.id());
        lastCompassTick.remove(siege.id());
        holdHalfWarned.remove(siege.id());
        holdNearWarned.remove(siege.id());
        wipedWarned.remove(siege.id());

        Optional<SiegeClans> clansOpt = resolveClans(siege);
        clearPendingPhase(siege.id(), clansOpt.orElse(null));
        clearActiveBossBar(siege.id(), clansOpt.orElse(null));

        for (SiegeCamp camp : siege.camps()) {
            if (!camp.broken()) {
                removeCampBlock(camp);
            }
        }
        removeSiegeCompasses(siege);

        if (clansOpt.isEmpty()) {
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        if (result == SiegeResult.CANCELLED) {
            plugin.getConflictParticipants().forget(siege.id());
            return;
        }
        me.lovelace.loveclans.activity.ConflictEvents.resolved(plugin, me.lovelace.loveclans.model.history.ConflictKind.SIEGE,
                siege.id(), siege.attackerClanId(), siege.defenderClanId(),
                result == SiegeResult.ATTACKER_WIN ? me.lovelace.loveclans.api.events.ConflictOutcome.ATTACKER_WIN
                        : me.lovelace.loveclans.api.events.ConflictOutcome.DEFENDER_WIN);

        Clan winner = result == SiegeResult.ATTACKER_WIN ? attacker : defender;
        Clan loser = result == SiegeResult.ATTACKER_WIN ? defender : attacker;

        if (result == SiegeResult.ATTACKER_WIN) {
            onlineMembers(attacker).forEach(p -> {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
                plugin.getMessages().sendTitle(p, "siege.end.victory-title", "siege.end.victory-subtitle",
                        Map.of("tag", defender.tag(), "color", defender.tagColor()));
            });
            onlineMembers(defender).forEach(p -> {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
                plugin.getMessages().sendTitle(p, "siege.end.defeat-title", "siege.end.defeat-subtitle",
                        Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
            });
        } else {
            onlineMembers(defender).forEach(p -> {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
                plugin.getMessages().sendTitle(p, "siege.end.repelled-title", "siege.end.repelled-subtitle",
                        Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
            });
            onlineMembers(attacker).forEach(p -> {
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);
                plugin.getMessages().sendTitle(p, "siege.end.failed-title", "siege.end.failed-subtitle",
                        Map.of("tag", defender.tag(), "color", defender.tagColor()));
            });
        }

        long baseExp = plugin.getConfig().getLong("leveling.war-win-exp", 1200L);
        double multiplier = result == SiegeResult.ATTACKER_WIN
                ? plugin.getConfig().getDouble("siege.win-exp-multiplier", 0.75)
                : plugin.getConfig().getDouble("siege.defend-win-exp-multiplier", 0.45);
        long rewardExp = Math.round(baseExp * multiplier);

        plugin.getClanManager().addExperienceAsync(winner, rewardExp);
        plugin.getClanManager().recordSiegeResultAsync(winner, true);
        plugin.getClanManager().recordSiegeResultAsync(loser, false);

        if (result == SiegeResult.ATTACKER_WIN) {
            // Мгновенный трофей: казна + предметы (siege-justify-rewards.md §2.2)
            transferInstantSpoils(attacker, defender);

            // Репарации: 3 дня (newsieges.md §4.1, siege-justify-rewards.md §2.3)
            int repDays = plugin.getConfig().getInt("siege.reparations.duration-days", 3);
            int baseDaily = plugin.getConfig().getInt("siege.reparations.daily-amount", 300);
            int perLevel = plugin.getConfig().getInt("siege.reparations.daily-per-defender-level", 40);
            int minDaily = plugin.getConfig().getInt("siege.reparations.min-daily", 100);
            int maxDaily = plugin.getConfig().getInt("siege.reparations.max-daily", 2000);
            long dailyAmount = Math.max(minDaily, Math.min(maxDaily, (long) baseDaily + (long) defender.level() * perLevel));
            plugin.getModifierManager().grantReparations(defender.id(), attacker.id(), repDays, dailyAmount);

            // Модификатор SIEGE_PRESSURE на защитника
            plugin.getModifierManager().grantSiegePressure(defender.id(), 3);

            // Casus мести защитнику
            plugin.getModifierManager().grantJustCasusBoth(defender.id(), attacker.id(), "revenge_siege");

            if (plugin.getConfig().getBoolean("siege.reward-artifact", true)) {
                grantRandomArtifact(attacker);
            }
        } else {
            // Осада отбита: бафф SIEGE_REPELLED защитнику на 2 дня
            plugin.getModifierManager().grantSiegeRepelled(defender.id(), 2);
        }

        plugin.getConflictArchive().record(ConflictKind.SIEGE, siege.attackerClanId(), siege.defenderClanId(),
                winner.id(), 0, 0, siege.startedAt());
    }

    private void transferInstantSpoils(Clan attacker, Clan defender) {
        double basePercent = plugin.getConfig().getDouble("siege.instant-loot.money-percent-base", 20.0);
        double perLevel = plugin.getConfig().getDouble("siege.instant-loot.money-percent-per-level", 0.5);
        double cap = plugin.getConfig().getDouble("siege.instant-loot.money-percent-cap", 35.0);
        double percent = Math.min(cap, basePercent + defender.level() * perLevel);

        long moneyAmount = Math.round(defender.chestMoney() * (percent / 100.0));
        if (moneyAmount > 0) {
            defender.addChestMoney(-moneyAmount);
            attacker.addChestMoney(moneyAmount);
            plugin.getStorage().updateClanChestMoney(defender.id(), defender.chestMoney());
            plugin.getStorage().updateClanChestMoney(attacker.id(), attacker.chestMoney());

            Map<String, String> placeholders = Map.of(
                    "amount", CoinFormat.format(moneyAmount),
                    "percent", String.valueOf((int) Math.round(percent)));
            onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "siege.chest-spoils-attacker", placeholders));
            onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "siege.chest-spoils-defender", placeholders));
        }

        // Share of the defender's stored stacks that moves over. A defender holding the chest menu open would write
        // his stale snapshot over the change on close, so such menus are closed first.
        onlineMembers(defender).forEach(p -> {
            org.bukkit.inventory.Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getType() == org.bukkit.event.inventory.InventoryType.CHEST && top.getHolder() == null) p.closeInventory();
        });
        double itemPercent = plugin.getConfig().getDouble("siege.instant-loot.item-slot-percent", 20.0);
        plugin.getClanManager().loadChestContentsAsync(defender).thenAccept(defContents ->
                plugin.getClanManager().loadChestContentsAsync(attacker).thenAccept(atkContents ->
                        plugin.runSync(() -> moveSpoilItems(attacker, defender, defContents, atkContents, itemPercent))));
    }

    /**
     * Main thread. Works on copies and only touches the unlocked rows of both chests. A stack the attacker has no
     * room for stays with the defender instead of disappearing.
     */
    private void moveSpoilItems(Clan attacker, Clan defender, ItemStack[] defSource, ItemStack[] atkSource, double itemPercent) {
        if (defSource == null || atkSource == null) return;
        if (plugin.getClanManager().isItemChestLocked(defender.id()) || plugin.getClanManager().isItemChestLocked(attacker.id())) {
            plugin.getLogger().warning("Siege spoils: a clan chest was in use, items were not moved.");
            return;
        }
        ItemStack[] def = defSource.clone();
        ItemStack[] atk = atkSource.clone();
        int defLimit = me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(defender.chestRows(), def.length);
        int atkLimit = me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(attacker.chestRows(), atk.length);
        List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < defLimit; i++) {
            if (def[i] != null && def[i].getType() != Material.AIR) occupied.add(i);
        }
        int toTake = (int) Math.round(occupied.size() * (itemPercent / 100.0));
        if (toTake <= 0) return;
        Collections.shuffle(occupied);
        boolean moved = false;
        for (int n = 0; n < toTake; n++) {
            int slot = occupied.get(n);
            for (int a = 0; a < atkLimit; a++) {
                if (atk[a] == null || atk[a].getType() == Material.AIR) {
                    atk[a] = def[slot];
                    def[slot] = null;
                    moved = true;
                    break;
                }
            }
        }
        if (moved) {
            plugin.getClanManager().saveChestContentsAsync(defender.id(), def);
            plugin.getClanManager().saveChestContentsAsync(attacker.id(), atk);
        }
    }

    private void grantRandomArtifact(Clan clan) {
        ArtifactType[] types = ArtifactType.values();
        ArtifactType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        Optional<Player> recipient = clan.members().values().stream()
                .filter(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .map(ClanMember::playerId).map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst()
                .or(() -> onlineMembers(clan).findFirst());
        recipient.ifPresent(player -> {
            ItemStack artifact = plugin.getArtifactManager().createArtifact(type);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(artifact);
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            plugin.getMessages().send(player, "siege.artifact-reward");
        });
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
        for (ClanSiege siege : activeSieges()) {
            activeSieges.remove(siege.id());
            clearPendingPhase(siege.id(), resolveClans(siege).orElse(null));
            clearActiveBossBar(siege.id(), resolveClans(siege).orElse(null));
            removeSiegeCompasses(siege);
            for (SiegeCamp camp : siege.camps()) {
                if (!camp.broken()) {
                    removeCampBlock(camp);
                }
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownDuration().toMillis();
        siegeCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

        for (ClanSiege siege : activeSieges.values()) {
            if (siege.state() == SiegeState.PREPARING) {
                tickPending(siege, now);
                continue;
            }
            tickActive(siege, now);
        }
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
                        onlineMembers(attacker).forEach(p -> p.sendActionBar(Component.text("§aОсадный лагерь #" + (camp.index() + 1) + " восстановлен!")));
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

        long requiredHoldMs = campHoldDuration().toMillis();
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
                            p.sendActionBar(Component.text("§6Лагерь #" + (camp.index() + 1)
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
            meta.displayName(plugin.getMessages().component("item.siege-compass.name", Map.of("tag", enemyClan.tag())));
            meta.lore(List.of(plugin.getMessages().component("item.siege-compass.lore")));
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
        long duration = cooldownDuration().toMillis();
        return elapsed < duration ? (duration - elapsed) : 0;
    }

    public int activeSiegesCount() {
        return activeSieges.size();
    }
}
