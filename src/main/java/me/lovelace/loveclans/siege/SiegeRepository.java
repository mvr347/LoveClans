package me.lovelace.loveclans.siege;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

/**
 * Ordered I/O on a virtual thread. A terminal receipt prevents stale checkpoints resurrecting a
 * siege.
 */
public final class SiegeRepository implements AutoCloseable {
  private static final Gson JSON = new Gson();
  private final DataSource source;
  private final ExecutorService io =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("loveclans-siege-", 0).factory());
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Set<CompletableFuture<?>> requests = ConcurrentHashMap.newKeySet();

  public SiegeRepository(DataSource source) {
    this.source = source;
  }

  public static void createTables(Statement st) throws SQLException {
    st.executeUpdate(
        "CREATE TABLE IF NOT EXISTS clan_siege_state (siege_id VARCHAR(36) PRIMARY KEY, revision"
            + " BIGINT NOT NULL, payload LONGBLOB NOT NULL, terminal INT NOT NULL DEFAULT 0)");
    st.executeUpdate(
        "CREATE TABLE IF NOT EXISTS clan_siege_receipts (siege_id VARCHAR(36) NOT NULL, step"
            + " VARCHAR(64) NOT NULL, payload LONGBLOB, PRIMARY KEY(siege_id, step))");
    st.executeUpdate(
        "CREATE TABLE IF NOT EXISTS clan_siege_artifacts (siege_id VARCHAR(36) PRIMARY KEY, clan_id"
            + " VARCHAR(36) NOT NULL, payload LONGBLOB NOT NULL)");
  }

  public <T> CompletableFuture<T> submit(Work<T> work) {
    CompletableFuture<T> answer = new CompletableFuture<>();
    if (closed.get())
      return CompletableFuture.failedFuture(new CancellationException("Siege storage is closing"));
    requests.add(answer);
    answer.whenComplete((v, e) -> requests.remove(answer));
    try {
      io.execute(
          () -> {
            try (Connection c = source.getConnection()) {
              answer.complete(work.run(c));
            } catch (Throwable error) {
              answer.completeExceptionally(error);
            }
          });
    } catch (RejectedExecutionException error) {
      answer.completeExceptionally(error);
    }
    return answer;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection connection) throws Exception;
  }

  public CompletableFuture<List<SiegeSnapshot>> load() {
    return submit(
        c -> {
          List<SiegeSnapshot> result = new ArrayList<>();
          try (Statement st = c.createStatement();
              ResultSet rs =
                  st.executeQuery("SELECT payload FROM clan_siege_state WHERE terminal = 0")) {
            while (rs.next()) {
              SiegeSnapshot state = decode(rs.getBytes(1));
              if (state.format() != 1) throw new SQLException("Unsupported siege snapshot version");
              result.add(state);
            }
          }
          return List.copyOf(result);
        });
  }

  public CompletableFuture<Void> save(SiegeSnapshot state) {
    byte[] payload = encode(state);
    return submit(
        c -> {
          save(c, state.id(), state.revision(), payload);
          return null;
        });
  }

  public static void save(Connection c, UUID id, long revision, byte[] payload)
      throws SQLException {
    c.setAutoCommit(false);
    try {
      boolean exists;
      try (PreparedStatement ps =
          c.prepareStatement("SELECT revision FROM clan_siege_state WHERE siege_id = ?")) {
        ps.setString(1, id.toString());
        try (ResultSet rs = ps.executeQuery()) {
          exists = rs.next();
        }
      }
      if (exists) {
        try (PreparedStatement ps =
            c.prepareStatement(
                "UPDATE clan_siege_state SET revision = ?, payload = ? WHERE siege_id = ? AND"
                    + " revision < ? AND terminal = 0")) {
          ps.setLong(1, revision);
          ps.setBytes(2, payload);
          ps.setString(3, id.toString());
          ps.setLong(4, revision);
          ps.executeUpdate();
        }
      } else
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO clan_siege_state(siege_id,revision,payload,terminal)"
                    + " VALUES(?,?,?,0)")) {
          ps.setString(1, id.toString());
          ps.setLong(2, revision);
          ps.setBytes(3, payload);
          ps.executeUpdate();
        }
      c.commit();
    } catch (Throwable error) {
      c.rollback();
      throw error;
    } finally {
      c.setAutoCommit(true);
    }
  }

  public CompletableFuture<Void> finish(UUID id) {
    return submit(
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement(
                  "UPDATE clan_siege_state SET terminal=1,payload=? WHERE siege_id=?")) {
            ps.setBytes(1, new byte[0]);
            ps.setString(2, id.toString());
            ps.executeUpdate();
          }
          return null;
        });
  }

  /** Executes an SQL-only settlement step and its receipt in the SAME transaction. */
  public <T> CompletableFuture<Boolean> once(UUID id, String step, Work<T> action) {
    return submit(c -> once(c, id, step, action));
  }

  public static boolean once(Connection c, UUID id, String step, Work<?> action) throws Exception {
    c.setAutoCommit(false);
    try {
      try (PreparedStatement ps =
          c.prepareStatement("SELECT step FROM clan_siege_receipts WHERE siege_id=? AND step=?")) {
        ps.setString(1, id.toString());
        ps.setString(2, step);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            c.rollback();
            return false;
          }
        }
      }
      action.run(c);
      try (PreparedStatement ps =
          c.prepareStatement("INSERT INTO clan_siege_receipts(siege_id,step) VALUES(?,?)")) {
        ps.setString(1, id.toString());
        ps.setString(2, step);
        ps.executeUpdate();
      }
      c.commit();
      return true;
    } catch (Throwable error) {
      c.rollback();
      throw error;
    } finally {
      c.setAutoCommit(true);
    }
  }

  public static byte[] encode(SiegeSnapshot state) {
    return JSON.toJson(state).getBytes(StandardCharsets.UTF_8);
  }

  public static SiegeSnapshot decode(byte[] bytes) {
    return JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), SiegeSnapshot.class);
  }

  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    io.shutdown();
    try {
      if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
        io.shutdownNow();
        requests.forEach(
            f -> f.completeExceptionally(new CancellationException("Остановка хранилища осад")));
      }
    } catch (InterruptedException e) {
      io.shutdownNow();
      requests.forEach(f -> f.cancel(false));
      Thread.currentThread().interrupt();
    }
  }
}
