package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.diplomacy.ClanLetter;
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
 * Эмбарго, блокада и письма между кланами (§5) — слой поверх {@link ClanManager}'s
 * {@code DiplomacyRelation} (нейтрален/союзник/враг), а не замена ему, так же как война/осада
 * /набег живут в своих менеджерах, а не в самом клане.
 */
public final class DiplomacyManager {
    private final LoveClansPlugin plugin;
    private final ClanStorage storage;

    // Эмбарго — взаимное, храним один канонический ключ на пару (меньший UUID первым).
    private final Set<AbstractMap.SimpleImmutableEntry<UUID, UUID>> embargoes = ConcurrentHashMap.newKeySet();
    // Блокада — односторонняя: blocker -> множество клановых ID, которые он блокирует.
    private final Map<UUID, Set<UUID>> blockades = new ConcurrentHashMap<>();

    public DiplomacyManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
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
        });
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> embargoKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new AbstractMap.SimpleImmutableEntry<>(a, b) : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    // --- Эмбарго (§5.2) ---

    public boolean isEmbargoed(UUID clanA, UUID clanB) {
        return embargoes.contains(embargoKey(clanA, clanB));
    }

    public CompletableFuture<Void> declareEmbargoAsync(Clan source, UUID actorId, Clan target) {
        return plugin.supplySync(() -> {
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
            return embargoKey(source.id(), target.id());
        }).thenCompose(key -> {
            embargoes.add(key);
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

    public boolean canBlockade(Clan blocker, Clan blocked) {
        double multiplier = plugin.getConfig().getDouble("influence.blockade-multiplier", 1.5);
        return blocker.influence() >= blocked.influence() * multiplier;
    }

    public CompletableFuture<Void> declareBlockadeAsync(Clan blocker, UUID actorId, Clan blocked) {
        return plugin.supplySync(() -> {
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
            if (!canBlockade(blocker, blocked)) {
                throw new IllegalStateException("diplomacy.blockade.insufficient-influence");
            }
            return null;
        }).thenCompose(ignored -> {
            blockades.computeIfAbsent(blocker.id(), k -> ConcurrentHashMap.newKeySet()).add(blocked.id());
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
        }).thenCompose(ignored -> liftBlockade(blocker.id(), blocked.id())).thenRun(() -> {
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

    // --- Письма (§5.4) ---

    public CompletableFuture<ClanLetter> sendLetterAsync(Clan from, UUID actorId, Clan to, String message) {
        return plugin.supplySync(() -> {
            if (!from.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (from.id().equals(to.id())) {
                throw new IllegalStateException("general.error");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalStateException("diplomacy.letter.empty");
            }
            int maxLength = plugin.getConfig().getInt("clans.diplomacy.letter-max-length", 256);
            if (message.length() > maxLength) {
                throw new IllegalStateException("diplomacy.letter.too-long");
            }
            if (plugin.getClanManager().inConflictWith(from.id(), to.id())) {
                throw new IllegalStateException("diplomacy.letter.conflict-blocked");
            }
            return new ClanLetter(UUID.randomUUID(), from.id(), to.id(), message.trim(), false, System.currentTimeMillis());
        }).thenCompose(letter -> storage.saveLetterAsync(letter).thenApply(v -> letter))
                .thenApply(letter -> {
                    plugin.getClanManager().getOnlineLeader(to).ifPresent(leader ->
                            plugin.getMessages().sendClickableLetter(leader, from.tag(), from.tagColor()));
                    return letter;
                });
    }

    public CompletableFuture<Collection<ClanLetter>> getLettersAsync(UUID clanA, UUID clanB) {
        return storage.loadLettersBetweenAsync(clanA, clanB);
    }

    public CompletableFuture<Void> markLetterReadAsync(UUID letterId) {
        return storage.markLetterReadAsync(letterId);
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull);
    }
}
