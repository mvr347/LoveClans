package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ContractType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferPickerTest {

    private static final UUID CLAN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static List<String> pool(int size) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) ids.add("vow_" + i);
        return ids;
    }

    @Test
    void picksThreeDistinctEntriesFromThePool() {
        List<String> pool = pool(20);
        List<String> offer = OfferPicker.pick(pool, CLAN, 1_000L, ContractType.WEEKLY, 3);
        assertEquals(3, offer.size());
        assertEquals(3, new HashSet<>(offer).size());
        assertTrue(pool.containsAll(offer));
    }

    @Test
    void sameInputGivesSameOffer() {
        assertEquals(OfferPicker.pick(pool(20), CLAN, 1_000L, ContractType.WEEKLY, 3),
                OfferPicker.pick(pool(20), CLAN, 1_000L, ContractType.WEEKLY, 3));
    }

    @Test
    void offerDoesNotDependOnConfigOrder() {
        List<String> shuffled = pool(20);
        Collections.reverse(shuffled);
        assertEquals(OfferPicker.pick(pool(20), CLAN, 1_000L, ContractType.WEEKLY, 3),
                OfferPicker.pick(shuffled, CLAN, 1_000L, ContractType.WEEKLY, 3));
    }

    @Test
    void offerChangesBetweenPeriodsClansAndTypes() {
        // Collisions are possible for a single sample, so look at many periods/clans at once.
        Set<List<String>> byPeriod = new HashSet<>();
        Set<List<String>> byClan = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            byPeriod.add(OfferPicker.pick(pool(20), CLAN, 1_000L + i * 604_800_000L, ContractType.WEEKLY, 3));
            byClan.add(OfferPicker.pick(pool(20), new UUID(i, i * 7L), 1_000L, ContractType.WEEKLY, 3));
        }
        assertTrue(byPeriod.size() > 20, "offer should rotate between periods, got " + byPeriod.size());
        assertTrue(byClan.size() > 20, "clans should see different offers, got " + byClan.size());
        assertNotEquals(OfferPicker.seed(CLAN, 1_000L, ContractType.WEEKLY), OfferPicker.seed(CLAN, 1_000L, ContractType.MONTHLY));
    }

    @Test
    void shortPoolReturnsWhatItHasAndEmptyPoolReturnsNothing() {
        assertEquals(2, OfferPicker.pick(pool(2), CLAN, 1_000L, ContractType.MONTHLY, 3).size());
        assertEquals(List.of(), OfferPicker.pick(List.of(), CLAN, 1_000L, ContractType.MONTHLY, 3));
        assertEquals(List.of(), OfferPicker.pick(pool(5), CLAN, 1_000L, ContractType.MONTHLY, 0));
    }

    @Test
    void everyPoolEntryEventuallyAppears() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.addAll(OfferPicker.pick(pool(20), new UUID(i, 1), 1_000L, ContractType.WEEKLY, 3));
        }
        assertEquals(20, seen.size());
    }
}
