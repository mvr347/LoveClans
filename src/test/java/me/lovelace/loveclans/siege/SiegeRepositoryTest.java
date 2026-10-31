package me.lovelace.loveclans.siege;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.siege.SiegeState;
import org.junit.jupiter.api.*;
import org.sqlite.SQLiteDataSource;

class SiegeRepositoryTest {
  private SQLiteDataSource source;
  private Connection anchor;
  private SiegeRepository repository;

  @BeforeEach
  void setup() throws Exception {
    source = new SQLiteDataSource();
    source.setUrl("jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared");
    anchor = source.getConnection();
    try (Statement st = anchor.createStatement()) {
      SiegeRepository.createTables(st);
      st.executeUpdate("CREATE TABLE accounts (balance INT NOT NULL)");
      st.executeUpdate("INSERT INTO accounts VALUES(100)");
    }
    repository = new SiegeRepository(source);
  }

  @AfterEach
  void cleanup() throws Exception {
    repository.close();
    anchor.close();
  }

  private SiegeSnapshot state(UUID id, long revision, long remaining, String outcome) {
    return new SiegeSnapshot(
        1,
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        new TerritoryKey("world", 1, 2),
        42,
        500,
        null,
        SiegeState.ACTIVE,
        remaining,
        240000,
        12000,
        List.of(
            new SiegeSnapshot.Camp(
                0,
                "world",
                1,
                70,
                2,
                CampLayout.Direction.EAST,
                4,
                3,
                true,
                100000,
                List.of(new SiegeSnapshot.Cell("world", 1, 70, 2, "minecraft:air")))),
        Map.of(UUID.randomUUID(), UUID.randomUUID()),
        outcome,
        revision);
  }

  @Test
  void snapshotRoundTripRetainsTheCompleteCampAndRemainingDurations() throws Exception {
    var snapshot = state(UUID.randomUUID(), 1, 600000, null);
    repository.save(snapshot).get(3, TimeUnit.SECONDS);
    assertEquals(snapshot, repository.load().get(3, TimeUnit.SECONDS).getFirst());
    assertEquals(snapshot, SiegeRepository.decode(SiegeRepository.encode(snapshot)));
  }

  @Test
  void restartUsesRemainingDurationsRatherThanTheTimeSpentOffline() {
    var snapshot = state(UUID.randomUUID(), 1, 600000, null);
    long afterLongShutdown = 9_000_000_000L;
    var resumed = snapshot.resume(afterLongShutdown);
    assertEquals(600000, resumed.endsAt() - afterLongShutdown);
    assertEquals(100000, resumed.camps().getFirst().respawnAt() - afterLongShutdown);
    assertEquals(snapshot.id(), resumed.id());
    assertEquals(snapshot.territory(), resumed.contestedTerritory());
    assertEquals(240000, snapshot.hold());
    assertEquals(12000, snapshot.wipeElapsed());
  }

  @Test
  void anOlderResponseCannotReplaceANewerState() throws Exception {
    UUID id = UUID.randomUUID();
    var newer = state(id, 5, 123000, null);
    repository.save(newer).join();
    repository.save(state(id, 3, 999000, null)).join();
    assertEquals(newer, repository.load().join().getFirst());
  }

  @Test
  void aTerminalReceiptStopsLateCheckpointsResurrectingTheSiege() throws Exception {
    UUID id = UUID.randomUUID();
    repository.save(state(id, 1, 1, null)).join();
    repository.finish(id).join();
    repository.save(state(id, 99, 999, null)).join();
    assertTrue(repository.load().join().isEmpty());
  }

  @Test
  void repeatingASettlementNeverTransfersMoneyTwice() throws Exception {
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 3; i++)
      assertEquals(
          i == 0,
          repository
              .once(
                  id,
                  "economic",
                  c -> {
                    try (Statement st = c.createStatement()) {
                      st.executeUpdate("UPDATE accounts SET balance=balance-25");
                    }
                    return null;
                  })
              .join());
    try (Statement st = anchor.createStatement();
        ResultSet rs = st.executeQuery("SELECT balance FROM accounts")) {
      assertTrue(rs.next());
      assertEquals(75, rs.getInt(1));
    }
  }

  @Test
  void failedSettlementRollsBackMoneyAndDoesNotLeaveAReceipt() throws Exception {
    UUID id = UUID.randomUUID();
    assertThrows(
        CompletionException.class,
        () ->
            repository
                .once(
                    id,
                    "economic",
                    c -> {
                      try (Statement st = c.createStatement()) {
                        st.executeUpdate("UPDATE accounts SET balance=0");
                      }
                      throw new SQLException("injected write failure");
                    })
                .join());
    assertTrue(repository.once(id, "economic", c -> null).join());
    try (Statement st = anchor.createStatement();
        ResultSet rs = st.executeQuery("SELECT balance FROM accounts")) {
      assertTrue(rs.next());
      assertEquals(100, rs.getInt(1));
    }
  }

  @Test
  void shutdownDrainsAcceptedTasksAndRejectsNewOnesWithCompletedFutures() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    var first =
        repository.submit(
            c -> {
              entered.countDown();
              assertTrue(Thread.currentThread().isVirtual());
              release.await();
              return 1;
            });
    assertTrue(entered.await(2, TimeUnit.SECONDS));
    var second = repository.submit(c -> 2);
    release.countDown();
    repository.close();
    assertEquals(1, first.join());
    assertEquals(2, second.join());
    var rejected = repository.load();
    assertTrue(rejected.isDone());
    assertTrue(rejected.isCompletedExceptionally());
  }

  @Test
  void oneFailureDoesNotPoisonFollowingOperations() {
    assertThrows(
        CompletionException.class,
        () ->
            repository
                .submit(
                    c -> {
                      throw new SQLException("fail");
                    })
                .join());
    assertEquals(9, repository.submit(c -> 9).join());
  }
}
