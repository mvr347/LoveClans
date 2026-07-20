package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.objective.BountyTurnInObjective;
import me.lovelace.loveclans.model.quest.objective.KillMobObjective;
import me.lovelace.loveclans.model.quest.objective.MineAnyOreObjective;
import me.lovelace.loveclans.model.quest.reward.ClanBankReward;
import me.lovelace.loveclans.storage.ClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Weekly clan contracts ("обеты") picked by a clan leader at the Marshal NPC: one active contract
 * per clan at a time, progress fed by event listeners, reward claimed by the leader once complete.
 */
public final class ContractManager {

    private final LoveClansPlugin plugin;
    private final ClanStorage storage;
    private final Map<String, ClanContractDefinition> catalog = new LinkedHashMap<>();
    private final Map<UUID, ClanQuestProgress> activeByClan = new ConcurrentHashMap<>();

    public ContractManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        buildCatalog();
    }

    private void buildCatalog() {
        FileConfiguration cfg = plugin.getConfig();

        int oreTarget = cfg.getInt("clans.contracts.labor.target-ore", 5000);
        String laborFormat = cfg.getString("clans.contracts.labor.progress-format",
                "<gray>Добыто руды: <white>{current_progress}</white>/{target_amount}");
        catalog.put("labor", new ClanContractDefinition("labor",
                cfg.getString("clans.contracts.labor.name", "Обет Труда"),
                cfg.getString("clans.contracts.labor.description", "Суммарно всем кланом накопать 5000 единиц руды."),
                new MineAnyOreObjective(oreTarget, laborFormat),
                new ClanBankReward(cfg.getString("clans.contracts.labor.reward-item", "currency:gold_coin"),
                        cfg.getLong("clans.contracts.labor.reward-amount", 5000))));

        int bountyTarget = cfg.getInt("clans.contracts.blood.target-kills", 10);
        String bloodFormat = cfg.getString("clans.contracts.blood.progress-format",
                "<gray>Сдано наёмников: <white>{current_progress}</white>/{target_amount}");
        catalog.put("blood", new ClanContractDefinition("blood",
                cfg.getString("clans.contracts.blood.name", "Обет Крови"),
                cfg.getString("clans.contracts.blood.description", "Сдать 10 голов игроков из враждебных кланов через bounty."),
                new BountyTurnInObjective(bountyTarget, bloodFormat),
                new ClanBankReward(cfg.getString("clans.contracts.blood.reward-item", "currency:gold_coin"),
                        cfg.getLong("clans.contracts.blood.reward-amount", 8000))));

        EntityType bossType;
        try {
            bossType = EntityType.valueOf(cfg.getString("clans.contracts.purification.boss-type", "WITHER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            bossType = EntityType.WITHER;
        }
        int bossTarget = cfg.getInt("clans.contracts.purification.target-kills", 5);
        String purificationFormat = cfg.getString("clans.contracts.purification.progress-format",
                "<gray>Уничтожено боссов: <white>{current_progress}</white>/{target_amount}");
        catalog.put("purification", new ClanContractDefinition("purification",
                cfg.getString("clans.contracts.purification.name", "Обет Очищения"),
                cfg.getString("clans.contracts.purification.description", "Уничтожить опасного босса всем кланом несколько раз."),
                new KillMobObjective(bossType, bossTarget, purificationFormat),
                new ClanBankReward(cfg.getString("clans.contracts.purification.reward-item", "currency:gold_coin"),
                        cfg.getLong("clans.contracts.purification.reward-amount", 10000))));
    }

    public Collection<ClanContractDefinition> catalog() {
        return catalog.values();
    }

    public Optional<ClanContractDefinition> definition(String id) {
        return Optional.ofNullable(catalog.get(id));
    }

    public Optional<ClanQuestProgress> activeContract(UUID clanId) {
        return Optional.ofNullable(activeByClan.get(clanId));
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllContractsAsync().thenAccept(all ->
                all.forEach(progress -> activeByClan.put(progress.clanId(), progress)));
    }

    private long weekMillis() {
        return TimeUnit.DAYS.toMillis(7);
    }

    /** Leader-only: picks a new contract, replacing any previous one once a week has elapsed since it started. */
    public CompletableFuture<ClanQuestProgress> selectContractAsync(Clan clan, UUID actorId, String contractId) {
        if (clan == null || actorId == null || contractId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and contract ID cannot be null."));
        }
        ClanContractDefinition definition = catalog.get(contractId);
        if (definition == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("contract.unknown"));
        }
        return plugin.supplySync(() -> {
            boolean isLeader = clan.member(actorId).map(m -> m.rank() == ClanRank.LEADER).orElse(false);
            if (!isLeader) {
                throw new IllegalStateException("general.no-permission");
            }
            ClanQuestProgress existing = activeByClan.get(clan.id());
            if (existing != null && System.currentTimeMillis() - existing.lastReset() < weekMillis()) {
                throw new IllegalStateException("contract.already-active");
            }
            return new ClanQuestProgress(clan.id(), contractId, Map.of(), false, false, System.currentTimeMillis());
        }).thenCompose(progress -> storage.saveContractProgressAsync(progress).thenApply(ignored -> {
            activeByClan.put(clan.id(), progress);
            return progress;
        }));
    }

    /** Called from event listeners; silently no-ops if the clan has no matching active contract. */
    public void recordProgress(UUID clanId, UUID playerId, Map<String, Object> eventData) {
        ClanQuestProgress progress = activeByClan.get(clanId);
        if (progress == null || progress.completed()) {
            return;
        }
        ClanContractDefinition definition = catalog.get(progress.questId());
        if (definition == null) {
            return;
        }

        int current = progress.objectiveProgress().getOrDefault(0, 0);
        int delta = definition.objective().updateProgress(clanId, playerId, current, eventData);
        if (delta <= 0) {
            return;
        }

        int updatedAmount = current + delta;
        boolean completed = definition.objective().isCompleted(updatedAmount);
        ClanQuestProgress updated = progress.withObjectiveProgress(0, updatedAmount).withCompleted(completed);
        activeByClan.put(clanId, updated);
        storage.saveContractProgressAsync(updated);

        if (completed) {
            plugin.getClanManager().getClanById(clanId).ifPresent(clan ->
                    plugin.runSync(() -> notifyCompletion(clan, definition)));
        }
    }

    private void notifyCompletion(Clan clan, ClanContractDefinition definition) {
        for (UUID memberId : clan.members().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                plugin.getMessages().send(member, "contract.completed", Map.of("name", definition.displayName()));
            }
        }
    }

    /** Leader-only: claims the reward for a completed, not-yet-claimed contract. */
    public CompletableFuture<Void> claimRewardAsync(Clan clan, UUID actorId) {
        if (clan == null || actorId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        }
        ClanQuestProgress progress = activeByClan.get(clan.id());
        if (progress == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("contract.none-active"));
        }
        return plugin.supplySync(() -> {
            boolean isLeader = clan.member(actorId).map(m -> m.rank() == ClanRank.LEADER).orElse(false);
            if (!isLeader) {
                throw new IllegalStateException("general.no-permission");
            }
            if (!progress.completed()) {
                throw new IllegalStateException("contract.not-completed");
            }
            if (progress.claimed()) {
                throw new IllegalStateException("contract.already-claimed");
            }
            return progress;
        }).thenCompose(current -> {
            ClanContractDefinition definition = catalog.get(current.questId());
            if (definition != null) {
                definition.reward().giveReward(plugin, Bukkit.getPlayer(actorId), clan);
            }
            ClanQuestProgress claimedProgress = current.withClaimed(true);
            activeByClan.put(clan.id(), claimedProgress);
            return storage.saveContractProgressAsync(claimedProgress);
        });
    }
}
