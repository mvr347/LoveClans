package me.lovelace.loveclans.gui;

import java.util.List;
import java.util.Objects;

/**
 * "Perfectionists" easter egg: the clan chest is in perfect order when it holds one kind of item only, every occupied
 * slot is a full stack, and enough slots are occupied for it to count. Generic over the stack type (the caller says
 * what "same kind" and "full" mean), so the rule is testable without a running server.
 */
public final class ChestOrder {
    private ChestOrder() {
    }

    /** Maximum stack size the rule asks for. */
    public static final int FULL_STACK = 64;

    /** What the caller knows about one occupied slot. */
    public record Slot<K>(K kind, int amount) {
    }

    /**
     * @param slots     the occupied slots only (empty slots are not listed)
     * @param minSlots  at least this many occupied slots, so a nearly empty chest is not "perfect"
     */
    public static <K> boolean perfect(List<Slot<K>> slots, int minSlots) {
        if (slots == null || slots.isEmpty() || slots.size() < Math.max(1, minSlots)) return false;
        K first = slots.get(0).kind();
        for (Slot<K> slot : slots) {
            if (slot.amount() != FULL_STACK || !Objects.equals(first, slot.kind())) return false;
        }
        return true;
    }
}
