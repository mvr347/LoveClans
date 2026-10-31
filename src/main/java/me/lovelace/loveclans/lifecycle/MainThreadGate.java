package me.lovelace.loveclans.lifecycle;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Lifecycle-aware main-thread calls: no accepted request remains hanging after shutdown. */
public final class MainThreadGate implements AutoCloseable {
  private final BooleanSupplier main, enabled;
  private final Consumer<Runnable> scheduler;
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final Set<CompletableFuture<?>> waiting = ConcurrentHashMap.newKeySet();

  public MainThreadGate(
      BooleanSupplier main, BooleanSupplier enabled, Consumer<Runnable> scheduler) {
    this.main = main;
    this.enabled = enabled;
    this.scheduler = scheduler;
  }

  public <T> CompletableFuture<T> call(Supplier<T> supplier) {
    if (!accepting.get() || !enabled.getAsBoolean())
      return CompletableFuture.failedFuture(new CancellationException("Плагин останавливается"));
    CompletableFuture<T> answer = new CompletableFuture<>();
    waiting.add(answer);
    answer.whenComplete((v, e) -> waiting.remove(answer));
    Runnable action =
        () -> {
          if (answer.isDone()) return;
          if (!accepting.get() || !enabled.getAsBoolean()) {
            answer.cancel(false);
            return;
          }
          try {
            answer.complete(supplier.get());
          } catch (Throwable failure) {
            answer.completeExceptionally(failure);
          }
        };
    try {
      if (main.getAsBoolean()) action.run();
      else scheduler.accept(action);
    } catch (Throwable failure) {
      answer.completeExceptionally(failure);
    }
    if (!accepting.get()) answer.cancel(false);
    return answer;
  }

  @Override
  public void close() {
    accepting.set(false);
    waiting.forEach(f -> f.cancel(false));
  }
}
