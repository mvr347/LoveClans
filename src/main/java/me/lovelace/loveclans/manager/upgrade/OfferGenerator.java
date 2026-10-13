package me.lovelace.loveclans.manager.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Builds the pair of options for one offer. The pair is stored by the caller and never re-rolled;
 * this class only decides which options may appear at all: never a maxed-out or pointless one, and
 * never the same option twice.
 */
public final class OfferGenerator {

    private OfferGenerator() {}

    /**
     * @param available whether an option can still bring something to this clan right now
     * @return two different options, one if only one is available, or an empty list
     */
    public static List<UpgradeOption> pair(Predicate<UpgradeOption> available, Random random) {
        List<UpgradeOption> pool = new ArrayList<>();
        for (UpgradeOption option : UpgradeOption.values()) {
            if (available.test(option)) {
                pool.add(option);
            }
        }
        java.util.Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.min(2, pool.size())));
    }
}
