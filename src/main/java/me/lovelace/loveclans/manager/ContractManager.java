package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import me.lovelace.loveclans.model.quest.QuestObjective;
import me.lovelace.loveclans.storage.ClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan contracts ("обеты", §1) — a weekly pool of 20 and a daily pool of 40, each config-driven
 * (config.yml, no hardcoded content). A clan can run one active WEEKLY and one active DAILY
 * contract at the same time, tracked as independent slots.
 *
 * The compact contract menu (§1.4) only ever surfaces one weekly candidate and two daily
 * candidates at a time rather than letting a clan browse the full pools, so both systems follow
 * the same "big backend pool, small rotating surface" shape: weekly features one contract per
 * calendar week (deterministic pick from the 20, so every contract in the pool comes up roughly
 * once every 20 weeks), daily rotates 3-5 (config-configurable) of the 40 in every day at
 * midnight. Both rotations are derived purely from the current date, so they're stable across
 * restarts without needing their own persistence.
 */
public final class ContractManager {

    private final LoveClansPlugin plugin;
    private final ClanStorage storage;

    private final Map<String, ClanContractDefinition> weeklyCatalog = new LinkedHashMap<>();
    private final List<String> weeklyOrder = new ArrayList<>();
    private final Map<String, ClanContractDefinition> dailyCatalog = new LinkedHashMap<>();
    private final List<String> dailyOrder = new ArrayList<>();

    private final Map<UUID, ClanQuestProgress> activeWeekly = new ConcurrentHashMap<>();
    private final Map<UUID, ClanQuestProgress> activeDaily = new ConcurrentHashMap<>();

    private volatile long cachedRotationDay = Long.MIN_VALUE;
    private volatile List<String> cachedDailyRotation = List.of();

