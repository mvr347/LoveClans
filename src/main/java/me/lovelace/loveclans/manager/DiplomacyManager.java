package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.diplomacy.ClanPact;
import me.lovelace.loveclans.model.diplomacy.ClanRelationRecord;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import me.lovelace.loveclans.model.diplomacy.PactType;
import me.lovelace.loveclans.api.events.ClanDiplomacyChangeEvent;
import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import me.lovelace.loveclans.storage.ClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Эмбарго, блокада, пакты (NAP/торговля/союз) и шкала отношений score (-100..+100) между кланами (§1, §5).
 */
public final class DiplomacyManager {
    private final LoveClansPlugin plugin;
    private final ClanStorage storage;

    // Эмбарго — взаимное, храним один канонический ключ на пару (меньший UUID первым).
    private final Set<AbstractMap.SimpleImmutableEntry<UUID, UUID>> embargoes = ConcurrentHashMap.newKeySet();
    // Блокада — односторонняя: blocker -> множество клановых ID, которые он блокирует.
    private final Map<UUID, Set<UUID>> blockades = new ConcurrentHashMap<>();
    // Пакты (соглашения) между кланами
    private final Map<UUID, ClanPact> pactsById = new ConcurrentHashMap<>();

    // Кулдауны дипломатических действий (source, target, action) -> expiresAt
    private record ActionCooldownKey(UUID source, UUID target, String action) {}
    private final Map<ActionCooldownKey, Long> actionCooldowns = new ConcurrentHashMap<>();

    // Входящие предложения пактов (from, to, type) -> expiresAt
    private record PactOfferKey(UUID fromClan, UUID toClan, PactType type) {}
    private final Map<PactOfferKey, Long> pendingPactOffers = new ConcurrentHashMap<>();

