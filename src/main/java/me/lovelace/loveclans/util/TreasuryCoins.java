package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.economy.Denomination;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy split of a clan treasury balance into coin stacks for the Казна screen: highest
 * denomination first, each stack at most {@code maxStack} coins, at most {@code maxPiles} stacks.
 * Pure arithmetic so it can be unit-tested without a server or LoveCore.
 */
public final class TreasuryCoins {

    /** One displayed stack: {@code count} coins of {@code denomination}. */
    public record Pile(Denomination denomination, int count) {
        public long value() {
            return denomination.value() * count;
        }
    }

    private TreasuryCoins() {}

    /**
     * Whatever does not fit into {@code maxPiles} stacks (or is smaller than the smallest denomination)
     * is simply not shown as coins; {@link #shownValue} tells how much is.
     */
    public static List<Pile> breakdown(long balance, List<Denomination> denominations, int maxStack, int maxPiles) {
        List<Pile> piles = new ArrayList<>();
        if (balance <= 0 || denominations == null || maxStack <= 0 || maxPiles <= 0) {
            return piles;
        }
        List<Denomination> sorted = new ArrayList<>(denominations);
        sorted.sort(Comparator.comparingLong(Denomination::value).reversed());
        long remaining = balance;
        for (Denomination denomination : sorted) {
            long count = remaining / denomination.value();
            while (count > 0) {
                if (piles.size() >= maxPiles) {
                    return piles;
                }
                int size = (int) Math.min(count, maxStack);
                piles.add(new Pile(denomination, size));
                count -= size;
                remaining -= denomination.value() * size;
            }
        }
        return piles;
    }

    public static long shownValue(List<Pile> piles) {
        long sum = 0;
        for (Pile pile : piles) {
            sum += pile.value();
        }
        return sum;
    }
}
