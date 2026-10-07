package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryCoinsTest {
    private static final Denomination COPPER = new Denomination("copper_coin", 1);
    private static final Denomination IRON = new Denomination("iron_coin", 100);
    private static final Denomination GOLD = new Denomination("gold_coin", 2000);
    private static final List<Denomination> DENS = List.of(COPPER, GOLD, IRON);

    @Test
    void greedyHighestFirst() {
        List<TreasuryCoins.Pile> piles = TreasuryCoins.breakdown(4_250, DENS, 64, 21);
        assertEquals(List.of(new TreasuryCoins.Pile(GOLD, 2), new TreasuryCoins.Pile(IRON, 2), new TreasuryCoins.Pile(COPPER, 50)), piles);
        assertEquals(4_250, TreasuryCoins.shownValue(piles));
    }

    @Test
    void splitsIntoMaxStacks() {
        List<TreasuryCoins.Pile> piles = TreasuryCoins.breakdown(150, List.of(COPPER), 64, 21);
        assertEquals(List.of(new TreasuryCoins.Pile(COPPER, 64), new TreasuryCoins.Pile(COPPER, 64), new TreasuryCoins.Pile(COPPER, 22)), piles);
    }

    @Test
    void capsPileCount() {
        List<TreasuryCoins.Pile> piles = TreasuryCoins.breakdown(64L * 2000 * 30, DENS, 64, 21);
        assertEquals(21, piles.size());
        assertTrue(TreasuryCoins.shownValue(piles) < 64L * 2000 * 30);
    }

    @Test
    void emptyForZeroOrNoDenominations() {
        assertTrue(TreasuryCoins.breakdown(0, DENS, 64, 21).isEmpty());
        assertTrue(TreasuryCoins.breakdown(100, List.of(), 64, 21).isEmpty());
    }
}
