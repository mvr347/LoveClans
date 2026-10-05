package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ClanQuestProgress;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active-contract slot of every clan for ONE contract type (one clan holds at most one contract per
 * type). All state transitions that must not happen twice are compare-and-set operations on the map, so
 * two members clicking at the same moment, or a manual claim racing the expiry tick, can never both win:
 * <ul>
 *   <li>{@link #reserve} - taking a contract. Exactly one caller gets {@code true};</li>
 *   <li>{@link #tryClaim} - paying out a reward. Exactly one caller gets the progress back, so the XP is
 *       granted once;</li>
 *   <li>{@link #replace} - progress updates, so a concurrent claim is never overwritten by a stale value.</li>
 * </ul>
 * Progress records are immutable values, which is what makes the value-based CAS correct.
 */
final class ContractSlots {

    private final ConcurrentHashMap<UUID, ClanQuestProgress> slots = new ConcurrentHashMap<>();

    Optional<ClanQuestProgress> get(UUID clanId) {
        return Optional.ofNullable(slots.get(clanId));
    }

    boolean isEmpty() {
        return slots.isEmpty();
    }

    /** A point-in-time copy, safe to iterate while the slots are being changed. */
    Collection<ClanQuestProgress> snapshot() {
        return List.copyOf(slots.values());
    }

    /** Restores a row loaded from storage. */
    void put(ClanQuestProgress progress) {
        slots.put(progress.clanId(), progress);
    }

    /** Atomically takes the clan's free slot. {@code false} means the clan already holds a contract. */
    boolean reserve(ClanQuestProgress progress) {
        return slots.putIfAbsent(progress.clanId(), progress) == null;
    }

    /**
     * Undoes {@link #reserve} when saving failed. Matches the contract by identity (id + start time) rather
     * than by full value, because progress may already have advanced since the reservation.
     */
    void release(ClanQuestProgress reserved) {
        slots.computeIfPresent(reserved.clanId(), (id, current) -> isSame(current, reserved) ? null : current);
    }

    /** Removes the slot only if it still holds the contract that was observed (id + start time). */
    boolean remove(ClanQuestProgress observed) {
        boolean[] removed = {false};
        slots.computeIfPresent(observed.clanId(), (id, current) -> {
            if (isSame(current, observed)) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        return removed[0];
    }

    /** CAS on the whole value: replaces {@code expected} with {@code updated} only if nothing changed meanwhile. */
    boolean replace(ClanQuestProgress expected, ClanQuestProgress updated) {
        return slots.replace(expected.clanId(), expected, updated);
    }

    /**
     * Marks a finished, not yet claimed contract as claimed. Returns the claimed state for the single winner,
     * and empty for everyone else (nothing active, not finished, or already claimed by someone else).
     */
    Optional<ClanQuestProgress> tryClaim(UUID clanId) {
        while (true) {
            ClanQuestProgress current = slots.get(clanId);
            if (current == null || !current.completed() || current.claimed()) {
                return Optional.empty();
            }
            ClanQuestProgress claimed = current.withClaimed(true);
            if (slots.replace(clanId, current, claimed)) {
                return Optional.of(claimed);
            }
        }
    }

    /** Rolls back {@link #tryClaim} when paying the reward failed, so the clan can try again. */
    void unclaim(ClanQuestProgress claimed) {
        slots.computeIfPresent(claimed.clanId(),
                (id, current) -> isSame(current, claimed) ? current.withClaimed(false) : current);
    }

    private static boolean isSame(ClanQuestProgress a, ClanQuestProgress b) {
        return a.startedAt() == b.startedAt() && a.questId().equals(b.questId());
    }
}
