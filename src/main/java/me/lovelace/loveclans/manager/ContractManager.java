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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Clan contracts ("обеты", §1). A clan can hold one WEEKLY (calendar week Mon-Sun) and one MONTHLY
 * (calendar month) contract at the same time. For each period the clan is offered a few contracts
 * to choose from ({@link OfferPicker}); the monthly pool is derived from the weekly one (bigger target,
 * bigger reward), so there is a single catalog to maintain in config.yml.
 *
 * Concurrency: the heavy parts are plain compare-and-set operations in {@link ContractSlots}. Taking a
 * contract reserves the slot on the main thread BEFORE the database write, a reward is paid by exactly one
 * caller, and progress writes are debounced ({@link #flushDirty}) instead of hitting the database on every
 * single block break.
 */
public final class ContractManager {

    /** A definition with the clan-size difficulty applied, ready to be shown or taken. */
    public record ScaledOffer(ClanContractDefinition definition, QuestObjective objective, int target, long rewardXp) {}

    /** All state of one contract type. */
    private static final class Track {
        final ContractType type;
        final Map<String, ClanContractDefinition> catalog;
        final ContractSlots slots = new ContractSlots();
        /** Clans whose in-memory progress is newer than what the database holds. */
        final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

        Track(ContractType type, Map<String, ClanContractDefinition> catalog) {
            this.type = type;
            this.catalog = Collections.unmodifiableMap(catalog);
        }
    }

    private static final int MAX_CHOICES = 3;

    private final LoveClansPlugin plugin;
    private final ClanStorage storage;
    private final Track weekly;
    private final Track monthly;

    public ContractManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        Map<String, ClanContractDefinition> weeklyCatalog = buildWeeklyCatalog();
        this.weekly = new Track(ContractType.WEEKLY, weeklyCatalog);
        this.monthly = new Track(ContractType.MONTHLY, buildMonthlyCatalog(weeklyCatalog));
    }

    // --- Catalog ---

    private Map<String, ClanContractDefinition> buildWeeklyCatalog() {
        Map<String, ClanContractDefinition> catalog = new LinkedHashMap<>();
        String path = "clans.contracts.weekly.pool";
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(path);
        if (root == null) {
            plugin.getLogger().warning("Missing contract pool config section: " + path + " - clans will have no contracts.");
            return catalog;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) continue;
            String name = entry.getString("name", id);
            String description = entry.getString("description", "");
            long rewardXp = entry.getLong("reward-xp", 0);
            ContractObjectiveFactory.build(plugin, id, entry.getConfigurationSection("objective")).ifPresentOrElse(
                    objective -> catalog.put(id, new ClanContractDefinition(id, ContractType.WEEKLY, name, description, objective, rewardXp)),
                    () -> plugin.getLogger().warning("Skipped invalid WEEKLY contract '" + id + "'."));
        }
        if (catalog.isEmpty()) {
            plugin.getLogger().warning("The weekly contract pool is empty - weekly AND monthly contracts are unavailable.");
        }
        return catalog;
    }

    /** Monthly contract = weekly contract with a bigger target and reward; there is no separate monthly pool to keep in sync. */
    private Map<String, ClanContractDefinition> buildMonthlyCatalog(Map<String, ClanContractDefinition> weeklyCatalog) {
        double targetMultiplier = Math.max(0.1, plugin.getConfig().getDouble("clans.contracts.monthly.target-multiplier", 4.0));
        double rewardMultiplier = Math.max(0.1, plugin.getConfig().getDouble("clans.contracts.monthly.reward-multiplier", 3.0));
        Map<String, ClanContractDefinition> catalog = new LinkedHashMap<>();
        for (ClanContractDefinition source : weeklyCatalog.values()) {
            String id = "m_" + source.id();
            catalog.put(id, new ClanContractDefinition(id, ContractType.MONTHLY, source.displayName(), source.description(),
                    source.objective().scaled(targetMultiplier), Math.round(source.baseRewardXp() * rewardMultiplier)));
        }
        return catalog;
    }

    private Track track(ContractType type) {
        return type == ContractType.MONTHLY ? monthly : weekly;
    }

    private Track[] tracks() {
        return new Track[]{weekly, monthly};
    }

    public Collection<ClanContractDefinition> catalog(ContractType type) {
        return track(type).catalog.values();
    }

    public Optional<ClanContractDefinition> definition(ContractType type, String id) {
        return Optional.ofNullable(track(type).catalog.get(id));
    }

    public Optional<ClanQuestProgress> active(UUID clanId, ContractType type) {
        return track(type).slots.get(clanId);
    }

    /** Cheap check for hot event listeners: with no active contract anywhere there is nothing to record. */
    public boolean hasAnyActive() {
        return !weekly.slots.isEmpty() || !monthly.slots.isEmpty();
    }

    public CompletableFuture<Void> loadAsync() {
        return loadTrack(weekly).thenCompose(v -> loadTrack(monthly));
    }

    private CompletableFuture<Void> loadTrack(Track track) {
        return storage.loadAllContractsAsync(track.type).thenAccept(all -> all.forEach(progress -> {
            if (track.catalog.containsKey(progress.questId())) {
                track.slots.put(progress);
            } else {
                plugin.getLogger().warning("Ignoring " + track.type + " contract '" + progress.questId()
                        + "' of clan " + progress.clanId() + ": it is no longer in the config pool.");
            }
        }));
    }

    // --- Difficulty (§1.2) ---

    private double difficultyMultiplier(Clan clan) {
        double base = plugin.getConfig().getDouble("clans.contracts.difficulty.base", 1.0);
        double perMemberStep = plugin.getConfig().getDouble("clans.contracts.difficulty.per-member-step", 0.15);
        int members = Math.max(1, clan.members().size());
        return base + (members - 1) * perMemberStep;
    }

    public ScaledOffer scale(Clan clan, ClanContractDefinition definition) {
        double multiplier = difficultyMultiplier(clan);
        QuestObjective scaled = definition.objective().scaled(multiplier);
        return new ScaledOffer(definition, scaled, scaled.getTargetAmount(), Math.round(definition.baseRewardXp() * multiplier));
    }

    /** Reconstructs a display-ready objective whose target matches the already-scaled snapshot stored on the progress. */
    public QuestObjective displayObjective(ClanContractDefinition definition, ClanQuestProgress progress) {
        int baseTarget = definition.objective().getTargetAmount();
        if (baseTarget <= 0 || progress.scaledTarget() == baseTarget) {
            return definition.objective();
        }
        return definition.objective().scaled(progress.scaledTarget() / (double) baseTarget);
    }

    // --- Periods and offers ---

    private ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public long periodStart(ContractType type) {
        return PeriodMath.startMillis(type, LocalDate.now(zone()), zone());
    }

    /** When the current period of this type ends (and the next offer appears). */
    public long periodEnd(ContractType type) {
        return PeriodMath.endMillis(type, LocalDate.now(zone()), zone());
    }

    /**
     * The contracts this clan may pick right now - same for every member and on every call during a period
     * (see {@link OfferPicker}). Fewer than three when the pool is small, empty when it is empty.
     */
    public List<ClanContractDefinition> offers(Clan clan, ContractType type) {
        Track track = track(type);
        int choices = Math.max(1, Math.min(MAX_CHOICES,
                plugin.getConfig().getInt("clans.contracts." + (type == ContractType.MONTHLY ? "monthly" : "weekly") + ".choices", MAX_CHOICES)));
        return OfferPicker.pick(track.catalog.keySet(), clan.id(), periodStart(type), type, choices).stream()
                .map(track.catalog::get)
                .toList();
    }

    // --- Taking a contract ---

    /**
     * Takes the chosen contract for the clan. The slot is reserved atomically on the main thread before the
     * database write, so two members cannot both take one; a failed write frees the slot again.
     */
    public CompletableFuture<ClanQuestProgress> selectAsync(Clan clan, UUID actorId, ContractType type, String contractId) {
        if (clan == null || actorId == null || type == null || contractId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor, type and contract ID cannot be null."));
        }
        Track track = track(type);
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CONTRACTS)) {
                throw new IllegalStateException("general.no-permission");
            }
            // An expired contract would otherwise block taking a new one until the next tick.
            settleExpired(track, clan.id(), System.currentTimeMillis());
            ClanContractDefinition definition = track.catalog.get(contractId);
            if (definition == null || offers(clan, type).stream().noneMatch(offer -> offer.id().equals(contractId))) {
                throw new IllegalStateException("contract.unknown");
            }
            ScaledOffer scaled = scale(clan, definition);
            ClanQuestProgress progress = new ClanQuestProgress(clan.id(), type, definition.id(), scaled.target(), scaled.rewardXp(),
                    0, false, false, System.currentTimeMillis(), periodEnd(type));
            if (!track.slots.reserve(progress)) {
                throw new IllegalStateException("contract.already-active");
            }
            return progress;
        }).thenCompose(progress -> storage.saveContractProgressAsync(progress).handle((ignored, error) -> {
            if (error != null) {
                track.slots.release(progress);
                throw new CompletionException(error);
            }
            return progress;
        }));
    }

    // --- Progress ---

    /**
     * Called from event listeners. {@code eventData} is only built when the clan really has an active
     * contract, so ordinary gameplay of clans without one costs nothing.
     */
    public void recordProgress(UUID clanId, UUID playerId, Supplier<Map<String, Object>> eventData) {
        boolean weeklyActive = weekly.slots.get(clanId).isPresent();
        boolean monthlyActive = monthly.slots.get(clanId).isPresent();
        if (!weeklyActive && !monthlyActive) return;
        Map<String, Object> data = eventData.get();
        if (weeklyActive) updateSlot(weekly, clanId, playerId, data);
        if (monthlyActive) updateSlot(monthly, clanId, playerId, data);
    }

    private void updateSlot(Track track, UUID clanId, UUID playerId, Map<String, Object> eventData) {
        // A lost compare-and-set means a claim or another update got in between: re-read and retry.
        for (int attempt = 0; attempt < 3; attempt++) {
            ClanQuestProgress progress = track.slots.get(clanId).orElse(null);
            // Progress made after the deadline must not complete the contract and earn the reward.
            if (progress == null || progress.completed() || System.currentTimeMillis() >= progress.expiresAt()) return;
            ClanContractDefinition definition = track.catalog.get(progress.questId());
            if (definition == null) return;

            int delta = definition.objective().updateProgress(clanId, playerId, progress.progress(), eventData);
            if (delta <= 0) return;

            int updatedAmount = Math.min(progress.progress() + delta, Math.max(progress.scaledTarget(), progress.progress()));
            boolean completed = updatedAmount >= progress.scaledTarget();
            ClanQuestProgress updated = progress.withProgress(updatedAmount).withCompleted(completed);
            if (!track.slots.replace(progress, updated)) continue;

            if (completed) {
                // Completion is saved at once (a restart must not take the reward away); ordinary progress is debounced.
                track.dirty.remove(clanId);
                storage.saveContractProgressAsync(updated).exceptionally(error -> {
                    plugin.getLogger().warning("Failed to save completed " + track.type + " contract for clan " + clanId + ": " + error.getMessage());
                    track.dirty.add(clanId);
                    return null;
                });
                plugin.getClanManager().getClanById(clanId).ifPresent(clan ->
                        plugin.runSync(() -> notifyOnline(clan, "contract.completed", Map.of("name", definition.displayName()))));
            } else {
                track.dirty.add(clanId);
            }
            return;
        }
    }

    /**
     * Writes progress that changed since the last flush. Runs on a timer
     * ({@code clans.contracts.progress-flush-seconds}) and on shutdown; a crash loses at most one interval of
     * progress instead of every block break costing a database write.
     */
    public void flushDirty() {
        for (Track track : tracks()) {
            if (track.dirty.isEmpty()) continue;
            for (UUID clanId : List.copyOf(track.dirty)) {
                if (!track.dirty.remove(clanId)) continue;
                track.slots.get(clanId).ifPresent(progress -> storage.saveContractProgressAsync(progress).exceptionally(error -> {
                    plugin.getLogger().warning("Failed to save " + track.type + " contract progress for clan " + clanId + ": " + error.getMessage());
                    track.dirty.add(clanId);
                    return null;
                }));
            }
        }
    }

    /** Forgets everything about a disbanded clan (its database rows go with the clan through ON DELETE CASCADE). */
    public void purgeClan(UUID clanId) {
        for (Track track : tracks()) {
            track.dirty.remove(clanId);
            track.slots.get(clanId).ifPresent(track.slots::remove);
        }
    }

    // --- Reward ---

    public CompletableFuture<Void> claimAsync(Clan clan, UUID actorId, ContractType type) {
        if (clan == null || actorId == null || type == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and type cannot be null."));
        }
        Track track = track(type);
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CONTRACTS)) {
                throw new IllegalStateException("general.no-permission");
            }
            ClanQuestProgress current = track.slots.get(clan.id()).orElseThrow(() -> new IllegalStateException("contract.none-active"));
            if (!current.completed()) {
                throw new IllegalStateException("contract.not-completed");
            }
            // The compare-and-set is the authority: exactly one of several simultaneous claims gets past it.
            return track.slots.tryClaim(clan.id()).orElseThrow(() -> new IllegalStateException("contract.already-claimed"));
        }).thenCompose(claimed -> payout(track, clan, claimed));
    }

    /**
     * Pays a reward that was already marked claimed. If paying fails BEFORE the experience is granted the
     * mark is rolled back so the clan can try again; once the experience went through the contract is
     * considered paid no matter what happens next (a leftover database row is cleaned up on the next tick),
     * so a clan can never be paid twice.
     */
    private CompletableFuture<Void> payout(Track track, Clan clan, ClanQuestProgress claimed) {
        ClanContractDefinition definition = track.catalog.get(claimed.questId());
        AtomicBoolean experienceGranted = new AtomicBoolean();
        int bonusPoints = Math.max(0, plugin.getConfig().getInt(
                "clans.contracts." + (track.type == ContractType.MONTHLY ? "monthly" : "weekly") + ".reward-upgrade-points",
                track.type == ContractType.MONTHLY ? 3 : 1));

        CompletableFuture<Clan> reward = plugin.getClanManager().addExperienceAsync(clan, claimed.scaledRewardXp()).thenApply(c -> {
            experienceGranted.set(true);
            return c;
        });
        if (bonusPoints > 0) {
            reward = reward.thenCompose(c -> {
                c.addUpgradePoints(bonusPoints);
                return plugin.getClanManager().updateClanAsync(c);
            });
        }
        return reward.thenCompose(c -> storage.deleteContractProgressAsync(clan.id(), track.type)).handle((ignored, error) -> {
            if (error != null && !experienceGranted.get()) {
                track.slots.unclaim(claimed);
                throw new CompletionException(error);
            }
            if (error != null) {
                plugin.getLogger().warning("Contract reward for clan " + clan.id() + " was paid but a follow-up step failed: " + error.getMessage());
            }
            track.slots.remove(claimed);
            track.dirty.remove(clan.id());
            if (definition != null) {
                plugin.runSync(() -> notifyOnline(clan, "contract.reward-claimed", Map.of("name", definition.displayName())));
            }
            return null;
        });
    }

    // --- Expiry and penalty (§1.3) ---

    /** Settles an expired contract of this clan right now (menus call it so a stale slot never blocks anything). */
    public void settleExpired(Clan clan) {
        long now = System.currentTimeMillis();
        for (Track track : tracks()) {
            settleExpired(track, clan.id(), now);
        }
    }

    public void tickContracts() {
        long now = System.currentTimeMillis();
        for (Track track : tracks()) {
            for (ClanQuestProgress progress : track.slots.snapshot()) {
                if (now >= progress.expiresAt()) settle(track, progress);
            }
        }
    }

    private void settleExpired(Track track, UUID clanId, long now) {
        track.slots.get(clanId).ifPresent(progress -> {
            if (now >= progress.expiresAt()) settle(track, progress);
        });
    }

    /**
     * Every branch starts with a compare-and-set that exactly one caller wins (claim or removal), so the tick
     * and a menu opening at the same moment cannot both pay a reward or both charge the penalty.
     */
    private void settle(Track track, ClanQuestProgress progress) {
        Optional<Clan> clanOpt = plugin.getClanManager().getClanById(progress.clanId());
        if (clanOpt.isEmpty()) {
            if (track.slots.remove(progress)) {
                track.dirty.remove(progress.clanId());
                storage.deleteContractProgressAsync(progress.clanId(), track.type);
            }
            return;
        }
        Clan clan = clanOpt.get();
        if (progress.completed() && !progress.claimed()) {
            // Grace: auto-claim so a clan doesn't lose an already-earned reward to a missed deadline.
            track.slots.tryClaim(progress.clanId()).ifPresent(claimed -> payout(track, clan, claimed).exceptionally(error -> {
                plugin.getLogger().warning("Failed to auto-claim expired " + track.type + " contract for clan " + clan.id() + ": " + error.getMessage());
                return null;
            }));
        } else if (!progress.completed()) {
            if (track.slots.remove(progress)) {
                track.dirty.remove(progress.clanId());
                applyPenalty(track, clan, progress);
            }
        } else if (track.slots.remove(progress)) {
            // Completed and already claimed: only a leftover row to clean up.
            track.dirty.remove(progress.clanId());
            storage.deleteContractProgressAsync(progress.clanId(), track.type);
        }
    }

    private void applyPenalty(Track track, Clan clan, ClanQuestProgress progress) {
        ClanContractDefinition definition = track.catalog.get(progress.questId());
        int penaltyPercent = plugin.getConfig().getInt("clans.contracts.penalty-percent", 80);
        long penalty = Math.round(progress.scaledRewardXp() * (penaltyPercent / 100.0));
        plugin.getClanManager().removeExperienceAsync(clan, penalty)
                .handle((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("Failed to apply contract penalty for clan " + clan.id() + ": " + error.getMessage());
                    }
                    return null;
                })
                .thenCompose(ignored -> storage.deleteContractProgressAsync(clan.id(), track.type))
                .thenRun(() -> {
                    if (definition != null) {
                        plugin.runSync(() -> notifyOnline(clan, "contract.failed",
                                Map.of("name", definition.displayName(), "penalty", String.valueOf(penalty))));
                    }
                }).exceptionally(error -> {
                    plugin.getLogger().warning("Failed to remove expired contract row for clan " + clan.id() + ": " + error.getMessage());
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