    public DiplomacyManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        reloadTiers();
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllEmbargoesAsync().thenAccept(pairs -> {
            embargoes.clear();
            embargoes.addAll(pairs);
        }).thenCompose(v -> storage.loadAllBlockadesAsync()).thenAccept(pairs -> {
            blockades.clear();
            for (AbstractMap.SimpleImmutableEntry<UUID, UUID> pair : pairs) {
                blockades.computeIfAbsent(pair.getKey(), k -> ConcurrentHashMap.newKeySet()).add(pair.getValue());
            }
        }).thenCompose(v -> storage.loadAllPactsAsync()).thenCompose(loadedPacts -> plugin.supplySync(() -> {
            pactsById.clear();
            long now = System.currentTimeMillis();
            Set<String> seen = new java.util.HashSet<>();
            for (ClanPact pact : loadedPacts) {
                String a = pact.firstClanId().toString();
                String b = pact.secondClanId().toString();
                String pair = a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
                if (pact.isExpired(now) || !seen.add(pair + ":" + pact.type())) {
                    // Expired pact, or a duplicate row (an alliance used to be saved 2-3 times): drop it for good
                    storage.deletePactAsync(pact.id());
                    continue;
                }
                pactsById.put(pact.id(), pact);
                plugin.getClanManager().getClanById(pact.firstClanId()).ifPresent(c -> c.addPact(pact.secondClanId(), pact.type()));
                plugin.getClanManager().getClanById(pact.secondClanId()).ifPresent(c -> c.addPact(pact.firstClanId(), pact.type()));
            }
            return null;
        })).thenCompose(v -> storage.loadAllRelationsAsync()).thenCompose(loadedRelations -> plugin.supplySync(() -> {
            for (ClanRelationRecord record : loadedRelations) {
                plugin.getClanManager().getClanById(record.sourceClanId()).ifPresent(c -> c.setRelation(record));
            }
            return null;
        }));
    }

    /** Пороги тиров берутся из config.yml (diplomacy.tiers: нижняя граница каждого диапазона). */
    public void reloadTiers() {
        var cfg = plugin.getConfig();
        DiplomacyTier.configure(
                cfg.getIntegerList("diplomacy.tiers.unfriendly").stream().findFirst().orElse(-59),
                cfg.getIntegerList("diplomacy.tiers.neutral").stream().findFirst().orElse(-19),
                cfg.getIntegerList("diplomacy.tiers.cordial").stream().findFirst().orElse(20),
                cfg.getIntegerList("diplomacy.tiers.friendly").stream().findFirst().orElse(60),
                cfg.getIntegerList("diplomacy.tiers.allied-ready").stream().findFirst().orElse(90));
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> embargoKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new AbstractMap.SimpleImmutableEntry<>(a, b) : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    // --- Эмбарго (§5.2) ---

    public boolean isEmbargoed(UUID clanA, UUID clanB) {
        return embargoes.contains(embargoKey(clanA, clanB));
    }

    public Set<UUID> getEmbargoesForClan(UUID clanId) {
        Set<UUID> result = new java.util.HashSet<>();
        for (AbstractMap.SimpleImmutableEntry<UUID, UUID> pair : embargoes) {
            if (pair.getKey().equals(clanId)) {
                result.add(pair.getValue());
            } else if (pair.getValue().equals(clanId)) {
                result.add(pair.getKey());
            }
        }
        return result;
    }

    public CompletableFuture<Void> declareEmbargoAsync(Clan source, UUID actorId, Clan target) {
        return plugin.supplySync(() -> {
            ClanManager.requireTerritory(source, target);
            if (!source.isRecognized() || !target.isRecognized()) {
                throw new IllegalStateException("deny.diplomacy.need-recognized");
            }
            if (!source.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (source.id().equals(target.id())) {
                throw new IllegalStateException("general.error");
            }
            if (plugin.getClanManager().inConflictWith(source.id(), target.id())) {
                throw new IllegalStateException("diplomacy.conflict-in-progress");
            }
            if (isEmbargoed(source.id(), target.id())) {
                throw new IllegalStateException("diplomacy.embargo.already-active");
            }
            requireAllowed(DiplomacyActions.Action.EMBARGO, source, target);
            return embargoKey(source.id(), target.id());
        }).thenCompose(key -> {
            embargoes.add(key);
            adjustPairScore(source.id(), target.id(), plugin.getConfig().getInt("diplomacy.events.embargo-declared", -10));
            return storage.saveEmbargoAsync(key.getKey(), key.getValue());
        }).thenRun(() -> {
            plugin.getClanManager().getOnlineMembersWithPermission(source, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.embargo.declared", Map.of("tag", target.tag(), "color", target.tagColor())));
            plugin.getClanManager().getOnlineMembersWithPermission(target, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.embargo.declared", Map.of("tag", source.tag(), "color", source.tagColor())));
        });
    }

    public CompletableFuture<Void> cancelEmbargoAsync(Clan source, UUID actorId, Clan target) {
        return plugin.supplySync(() -> {
            if (!source.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (!isEmbargoed(source.id(), target.id())) {
                throw new IllegalStateException("diplomacy.embargo.not-active");
            }
            return embargoKey(source.id(), target.id());
        }).thenCompose(key -> {
            embargoes.remove(key);
            adjustPairScore(source.id(), target.id(), plugin.getConfig().getInt("diplomacy.events.sanction-lifted", 5));
            return storage.deleteEmbargoAsync(key.getKey(), key.getValue());
        }).thenRun(() -> {
            plugin.getClanManager().getOnlineMembersWithPermission(source, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.embargo.cancelled", Map.of("tag", target.tag(), "color", target.tagColor())));
            plugin.getClanManager().getOnlineMembersWithPermission(target, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.embargo.cancelled", Map.of("tag", source.tag(), "color", source.tagColor())));
        });
    }

    // --- Блокада (§5.3) ---

    public boolean isBlockading(UUID blockerClanId, UUID blockedClanId) {
        return blockades.getOrDefault(blockerClanId, Set.of()).contains(blockedClanId);
    }

    public boolean isBlockaded(UUID clanId) {
        return blockades.values().stream().anyMatch(set -> set.contains(clanId));
    }

    public Optional<UUID> blockaderOf(UUID blockedClanId) {
        return blockades.entrySet().stream()
                .filter(e -> e.getValue().contains(blockedClanId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Set<UUID> getOutgoingBlockades(UUID clanId) {
        Set<UUID> set = blockades.get(clanId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public Set<UUID> getIncomingBlockades(UUID clanId) {
        Set<UUID> result = new java.util.HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : blockades.entrySet()) {
            if (entry.getValue().contains(clanId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean canBlockade(Clan blocker, Clan blocked) {
        double multiplier = plugin.getConfig().getDouble("influence.blockade-multiplier", 1.5);
        return blocker.influence() >= blocked.influence() * multiplier;
    }

    public CompletableFuture<Void> declareBlockadeAsync(Clan blocker, UUID actorId, Clan blocked) {
        return plugin.supplySync(() -> {
            ClanManager.requireTerritory(blocker, blocked);
            if (!blocker.isRecognized() || !blocked.isRecognized()) {
                throw new IllegalStateException("deny.diplomacy.need-recognized");
            }
            if (blocker.member(actorId).map(m -> m.rank() != ClanRank.LEADER).orElse(true)) {
                throw new IllegalStateException("diplomacy.blockade.leader-only");
            }
            if (blocker.id().equals(blocked.id())) {
                throw new IllegalStateException("general.error");
            }
            if (plugin.getClanManager().inConflictWith(blocker.id(), blocked.id())) {
                throw new IllegalStateException("diplomacy.conflict-in-progress");
            }
            if (isBlockading(blocker.id(), blocked.id())) {
                throw new IllegalStateException("diplomacy.blockade.already-active");
            }
            if (plugin.getConfig().getBoolean("diplomacy.blockade-exclusive", true)) {
                Optional<UUID> currentBlockader = blockaderOf(blocked.id());
                if (currentBlockader.isPresent() && !currentBlockader.get().equals(blocker.id())) {
                    throw new IllegalStateException("deny.blockade.already-blockaded-by-other");
                }
            }
            if (plugin.getModifierManager().hasGuildShield(blocked.id())) {
                throw new IllegalStateException("deny.guild-shield.active");
            }
            requireAllowed(DiplomacyActions.Action.BLOCKADE, blocker, blocked);
            if (!canBlockade(blocker, blocked)) {
                throw new IllegalStateException("diplomacy.blockade.insufficient-influence");
            }
            return null;
        }).thenCompose(ignored -> {
            blockades.computeIfAbsent(blocker.id(), k -> ConcurrentHashMap.newKeySet()).add(blocked.id());
            adjustPairScore(blocker.id(), blocked.id(), plugin.getConfig().getInt("diplomacy.events.blockade-declared", -20));
            return storage.saveBlockadeAsync(blocker.id(), blocked.id());
        }).thenRun(() -> {
            plugin.getClanManager().getOnlineMembersWithPermission(blocker, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.blockade.declared", Map.of("tag", blocked.tag(), "color", blocked.tagColor())));
            onlineMembers(blocked).forEach(p -> plugin.getMessages().send(p, "diplomacy.blockade.received", Map.of("tag", blocker.tag(), "color", blocker.tagColor())));
        });
    }

    public CompletableFuture<Void> cancelBlockadeAsync(Clan blocker, UUID actorId, Clan blocked) {
        return plugin.supplySync(() -> {
            if (blocker.member(actorId).map(m -> m.rank() != ClanRank.LEADER).orElse(true)) {
                throw new IllegalStateException("diplomacy.blockade.leader-only");
            }
            if (!isBlockading(blocker.id(), blocked.id())) {
                throw new IllegalStateException("diplomacy.blockade.not-active");
            }
            return null;
        }).thenCompose(ignored -> {
            adjustPairScore(blocker.id(), blocked.id(), plugin.getConfig().getInt("diplomacy.events.sanction-lifted", 5));
            return liftBlockade(blocker.id(), blocked.id());
        }).thenRun(() -> {
            plugin.getClanManager().getOnlineMembersWithPermission(blocker, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "diplomacy.blockade.cancelled", Map.of("tag", blocked.tag(), "color", blocked.tagColor())));
            onlineMembers(blocked).forEach(p -> plugin.getMessages().send(p, "diplomacy.blockade.lifted", Map.of("tag", blocker.tag(), "color", blocker.tagColor())));
        });
    }

    private CompletableFuture<Void> liftBlockade(UUID blockerClanId, UUID blockedClanId) {
        Set<UUID> set = blockades.get(blockerClanId);
        if (set != null) {
            set.remove(blockedClanId);
        }
        return storage.deleteBlockadeAsync(blockerClanId, blockedClanId);
    }

    /** Called by WarManager when a war is declared between two clans - a blockade in either direction is lifted immediately. */
    public void liftBlockadesBetween(UUID clanA, UUID clanB) {
        if (isBlockading(clanA, clanB)) {
            liftBlockade(clanA, clanB).exceptionally(t -> {
                plugin.getLogger().warning("Failed to auto-lift blockade " + clanA + " -> " + clanB + ": " + t.getMessage());
                return null;
            });
        }
        if (isBlockading(clanB, clanA)) {
            liftBlockade(clanB, clanA).exceptionally(t -> {
                plugin.getLogger().warning("Failed to auto-lift blockade " + clanB + " -> " + clanA + ": " + t.getMessage());
                return null;
            });
        }
    }

    /** Called by WarManager at war end - if the loser was blockading the winner, "rising against the oppressor" lifts it. */
    public void liftBlockadeOnVictory(UUID winnerClanId, UUID loserClanId) {
        if (!isBlockading(loserClanId, winnerClanId)) {
            return;
        }
        liftBlockade(loserClanId, winnerClanId).thenRun(() -> {
            plugin.getClanManager().getClanById(winnerClanId).ifPresent(winner ->
                    onlineMembers(winner).forEach(p -> plugin.getMessages().send(p, "diplomacy.blockade.overthrown")));
        }).exceptionally(t -> {
            plugin.getLogger().warning("Failed to lift blockade on victory " + winnerClanId + " over " + loserClanId + ": " + t.getMessage());
            return null;
        });
    }



    // --- Пакты (соглашения) (§5) ---

    public boolean hasPact(UUID clanA, UUID clanB, PactType type) {
        long now = System.currentTimeMillis();
        return pactsById.values().stream()
                .anyMatch(p -> p.type() == type && p.matchesPair(clanA, clanB) && !p.isExpired(now));
    }

    public Optional<ClanPact> getPact(UUID clanA, UUID clanB, PactType type) {
        long now = System.currentTimeMillis();
        return pactsById.values().stream()
                .filter(p -> p.type() == type && p.matchesPair(clanA, clanB) && !p.isExpired(now))
                .findFirst();
    }

    public Collection<ClanPact> getPactsForClan(UUID clanId) {
        long now = System.currentTimeMillis();
        return pactsById.values().stream()
                .filter(p -> p.involves(clanId) && !p.isExpired(now))
                .toList();
    }

    public CompletableFuture<Void> addPactAsync(ClanPact pact) {
        pactsById.put(pact.id(), pact);
        plugin.getClanManager().getClanById(pact.firstClanId()).ifPresent(c -> c.addPact(pact.secondClanId(), pact.type()));
        plugin.getClanManager().getClanById(pact.secondClanId()).ifPresent(c -> c.addPact(pact.firstClanId(), pact.type()));
        return storage.savePactAsync(pact);
    }

    public CompletableFuture<Void> removePactAsync(UUID pactId) {
        ClanPact pact = pactsById.remove(pactId);
        if (pact != null) {
            plugin.getClanManager().getClanById(pact.firstClanId()).ifPresent(c -> c.removePact(pact.secondClanId(), pact.type()));
            plugin.getClanManager().getClanById(pact.secondClanId()).ifPresent(c -> c.removePact(pact.firstClanId(), pact.type()));
        }
        return storage.deletePactAsync(pactId);
    }

    public CompletableFuture<Void> removePactAsync(UUID clanA, UUID clanB, PactType type) {
        Optional<ClanPact> existing = getPact(clanA, clanB, type);
        if (existing.isPresent()) {
            return removePactAsync(existing.get().id());
        }
        return CompletableFuture.completedFuture(null);
    }

    // --- Отношения (общий score пары, §1) ---
    // Один score на пару кланов: в Clan.relations лежат две зеркальные записи с одинаковым значением.
    // Все изменения идут только через commitPairRecord / adjustPairScore на главном потоке.

    public int getScore(UUID sourceClanId, UUID targetClanId) {
        return plugin.getClanManager().getClanById(sourceClanId)
                .map(c -> c.relationScore(targetClanId))
                .orElse(0);
    }

    public ClanRelationRecord getRelation(UUID sourceClanId, UUID targetClanId) {
        return plugin.getClanManager().getClanById(sourceClanId)
                .map(c -> c.getRelation(targetClanId))
                .orElseGet(() -> ClanRelationRecord.createDefault(sourceClanId, targetClanId));
    }

    public void adjustPairScore(UUID clanA, UUID clanB, int delta) {
        if (delta == 0) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            plugin.runSync(() -> adjustPairScore(clanA, clanB, delta));
            return;
        }
        setPairScore(clanA, clanB, getScore(clanA, clanB) + delta);
    }

    public void setPairScore(UUID clanA, UUID clanB, int newScore) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.runSync(() -> setPairScore(clanA, clanB, newScore));
            return;
        }
        Optional<Clan> a = plugin.getClanManager().getClanById(clanA);
        if (a.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        commitPairRecord(clanA, clanB, a.get().getRelation(clanB).withScore(newScore, now));
    }

    /**
     * Записывает запись стороны clanA и её зеркало для clanB: пара всегда видит одно число.
     * Вызывается на главном потоке. Сохранение пары идёт одной транзакцией.
     */
    public void commitPairRecord(UUID clanA, UUID clanB, ClanRelationRecord recordOfA) {
        Optional<Clan> aOpt = plugin.getClanManager().getClanById(clanA);
        Optional<Clan> bOpt = plugin.getClanManager().getClanById(clanB);
        if (aOpt.isEmpty() || bOpt.isEmpty() || clanA.equals(clanB)) {
            return;
        }
        Clan a = aOpt.get();
        Clan b = bOpt.get();
        int minScore = plugin.getConfig().getInt("diplomacy.score-min", ClanRelationRecord.MIN_SCORE);
        int maxScore = plugin.getConfig().getInt("diplomacy.score-max", ClanRelationRecord.MAX_SCORE);
        int clamped = Math.max(minScore, Math.min(maxScore, recordOfA.score()));
        ClanRelationRecord mine = new ClanRelationRecord(clanA, clanB, clamped, recordOfA.score24hAgo(),
                recordOfA.lastTrendUpdate(), recordOfA.updatedAt());
        ClanRelationRecord mirror = new ClanRelationRecord(clanB, clanA, clamped, mine.score24hAgo(),
                mine.lastTrendUpdate(), mine.updatedAt());

        boolean alliance = a.hasPactWith(clanB, PactType.ALLIANCE);
        DiplomacyTier before = DiplomacyTier.fromScore(a.relationScore(clanB), alliance);
        a.setRelation(mine);
        b.setRelation(mirror);
        DiplomacyTier after = DiplomacyTier.fromScore(clamped, alliance);

        storage.saveRelationPairAsync(mine, mirror).exceptionally(t -> {
            plugin.getLogger().warning("Не удалось сохранить отношения " + a.tag() + " и " + b.tag() + ": " + t.getMessage());
            return null;
        });

        enforcePactThresholds(a, b, clamped);

        if (before != after) {
            Bukkit.getPluginManager().callEvent(new ClanDiplomacyChangeEvent(clanA, clanB,
                    after.toLegacyRelation(), before.toLegacyRelation()));
            notifyTierChange(a, b, before, after);
        }
    }

    private String tierTitle(DiplomacyTier tier) {
        return plugin.getMessages().raw("diplomacy.tier." + tier.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void notifyTierChange(Clan a, Clan b, DiplomacyTier before, DiplomacyTier after) {
        String key = after.ordinal() > before.ordinal() ? "diplomacy.tier-improved" : "diplomacy.tier-worsened";
        for (Clan[] sides : new Clan[][]{{a, b}, {b, a}}) {
            Clan self = sides[0];
            Clan other = sides[1];
            Map<String, String> placeholders = Map.of("tag", other.tag(), "color", other.tagColor(),
                    "tier", tierTitle(after), "old-tier", tierTitle(before));
            plugin.getClanManager().getOnlineMembersWithPermission(self, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, key, placeholders));
        }
    }

    /** Пакт держится, пока отношения выше порога минус запас; ниже — расторгается без штрафа (отношения уже упали). */
    private void enforcePactThresholds(Clan a, Clan b, int score) {
        int margin = plugin.getConfig().getInt("diplomacy.pacts.auto-break-margin", 20);
        for (PactType type : PactType.values()) {
            if (score >= minScore(type) - margin) {
                continue;
            }
            getPact(a.id(), b.id(), type).ifPresent(pact -> {
                removePactAsync(pact.id()).exceptionally(t -> null);
                for (Clan[] sides : new Clan[][]{{a, b}, {b, a}}) {
                    Map<String, String> placeholders = Map.of("type", type.displayName(),
                            "tag", sides[1].tag(), "color", sides[1].tagColor());
                    plugin.getClanManager().getOnlineMembersWithPermission(sides[0], ClanPermission.DIPLOMACY)
                            .forEach(p -> plugin.getMessages().send(p, "diplomacy.pact.auto-broken", placeholders));
                }
            });
        }
    }

    public int minScore(PactType type) {
        return switch (type) {
            case NAP -> plugin.getConfig().getInt("diplomacy.pacts.nap-min-score", -10);
            case TRADE -> plugin.getConfig().getInt("diplomacy.pacts.trade-min-score", 20);
            case ALLIANCE -> plugin.getConfig().getInt("diplomacy.pacts.alliance-min-score", 90);
        };
    }

    /** Состояние пары для матрицы действий (используется и GUI, и проверками менеджеров). */
    public DiplomacyActions.Context contextOf(Clan source, Clan target) {
        UUID t = target.id();
        boolean alliance = hasPact(source.id(), t, PactType.ALLIANCE);
        int score = source.relationScore(t);
        return new DiplomacyActions.Context(DiplomacyTier.fromScore(score, alliance), score,
                hasPact(source.id(), t, PactType.NAP), hasPact(source.id(), t, PactType.TRADE), alliance);
    }

    /** Бросает IllegalStateException с ключом причины, если матрица не разрешает действие этой паре. */
    public void requireAllowed(DiplomacyActions.Action action, Clan source, Clan target) {
        PactType pact = switch (action) {
            case NAP -> PactType.NAP;
            case TRADE_PACT -> PactType.TRADE;
            case ALLIANCE -> PactType.ALLIANCE;
            default -> null;
        };
        String deny = DiplomacyActions.denyKey(action, contextOf(source, target), pact == null ? 0 : minScore(pact));
        if (deny != null) {
            throw new IllegalStateException(deny);
        }
    }

    /** Враждебный тир: торговля и дары между кланами запрещены. */
    public boolean isHostile(UUID clanA, UUID clanB) {
        return plugin.getClanManager().getClanById(clanA)
                .map(c -> c.tierTo(clanB) == DiplomacyTier.HOSTILE).orElse(false);
    }

    // --- Кулдауны действий и предложения пактов (§3, §5) ---

    public boolean isActionOnCooldown(UUID source, UUID target, String action) {
        Long exp = actionCooldowns.get(new ActionCooldownKey(source, target, action));
        return exp != null && System.currentTimeMillis() < exp;
    }

    public long getActionCooldownRemaining(UUID source, UUID target, String action) {
        Long exp = actionCooldowns.get(new ActionCooldownKey(source, target, action));
        return exp != null ? Math.max(0L, exp - System.currentTimeMillis()) : 0L;
    }

    public void setActionCooldown(UUID source, UUID target, String action, long durationMillis) {
        actionCooldowns.put(new ActionCooldownKey(source, target, action), System.currentTimeMillis() + durationMillis);
    }

    public boolean hasPendingPactOffer(UUID fromClan, UUID toClan, PactType type) {
        Long exp = pendingPactOffers.get(new PactOfferKey(fromClan, toClan, type));
        return exp != null && System.currentTimeMillis() < exp;
    }

    public void removePendingPactOffer(UUID fromClan, UUID toClan, PactType type) {
        pendingPactOffers.remove(new PactOfferKey(fromClan, toClan, type));
    }

    /** Чистит просроченные кулдауны, предложения и суточные счётчики (раньше эти карты росли без предела). */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        actionCooldowns.values().removeIf(exp -> exp <= now);
        pendingPactOffers.values().removeIf(exp -> exp <= now);
        long today = now / 86_400_000L;
        dailyDelta.values().removeIf(entry -> entry.day() != today);
    }

    /** Убирает все следы распущенного клана: пакты, эмбарго, блокады, предложения, записи отношений у остальных. */
    public CompletableFuture<Void> purgeClan(UUID clanId) {
        return plugin.supplySync(() -> {
            pactsById.values().removeIf(p -> p.involves(clanId));
            embargoes.removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
            blockades.remove(clanId);
            blockades.values().forEach(set -> set.remove(clanId));
            actionCooldowns.keySet().removeIf(k -> k.source().equals(clanId) || k.target().equals(clanId));
            pendingPactOffers.keySet().removeIf(k -> k.fromClan().equals(clanId) || k.toClan().equals(clanId));
            dailyDelta.keySet().removeIf(k -> k.contains(clanId.toString()));
            for (Clan other : plugin.getClanManager().getAllClans()) {
                other.forgetClan(clanId);
            }
            return null;
        }).thenCompose(v -> storage.purgeClanDiplomacyAsync(clanId));
    }

    // --- Платные жесты (§3) ---

    public CompletableFuture<Void> improveRelationsAsync(Player player, Clan source, Clan target) {
        return plugin.supplySync(() -> {
            requireDiplomat(player, source, target);
            requireAllowed(DiplomacyActions.Action.IMPROVE, source, target);
            if (isActionOnCooldown(source.id(), target.id(), "improve")) {
                throw new IllegalStateException("diplomacy.improve.cooldown");
            }
            long cost = plugin.getConfig().getLong("diplomacy.actions.improve-cost", 500L);
            if (source.chestMoney() < cost) {
                throw new IllegalStateException("diplomacy.improve.not-enough-money");
            }
            long cooldownHours = plugin.getConfig().getLong("diplomacy.actions.improve-cooldown-hours", 6L);
            int scoreGain = plugin.getConfig().getInt("diplomacy.actions.improve-score", 5);

            long newBalance = source.addChestMoney(-cost);
            setActionCooldown(source.id(), target.id(), "improve", cooldownHours * 3600_000L);
            adjustPairScore(source.id(), target.id(), scoreGain);
            storage.updateClanChestMoney(source.id(), newBalance).exceptionally(t -> {
                plugin.runSync(() -> source.addChestMoney(cost));
                return null;
            });
            notifyBoth(source, target, "diplomacy.improve.sent", "diplomacy.improve.received", Map.of());
            return null;
        });
    }

    public CompletableFuture<Void> insultClanAsync(Player player, Clan source, Clan target) {
        return plugin.supplySync(() -> {
            requireDiplomat(player, source, target);
            requireAllowed(DiplomacyActions.Action.INSULT, source, target);
            if (isActionOnCooldown(source.id(), target.id(), "insult")) {
                throw new IllegalStateException("diplomacy.insult.cooldown");
            }
            long cooldownHours = plugin.getConfig().getLong("diplomacy.actions.insult-cooldown-hours", 6L);
            int penalty = plugin.getConfig().getInt("diplomacy.actions.insult-score", -12);

            setActionCooldown(source.id(), target.id(), "insult", cooldownHours * 3600_000L);
            adjustPairScore(source.id(), target.id(), penalty);
            notifyBoth(source, target, "diplomacy.insult.sent", "diplomacy.insult.received", Map.of());
            return null;
        });
    }

    private void requireDiplomat(Player player, Clan source, Clan target) {
        if (!source.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            throw new IllegalStateException("general.no-permission");
        }
        if (source.id().equals(target.id())) {
            throw new IllegalStateException("general.error");
        }
        if (!source.isRecognized() || !target.isRecognized()) {
            throw new IllegalStateException("deny.diplomacy.need-recognized");
        }
    }

    private void notifyBoth(Clan source, Clan target, String sourceKey, String targetKey, Map<String, String> extra) {
        Map<String, String> toSource = new java.util.HashMap<>(extra);
        toSource.put("tag", target.tag());
        toSource.put("color", target.tagColor());
        Map<String, String> toTarget = new java.util.HashMap<>(extra);
        toTarget.put("tag", source.tag());
        toTarget.put("color", source.tagColor());
        plugin.getClanManager().getOnlineMembersWithPermission(source, ClanPermission.DIPLOMACY)
                .forEach(p -> plugin.getMessages().send(p, sourceKey, toSource));
        plugin.getClanManager().getOnlineMembersWithPermission(target, ClanPermission.DIPLOMACY)
                .forEach(p -> plugin.getMessages().send(p, targetKey, toTarget));
    }

    // --- Пакты: предложение / принятие / отмена / разрыв ---

    public CompletableFuture<Void> proposeOrAcceptPactAsync(Player player, Clan source, Clan target, PactType type) {
        return plugin.supplySync(() -> {
            requireDiplomat(player, source, target);
            if (hasPact(source.id(), target.id(), type)) {
                throw new IllegalStateException("diplomacy.pact.already-active");
            }
            if (plugin.getClanManager().inConflictWith(source.id(), target.id())) {
                throw new IllegalStateException("diplomacy.conflict-in-progress");
            }
            requireAllowed(switch (type) {
                case NAP -> DiplomacyActions.Action.NAP;
                case TRADE -> DiplomacyActions.Action.TRADE_PACT;
                case ALLIANCE -> DiplomacyActions.Action.ALLIANCE;
            }, source, target);

            if (hasPendingPactOffer(target.id(), source.id(), type)) {
                removePendingPactOffer(target.id(), source.id(), type);
                int bonus = plugin.getConfig().getInt("diplomacy.events.pact-signed." + type.name().toLowerCase(java.util.Locale.ROOT),
                        switch (type) {
                            case NAP -> 5;
                            case TRADE -> 8;
                            case ALLIANCE -> 10;
                        });
                CompletableFuture<Void> saved = addPactAsync(ClanPact.createPermanent(source.id(), target.id(), type));
                adjustPairScore(source.id(), target.id(), bonus);
                notifyBoth(source, target, "diplomacy.pact.accepted", "diplomacy.pact.accepted", Map.of("type", type.displayName()));
                return saved;
            }
            if (hasPendingPactOffer(source.id(), target.id(), type)) {
                removePendingPactOffer(source.id(), target.id(), type);
                plugin.getMessages().send(player, "diplomacy.pact.cancelled");
                return CompletableFuture.<Void>completedFuture(null);
            }
            long ttlHours = plugin.getConfig().getLong("diplomacy.pacts.offer-ttl-hours", 24L);
            pendingPactOffers.put(new PactOfferKey(source.id(), target.id(), type), System.currentTimeMillis() + ttlHours * 3600_000L);
            notifyBoth(source, target, "diplomacy.pact.offered", "diplomacy.pact.received", Map.of("type", type.displayName()));
            return CompletableFuture.<Void>completedFuture(null);
        }).thenCompose(f -> f);
    }

    public CompletableFuture<Void> cancelOrBreakPactAsync(Player player, Clan source, Clan target, PactType type) {
        return plugin.supplySync(() -> {
            if (!source.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            Optional<ClanPact> pactOpt = getPact(source.id(), target.id(), type);
            if (pactOpt.isEmpty()) {
                throw new IllegalStateException("diplomacy.pact.not-active");
            }
            CompletableFuture<Void> removed = removePactAsync(pactOpt.get().id());
            int penalty = switch (type) {
                case NAP -> plugin.getConfig().getInt("diplomacy.pacts.nap-break-penalty", -15);
                case TRADE -> plugin.getConfig().getInt("diplomacy.pacts.trade-break-penalty", -10);
                case ALLIANCE -> plugin.getConfig().getInt("diplomacy.pacts.alliance-break-penalty", -40);
            };
            adjustPairScore(source.id(), target.id(), penalty);
            notifyBoth(source, target, "diplomacy.pact.broken", "diplomacy.pact.broken", Map.of("type", type.displayName()));
            return removed;
        }).thenCompose(f -> f);
    }

    // --- Совместимость API / админка: выставить «статус» через счёт ---

    /** Для LoveClansAPI и админ-команды: ALLY/ENEMY/NEUTRAL выражаются счётом и пактом союза. */
    public CompletableFuture<Clan> adminSetRelationAsync(Clan source, Clan target, DiplomacyRelation relation, UUID actorId) {
        return plugin.supplySync(() -> {
            if (actorId != null && !source.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (source.id().equals(target.id())) {
                throw new IllegalStateException("general.error");
            }
            Optional<ClanPact> alliance = getPact(source.id(), target.id(), PactType.ALLIANCE);
            CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
            switch (relation) {
                case NEUTRAL -> {
                    if (alliance.isPresent()) pending = removePactAsync(alliance.get().id());
                    setPairScore(source.id(), target.id(), 0);
                }
                case ENEMY -> {
                    if (alliance.isPresent()) pending = removePactAsync(alliance.get().id());
                    setPairScore(source.id(), target.id(), Math.min(source.relationScore(target.id()), -70));
                }
                case ALLY -> {
                    setPairScore(source.id(), target.id(), Math.max(source.relationScore(target.id()), minScore(PactType.ALLIANCE)));
                    if (alliance.isEmpty()) pending = addPactAsync(ClanPact.createPermanent(source.id(), target.id(), PactType.ALLIANCE));
                }
            }
            return pending.thenApply(v -> source);
        }).thenCompose(f -> f);
    }

    // --- Автоматические сдвиги отношений от событий (§3, §7) ---

    private int eventDelta(String key, int def) {
        return plugin.getConfig().getInt("diplomacy.events." + key, def);
    }

    public void onWarVictory(Clan winner, Clan loser) {
        adjustPairScore(winner.id(), loser.id(), eventDelta("war-victory", -25));
    }

    public void onSiegeVictory(Clan winner, Clan loser) {
        adjustPairScore(winner.id(), loser.id(), eventDelta("siege-victory", -20));
    }

    public void onRaidVictory(Clan attacker, Clan defender) {
        adjustPairScore(attacker.id(), defender.id(), eventDelta("raid-victory", -20));
    }

    public void onRaidDefended(Clan attacker, Clan defender) {
        adjustPairScore(attacker.id(), defender.id(), eventDelta("raid-defended", -10));
    }

    public void onPeaceSettled(Clan clanA, Clan clanB) {
        adjustPairScore(clanA.id(), clanB.id(), eventDelta("war-peace", 10));
    }

    /** Мирное завершение осады или рейда (раньше отношения менялись только при победе). */
    public void onConflictPeace(Clan clanA, Clan clanB) {
        adjustPairScore(clanA.id(), clanB.id(), eventDelta("conflict-peace", 8));
    }

    /** Убийство игрока чужого клана вне войны. На земле жертвы штраф вдвое больше. Суточный лимит на пару. */
    public void onKill(Clan killerClan, Clan victimClan, boolean onVictimTerritory) {
        int base = eventDelta("pvp-kill", -3);
        int delta = onVictimTerritory ? base * 2 : base;
        applyCappedDelta(killerClan.id(), victimClan.id(), "kill", delta, eventDelta("pvp-kill-daily-cap", -15));
    }

    /** Завершённая сделка между кланами: небольшой плюс, не больше суточного лимита. */
    public void onTradeCompleted(Clan clanA, Clan clanB) {
        applyCappedDelta(clanA.id(), clanB.id(), "trade", eventDelta("trade-completed", 3), eventDelta("trade-daily-cap", 9));
    }

    private record DailyDelta(long day, int total) {}

    private final Map<String, DailyDelta> dailyDelta = new ConcurrentHashMap<>();

    private void applyCappedDelta(UUID a, UUID b, String kind, int delta, int dailyCap) {
        if (delta == 0) {
            return;
        }
        String key = (a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a) + ":" + kind;
        long today = System.currentTimeMillis() / 86_400_000L;
        int[] applied = new int[1];
        dailyDelta.compute(key, (k, old) -> {
            int used = old != null && old.day() == today ? old.total() : 0;
            int room = dailyCap - used;
            int step = delta > 0 ? Math.min(delta, Math.max(0, room)) : Math.max(delta, Math.min(0, room));
            applied[0] = step;
            return new DailyDelta(today, used + step);
        });
        if (applied[0] != 0) {
            adjustPairScore(a, b, applied[0]);
        }
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull);
    }
}
