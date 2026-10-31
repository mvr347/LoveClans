package me.lovelace.loveclans.siege;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.siege.*;
import org.junit.jupiter.api.Test;

class SiegeRewardsTest {
  private Connection database() throws Exception {
    Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
    try (Statement st = c.createStatement()) {
      SiegeRepository.createTables(st);
      st.executeUpdate(
          "CREATE TABLE clans(id TEXT PRIMARY KEY,chest_money BIGINT,level INT,experience"
              + " BIGINT,upgrade_points INT,sieges_won INT,sieges_lost INT)");
      st.executeUpdate("CREATE TABLE clan_chest(clan_id TEXT PRIMARY KEY,contents BLOB)");
      st.executeUpdate(
          "CREATE TABLE clan_conflicts(id TEXT PRIMARY KEY,kind TEXT,attacker_clan_id"
              + " TEXT,defender_clan_id TEXT,winner_clan_id TEXT,attacker_score INT,defender_score"
              + " INT,started_at BIGINT,ended_at BIGINT)");
      st.executeUpdate(
          "CREATE TABLE clan_modifiers(id TEXT PRIMARY KEY,clan_id TEXT,type TEXT,payload"
              + " TEXT,started_at BIGINT,ends_at BIGINT,stacks INT)");
    }
    return c;
  }

  private SiegeRewards.Prepared prepared(Connection c) throws Exception {
    UUID attacker = UUID.randomUUID(), defender = UUID.randomUUID();
    try (PreparedStatement ps = c.prepareStatement("INSERT INTO clans VALUES(?,?,5,0,0,0,0)")) {
      for (UUID id : List.of(attacker, defender)) {
        ps.setString(1, id.toString());
        ps.setLong(2, 1000);
        ps.addBatch();
      }
      ps.executeBatch();
    }
    ClanSiege siege =
        new ClanSiege(
            UUID.randomUUID(),
            attacker,
            defender,
            new TerritoryKey("world", 0, 0),
            10,
            20,
            SiegeState.ACTIVE,
            List.of());
    long[] thresholds = new long[22];
    Arrays.fill(thresholds, Long.MAX_VALUE);
    return new SiegeRewards.Prepared(
        siege,
        attacker,
        defender,
        true,
        new byte[] {1},
        new byte[] {2},
        new byte[] {3},
        List.of(),
        List.of(),
        25,
        900,
        20,
        thresholds,
        30);
  }

  @Test
  void realSettlementCodeCommitsTreasuryChestsExperienceArchiveAndPendingArtifactOnce()
      throws Exception {
    try (Connection c = database()) {
      var reward = prepared(c);
      assertTrue(
          SiegeRepository.once(
              c,
              reward.siege().id(),
              "economic",
              tx -> {
                reward.commit(tx);
                return null;
              }));
      assertFalse(
          SiegeRepository.once(
              c,
              reward.siege().id(),
              "economic",
              tx -> {
                reward.commit(tx);
                return null;
              }));
      try (Statement st = c.createStatement();
          ResultSet rs =
              st.executeQuery(
                  "SELECT SUM(chest_money),SUM(experience),SUM(sieges_won),SUM(sieges_lost) FROM"
                      + " clans")) {
        assertTrue(rs.next());
        assertEquals(2000, rs.getLong(1));
        assertEquals(900, rs.getLong(2));
        assertEquals(1, rs.getInt(3));
        assertEquals(1, rs.getInt(4));
      }
      for (String table : List.of("clan_conflicts", "clan_siege_artifacts"))
        try (Statement st = c.createStatement();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
          assertTrue(rs.next());
          assertEquals(1, rs.getInt(1));
        }
      try (PreparedStatement ps = c.prepareStatement("SELECT chest_money FROM clans WHERE id=?")) {
        ps.setString(1, reward.winner().toString());
        try (ResultSet rs = ps.executeQuery()) {
          assertTrue(rs.next());
          assertEquals(1250, rs.getLong(1));
        }
      }
    }
  }

  @Test
  void failureAtTheArchiveWriteRollsBackEarlierEconomicChanges() throws Exception {
    try (Connection c = database()) {
      var reward = prepared(c);
      try (Statement st = c.createStatement()) {
        st.executeUpdate("DROP TABLE clan_conflicts");
      }
      assertThrows(
          SQLException.class,
          () ->
              SiegeRepository.once(
                  c,
                  reward.siege().id(),
                  "economic",
                  tx -> {
                    reward.commit(tx);
                    return null;
                  }));
      try (Statement st = c.createStatement();
          ResultSet rs =
              st.executeQuery(
                  "SELECT MIN(chest_money),MAX(chest_money),SUM(sieges_won),SUM(experience) FROM"
                      + " clans")) {
        assertTrue(rs.next());
        assertEquals(1000, rs.getLong(1));
        assertEquals(1000, rs.getLong(2));
        assertEquals(0, rs.getInt(3));
        assertEquals(0, rs.getLong(4));
      }
      try (Statement st = c.createStatement();
          ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clan_siege_receipts")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
      }
    }
  }
}
