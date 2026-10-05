package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractSlotsTest {

    private static final UUID CLAN = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private static ClanQuestProgress progress(String id, long startedAt, int done, boolean completed, boolean claimed) {
        return new ClanQuestProgress(CLAN, ContractType.WEEKLY, id, 10, 500L, done, completed, claimed, startedAt, startedAt + 1_000);
    }

    /** Runs {@code task} on {@code threads} threads released at the same instant and returns every result. */
    private static <T> List<T> race(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyOneOfManyConcurrentReservationsWins() throws Exception {
        for (int round = 0; round < 50; round++) {
            ContractSlots slots = new ContractSlots();
            AtomicInteger counter = new AtomicInteger();
            List<Boolean> results = race(16, () -> slots.reserve(progress("vow_" + counter.incrementAndGet(), 1L, 0, false, false)));
            assertEquals(1, results.stream().filter(b -> b).count(), "round " + round);
        }
    }

    @Test
    void onlyOneOfManyConcurrentClaimsWins() throws Exception {
        for (int round = 0; round < 50; round++) {
            ContractSlots slots = new ContractSlots();
            slots.put(progress("vow_1", 1L, 10, true, false));
            List<Boolean> results = race(16, () -> slots.tryClaim(CLAN).isPresent());
            assertEquals(1, results.stream().filter(b -> b).count(), "round " + round);
            assertTrue(slots.get(CLAN).orElseThrow().claimed());
        }
    }

    @Test
    void claimRequiresFinishedAndUnclaimedContract() {
        ContractSlots slots = new ContractSlots();
        assertTrue(slots.tryClaim(CLAN).isEmpty(), "nothing active");
        slots.put(progress("vow_1", 1L, 3, false, false));
        assertTrue(slots.tryClaim(CLAN).isEmpty(), "not finished");
        slots.put(progress("vow_1", 1L, 10, true, true));
        assertTrue(slots.tryClaim(CLAN).isEmpty(), "already claimed");
    }

    @Test
    void unclaimLetsTheClanRetryAfterAFailedPayout() {
        ContractSlots slots = new ContractSlots();
        slots.put(progress("vow_1", 1L, 10, true, false));
        ClanQuestProgress claimed = slots.tryClaim(CLAN).orElseThrow();
        assertTrue(slots.tryClaim(CLAN).isEmpty());
        slots.unclaim(claimed);
        assertTrue(slots.tryClaim(CLAN).isPresent());
    }

    @Test
    void releaseOnlyFreesTheContractThatWasReservedEvenIfProgressAdvanced() {
        ContractSlots slots = new ContractSlots();
        ClanQuestProgress reserved = progress("vow_1", 1L, 0, false, false);
        assertTrue(slots.reserve(reserved));
        assertFalse(slots.reserve(progress("vow_2", 2L, 0, false, false)));
        // progress moved on while the save was still in flight
        assertTrue(slots.replace(reserved, reserved.withProgress(4)));
        slots.release(reserved);
        assertTrue(slots.get(CLAN).isEmpty());
        // a different contract that took the slot afterwards must NOT be freed by a stale release
        assertTrue(slots.reserve(progress("vow_2", 2L, 0, false, false)));
        slots.release(reserved);
        assertEquals("vow_2", slots.get(CLAN).orElseThrow().questId());
    }

    @Test
    void staleProgressUpdateCannotOverwriteAClaim() {
        ContractSlots slots = new ContractSlots();
        ClanQuestProgress finished = progress("vow_1", 1L, 10, true, false);
        slots.put(finished);
        slots.tryClaim(CLAN).orElseThrow();
        // an update computed from the pre-claim value must lose the CAS
        assertFalse(slots.replace(finished, finished.withProgress(11)));
        assertTrue(slots.get(CLAN).orElseThrow().claimed());
    }

    @Test
    void removeOnlyRemovesTheObservedContract() {
        ContractSlots slots = new ContractSlots();
        ClanQuestProgress old = progress("vow_1", 1L, 0, false, false);
        slots.put(progress("vow_2", 2L, 0, false, false));
        assertFalse(slots.remove(old));
        assertEquals("vow_2", slots.get(CLAN).orElseThrow().questId());
        assertTrue(slots.remove(slots.get(CLAN).orElseThrow()));
        assertTrue(slots.isEmpty());
    }
}
