package me.lovelace.loveclans.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class MainThreadGateTest {
  @Test
  void pendingActionIsCancelledAndCannotExecuteAfterDisable() {
    List<Runnable> queued = new ArrayList<>();
    AtomicInteger effects = new AtomicInteger();
    var gate = new MainThreadGate(() -> false, () -> true, queued::add);
    var result = gate.call(effects::incrementAndGet);
    assertFalse(result.isDone());
    gate.close();
    assertTrue(result.isCancelled());
    queued.getFirst().run();
    assertEquals(0, effects.get());
  }

  @Test
  void allQueuedCallsFinishOnShutdown() {
    List<Runnable> queued = new ArrayList<>();
    var gate = new MainThreadGate(() -> false, () -> true, queued::add);
    List<CompletableFuture<Integer>> calls = new ArrayList<>();
    for (int i = 0; i < 100; i++) calls.add(gate.call(() -> 1));
    gate.close();
    assertTrue(calls.stream().allMatch(CompletableFuture::isDone));
    assertTrue(gate.call(() -> 2).isCompletedExceptionally());
  }

  @Test
  void queuedActionRechecksWhetherThePluginIsEnabled() {
    List<Runnable> queued = new ArrayList<>();
    AtomicBoolean enabled = new AtomicBoolean(true);
    var gate = new MainThreadGate(() -> false, enabled::get, queued::add);
    var result = gate.call(() -> 1);
    enabled.set(false);
    queued.getFirst().run();
    assertTrue(result.isCancelled());
  }

  @Test
  void schedulingRejectionCompletesTheRequest() {
    var gate =
        new MainThreadGate(
            () -> false,
            () -> true,
            r -> {
              throw new RejectedExecutionException("closed");
            });
    var result = gate.call(() -> 1);
    assertTrue(result.isDone());
    assertThrows(CompletionException.class, result::join);
  }

  @Test
  void mainThreadExecutesDirectlyAndPropagatesExceptions() {
    var gate =
        new MainThreadGate(
            () -> true,
            () -> true,
            r -> {
              fail("must not schedule");
            });
    assertEquals(7, gate.call(() -> 7).join());
    assertThrows(
        CompletionException.class,
        () ->
            gate.call(
                    () -> {
                      throw new IllegalArgumentException("fail");
                    })
                .join());
    gate.close();
  }

  @Test
  void aClosedGateCannotBeReenteredFromCancellationCallbacks() {
    List<Runnable> queued = new ArrayList<>();
    var gate = new MainThreadGate(() -> false, () -> true, queued::add);
    var first = gate.call(() -> 1);
    AtomicReference<CompletableFuture<Integer>> second = new AtomicReference<>();
    first.whenComplete((v, e) -> second.set(gate.call(() -> 2)));
    gate.close();
    assertTrue(second.get().isCompletedExceptionally());
    assertEquals(1, queued.size());
  }
}