    public ContractManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        buildCatalogs();
    }

    private void buildCatalogs() {
        buildCatalog(ContractType.WEEKLY, "clans.contracts.weekly.pool", weeklyCatalog, weeklyOrder);
        buildCatalog(ContractType.DAILY, "clans.contracts.daily.pool", dailyCatalog, dailyOrder);
    }

    private void buildCatalog(ContractType type, String path, Map<String, ClanContractDefinition> catalog, List<String> order) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(path);
        if (root == null) {
            plugin.getLogger().warning("Missing contract pool config section: " + path);
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) continue;
            String name = entry.getString("name", id);
            String description = entry.getString("description", "");
            long rewardXp = entry.getLong("reward-xp", 0);
            ContractObjectiveFactory.build(plugin, id, entry.getConfigurationSection("objective")).ifPresentOrElse(objective -> {
                catalog.put(id, new ClanContractDefinition(id, type, name, description, objective, rewardXp));
                order.add(id);
            }, () -> plugin.getLogger().warning("Skipped invalid " + type + " contract '" + id + "'."));
        }
    }

    public Collection<ClanContractDefinition> weeklyCatalog() {
        return weeklyCatalog.values();
    }

    public Collection<ClanContractDefinition> dailyCatalog() {
        return dailyCatalog.values();
    }

    public Optional<ClanQuestProgress> activeWeekly(UUID clanId) {
        return Optional.ofNullable(activeWeekly.get(clanId));
    }

    public Optional<ClanQuestProgress> activeDaily(UUID clanId) {
        return Optional.ofNullable(activeDaily.get(clanId));
    }

    public Optional<ClanContractDefinition> definition(ContractType type, String id) {
        return Optional.ofNullable(type == ContractType.DAILY ? dailyCatalog.get(id) : weeklyCatalog.get(id));
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllContractsAsync(ContractType.WEEKLY).thenAccept(all -> all.forEach(progress -> {
            if (weeklyCatalog.containsKey(progress.questId())) activeWeekly.put(progress.clanId(), progress);
        })).thenCompose(v -> storage.loadAllContractsAsync(ContractType.DAILY)).thenAccept(all -> all.forEach(progress -> {
            if (dailyCatalog.containsKey(progress.questId())) activeDaily.put(progress.clanId(), progress);
        }));
    }

    // --- Динамическая сложность (§1.2) ---

    private double difficultyMultiplier(Clan clan) {
        double base = plugin.getConfig().getDouble("clans.contracts.difficulty.base", 1.0);
        double perMemberStep = plugin.getConfig().getDouble("clans.contracts.difficulty.per-member-step", 0.15);
        int members = Math.max(1, clan.members().size());
        return base + (members - 1) * perMemberStep;
    }

    /** Reconstructs a display-ready objective whose target matches the already-scaled snapshot stored on the progress. */
    public QuestObjective displayObjective(ClanContractDefinition definition, ClanQuestProgress progress) {
        int baseTarget = definition.objective().getTargetAmount();
        if (baseTarget <= 0 || progress.scaledTarget() == baseTarget) {
            return definition.objective();
        }
        return definition.objective().scaled(progress.scaledTarget() / (double) baseTarget);
    }

    // --- Ротация: еженедельный "контракт недели" и ежедневная выборка (§1.1) ---

    private ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private LocalDate currentWeekMonday() {
        return LocalDate.now(zone()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private long weekEndMillis(LocalDate monday) {
        return monday.plusWeeks(1).atStartOfDay(zone()).toInstant().toEpochMilli();
    }

    public Optional<ClanContractDefinition> featuredWeekly() {
        if (weeklyOrder.isEmpty()) return Optional.empty();
        long weekIndex = currentWeekMonday().toEpochDay() / 7;
        String id = weeklyOrder.get((int) Math.floorMod(weekIndex, weeklyOrder.size()));
        return Optional.ofNullable(weeklyCatalog.get(id));
    }

    public synchronized List<ClanContractDefinition> dailyRotation() {
        long today = LocalDate.now(zone()).toEpochDay();
        if (today != cachedRotationDay) {
            recomputeDailyRotation(today);
        }
        return cachedDailyRotation.stream().map(dailyCatalog::get).filter(Objects::nonNull).toList();
    }

    private void recomputeDailyRotation(long epochDay) {
        if (dailyOrder.isEmpty()) {
            cachedDailyRotation = List.of();
            cachedRotationDay = epochDay;
            return;
        }
        Random random = new Random(epochDay);
        List<String> shuffled = new ArrayList<>(dailyOrder);
        Collections.shuffle(shuffled, random);
        int min = Math.max(1, plugin.getConfig().getInt("clans.contracts.daily.rotation-min", 3));
        int max = Math.max(min, plugin.getConfig().getInt("clans.contracts.daily.rotation-max", 5));
        int count = Math.min(shuffled.size(), min + (max > min ? random.nextInt(max - min + 1) : 0));
        cachedDailyRotation = List.copyOf(shuffled.subList(0, count));
        cachedRotationDay = epochDay;
    }

    // --- Выбор контракта ---

    public CompletableFuture<ClanQuestProgress> selectWeeklyAsync(Clan clan, UUID actorId) {
        if (clan == null || actorId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        }
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CONTRACTS)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (activeWeekly.containsKey(clan.id())) {
                throw new IllegalStateException("contract.already-active");
            }
            ClanContractDefinition definition = featuredWeekly().orElseThrow(() -> new IllegalStateException("contract.none-available"));
            double multiplier = difficultyMultiplier(clan);
            QuestObjective scaled = definition.objective().scaled(multiplier);
            long now = System.currentTimeMillis();
            return new ClanQuestProgress(clan.id(), ContractType.WEEKLY, definition.id(), scaled.getTargetAmount(),
                    Math.round(definition.baseRewardXp() * multiplier), 0, false, false, now, weekEndMillis(currentWeekMonday()));
        }).thenCompose(progress -> storage.saveContractProgressAsync(progress).thenApply(ignored -> {
            activeWeekly.put(clan.id(), progress);
            return progress;
        }));
    }

    public CompletableFuture<ClanQuestProgress> selectDailyAsync(Clan clan, UUID actorId, String contractId) {
        if (clan == null || actorId == null || contractId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and contract ID cannot be null."));
        }
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CONTRACTS)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (activeDaily.containsKey(clan.id())) {
                throw new IllegalStateException("contract.already-active");
            }
            ClanContractDefinition definition = dailyCatalog.get(contractId);
            if (definition == null || !dailyRotation().contains(definition)) {
                throw new IllegalStateException("contract.unknown");
            }
            double multiplier = difficultyMultiplier(clan);
            QuestObjective scaled = definition.objective().scaled(multiplier);
            long deadlineHours = plugin.getConfig().getLong("clans.contracts.daily.deadline-hours", 24);
            long now = System.currentTimeMillis();
            long expiresAt = now + Math.round(deadlineHours * multiplier * 60L * 60L * 1000L);
            return new ClanQuestProgress(clan.id(), ContractType.DAILY, definition.id(), scaled.getTargetAmount(),
                    Math.round(definition.baseRewardXp() * multiplier), 0, false, false, now, expiresAt);
        }).thenCompose(progress -> storage.saveContractProgressAsync(progress).thenApply(ignored -> {
            activeDaily.put(clan.id(), progress);
            return progress;
        }));
    }

    /** Called from event listeners; silently no-ops for whichever slot(s) don't have a matching active contract. */
    public void recordProgress(UUID clanId, UUID playerId, Map<String, Object> eventData) {
        updateSlot(activeWeekly, weeklyCatalog, ContractType.WEEKLY, clanId, playerId, eventData);
        updateSlot(activeDaily, dailyCatalog, ContractType.DAILY, clanId, playerId, eventData);
    }

    private void updateSlot(Map<UUID, ClanQuestProgress> active, Map<String, ClanContractDefinition> catalog,
                             ContractType type, UUID clanId, UUID playerId, Map<String, Object> eventData) {
        ClanQuestProgress progress = active.get(clanId);
        if (progress == null || progress.completed()) return;
        ClanContractDefinition definition = catalog.get(progress.questId());
        if (definition == null) return;

        int delta = definition.objective().updateProgress(clanId, playerId, progress.progress(), eventData);
        if (delta <= 0) return;

        int updatedAmount = progress.progress() + delta;
        boolean completed = updatedAmount >= progress.scaledTarget();
        ClanQuestProgress updated = progress.withProgress(updatedAmount).withCompleted(completed);
        active.put(clanId, updated);
        storage.saveContractProgressAsync(updated);

        if (completed) {
            plugin.getClanManager().getClanById(clanId).ifPresent(clan ->
                    plugin.runSync(() -> notifyOnline(clan, "contract.completed", Map.of("name", definition.displayName()))));
        }
    }

    // --- Сдача награды ---

    public CompletableFuture<Void> claimWeeklyAsync(Clan clan, UUID actorId) {
        return claim(activeWeekly, weeklyCatalog, ContractType.WEEKLY, clan, actorId);
    }

    public CompletableFuture<Void> claimDailyAsync(Clan clan, UUID actorId) {
        return claim(activeDaily, dailyCatalog, ContractType.DAILY, clan, actorId);
    }

    private CompletableFuture<Void> claim(Map<UUID, ClanQuestProgress> active, Map<String, ClanContractDefinition> catalog,
                                           ContractType type, Clan clan, UUID actorId) {
        if (clan == null || actorId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        }
        ClanQuestProgress progress = active.get(clan.id());
        if (progress == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("contract.none-active"));
        }
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CONTRACTS)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (!progress.completed()) {
                throw new IllegalStateException("contract.not-completed");
            }
            if (progress.claimed()) {
                throw new IllegalStateException("contract.already-claimed");
            }
            return progress;
        }).thenCompose(current -> grantAndClear(active, catalog, type, clan, current));
    }

    private CompletableFuture<Void> grantAndClear(Map<UUID, ClanQuestProgress> active, Map<String, ClanContractDefinition> catalog,
                                                    ContractType type, Clan clan, ClanQuestProgress progress) {
        ClanContractDefinition definition = catalog.get(progress.questId());
        CompletableFuture<Clan> rewardFuture = plugin.getClanManager().addExperienceAsync(clan, progress.scaledRewardXp());
        if (type == ContractType.WEEKLY) {
            int bonusPoints = plugin.getConfig().getInt("clans.contracts.weekly.reward-upgrade-points", 1);
            rewardFuture = rewardFuture.thenCompose(c -> {
                c.addUpgradePoints(bonusPoints);
                return plugin.getClanManager().updateClanAsync(c);
            });
        }
        return rewardFuture.thenCompose(c -> storage.deleteContractProgressAsync(clan.id(), type)).thenRun(() -> {
            active.remove(clan.id());
            if (definition != null) {
                plugin.runSync(() -> notifyOnline(clan, "contract.reward-claimed", Map.of("name", definition.displayName())));
            }
        });
    }

    // --- Истечение срока и штраф (§1.3) ---

    public void tickContracts() {
        tickSlot(activeWeekly, weeklyCatalog, ContractType.WEEKLY);
        tickSlot(activeDaily, dailyCatalog, ContractType.DAILY);
    }

    private void tickSlot(Map<UUID, ClanQuestProgress> active, Map<String, ClanContractDefinition> catalog, ContractType type) {
        long now = System.currentTimeMillis();
        for (ClanQuestProgress progress : List.copyOf(active.values())) {
            if (now < progress.expiresAt()) continue;
            Optional<Clan> clanOpt = plugin.getClanManager().getClanById(progress.clanId());
            if (clanOpt.isEmpty()) {
                active.remove(progress.clanId());
                storage.deleteContractProgressAsync(progress.clanId(), type);
                continue;
            }
            Clan clan = clanOpt.get();
            if (progress.completed() && !progress.claimed()) {
                // Grace: auto-claim so a clan doesn't lose an already-earned reward to a missed deadline.
                grantAndClear(active, catalog, type, clan, progress).exceptionally(t -> {
                    plugin.getLogger().warning("Failed to auto-claim expired " + type + " contract for clan " + clan.id() + ": " + t.getMessage());
                    return null;
                });
            } else if (!progress.completed()) {
                applyPenalty(active, catalog, type, clan, progress);
            } else {
                active.remove(progress.clanId());
                storage.deleteContractProgressAsync(progress.clanId(), type);
            }
        }
    }

    private void applyPenalty(Map<UUID, ClanQuestProgress> active, Map<String, ClanContractDefinition> catalog,
                               ContractType type, Clan clan, ClanQuestProgress progress) {
        ClanContractDefinition definition = catalog.get(progress.questId());
        int penaltyPercent = plugin.getConfig().getInt("clans.contracts.penalty-percent", 80);
        long penalty = Math.round(progress.scaledRewardXp() * (penaltyPercent / 100.0));
        plugin.getClanManager().removeExperienceAsync(clan, penalty)
                .thenCompose(c -> storage.deleteContractProgressAsync(clan.id(), type))
                .thenRun(() -> {
                    active.remove(clan.id());
                    if (definition != null) {
                        plugin.runSync(() -> notifyOnline(clan, "contract.failed",
                                Map.of("name", definition.displayName(), "penalty", String.valueOf(penalty))));
                    }
                }).exceptionally(t -> {
                    plugin.getLogger().warning("Failed to apply contract penalty for clan " + clan.id() + ": " + t.getMessage());
                    return null;
                });
    }

    private void notifyOnline(Clan clan, String key, Map<String, String> placeholders) {
        for (UUID memberId : clan.members().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                plugin.getMessages().send(member, key, placeholders);
            }
        }
    }
}
