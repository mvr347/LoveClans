package me.lovelace.loveclans.manager.stateorder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.random.RandomGenerator;

/** Random choices of a new state order: the category (never the previous one again) and its materials. */
public final class StateOrderPicker {
    private StateOrderPicker() {}

    /** A random category different from {@code previous} (any category when {@code previous} is null). */
    public static StateOrderCategory pickCategory(StateOrderCategory previous, RandomGenerator random) {
        List<StateOrderCategory> options = new ArrayList<>(List.of(StateOrderCategory.values()));
        if (previous != null && options.size() > 1) options.remove(previous);
        return options.get(random.nextInt(options.size()));
    }

    /** Up to {@code count} distinct random entries of {@code pool} (duplicates in the pool count once). */
    public static <T> List<T> pickMaterials(List<T> pool, int count, RandomGenerator random) {
        List<T> distinct = new ArrayList<>(new LinkedHashSet<>(pool));
        // Partial Fisher-Yates: the first n positions end up a uniform random sample.
        int n = Math.max(0, Math.min(count, distinct.size()));
        for (int i = 0; i < n; i++) {
            int j = i + random.nextInt(distinct.size() - i);
            T tmp = distinct.get(i);
            distinct.set(i, distinct.get(j));
            distinct.set(j, tmp);
        }
        return List.copyOf(distinct.subList(0, n));
    }
}
