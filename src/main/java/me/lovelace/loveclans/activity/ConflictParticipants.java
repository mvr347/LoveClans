package me.lovelace.loveclans.activity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who actually took part in a running conflict. The managers mark an online, non-AFK clan member whenever he does
 * something that counts (stands in the contested claim or the raid zone, scores a kill, breaks or fortifies a
 * camp). The set is handed over once, with the resolved-event, so rewards go to participants and not to everyone
 * who happens to be in the clan.
 */
public final class ConflictParticipants {
    private final Map<UUID, Map<UUID, UUID>> byConflict = new ConcurrentHashMap<>();

    public void mark(UUID conflictId, UUID playerId, UUID clanId) {
        byConflict.computeIfAbsent(conflictId, id -> new ConcurrentHashMap<>()).put(playerId, clanId);
    }

    /** Removes and returns {@code player -> clan} of the conflict. */
    public Map<UUID, UUID> drain(UUID conflictId) {
        Map<UUID, UUID> taken = byConflict.remove(conflictId);
        return taken == null ? Map.of() : new HashMap<>(taken);
    }

    public void forget(UUID conflictId) {
        byConflict.remove(conflictId);
    }
}
