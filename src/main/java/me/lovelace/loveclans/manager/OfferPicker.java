package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ContractType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Picks the few contracts a clan is offered for one period. Nothing is stored: the offer is a pure
 * function of (pool, clan, period start, type), so it is the same every time the menu is opened, after
 * a restart, and for every member - there is no re-roll to farm. The pool is sorted before shuffling,
 * so reordering entries in config.yml does not change the offer inside a running period. Different
 * clans (and weekly vs monthly) get different picks.
 */
public final class OfferPicker {

    private OfferPicker() {}

    public static List<String> pick(Collection<String> poolIds, UUID clanId, long periodStartMillis,
                                    ContractType type, int count) {
        if (poolIds == null || poolIds.isEmpty() || count <= 0) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(new java.util.TreeSet<>(poolIds));
        Collections.shuffle(sorted, new Random(seed(clanId, periodStartMillis, type)));
        return List.copyOf(sorted.subList(0, Math.min(count, sorted.size())));
    }

    static long seed(UUID clanId, long periodStartMillis, ContractType type) {
        long seed = clanId.getMostSignificantBits() * 31L + clanId.getLeastSignificantBits();
        seed = seed * 1_000_003L + periodStartMillis;
        // String.hashCode is specified by the JLS, unlike Enum.hashCode which is identity-based.
        return seed * 31L + type.name().hashCode();
    }
}
