package me.lovelace.loveclans.activity;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActivityLedgerTest {
    private static final UUID CLAN = UUID.randomUUID();
    private static final UUID OTHER_CLAN = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID MAX = UUID.randomUUID();
    private static final long WED = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli();
    private static final long NEXT_WEEK = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli();
    private static final long NEXT_MONTH = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli();

    @Test
    void addsToAllThreePeriods() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.add(CLAN, ALEX, ActivityCategory.WAR, 25, WED);
        for (ActivityPeriod period : ActivityPeriod.values()) {
            assertEquals(25, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, period, WED)).war());
        }
    }

    @Test
    void weekAndMonthRollOverButLifetimeStays() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.add(CLAN, ALEX, ActivityCategory.RAID, 60, WED);
        assertEquals(0, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, ActivityPeriod.WEEKLY, NEXT_WEEK)).total());
        assertEquals(60, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, ActivityPeriod.MONTHLY, NEXT_WEEK)).total());
        assertEquals(0, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, ActivityPeriod.MONTHLY, NEXT_MONTH)).total());
        assertEquals(60, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, ActivityPeriod.LIFETIME, NEXT_MONTH)).total());
    }

    @Test
    void isoWeekKeySpansNewYear() {
        long dec31 = Instant.parse("2026-12-31T10:00:00Z").toEpochMilli();
        long jan1 = Instant.parse("2027-01-01T10:00:00Z").toEpochMilli();
        assertEquals(PeriodKeys.of(ActivityPeriod.WEEKLY, dec31), PeriodKeys.of(ActivityPeriod.WEEKLY, jan1));
    }

    @Test
    void eventKeyPaysOnlyOnce() {
        ActivityLedger ledger = new ActivityLedger();
        assertTrue(ledger.claim("war:1:" + ALEX));
        assertFalse(ledger.claim("war:1:" + ALEX));
    }

    @Test
    void clanTotalsAndRankingAreSeparatePerClan() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.add(CLAN, ALEX, ActivityCategory.WAR, 100, WED);
        ledger.add(CLAN, MAX, ActivityCategory.ECONOMY, 300, WED);
        ledger.add(OTHER_CLAN, ALEX, ActivityCategory.WAR, 5, WED);

        assertEquals(400, ClanActivity.of(CLAN, ledger.clanTotals(CLAN, ActivityPeriod.LIFETIME, WED)).total());
        List<PlayerActivity> ranking = ledger.membersRanking(CLAN, ActivityPeriod.LIFETIME, WED);
        assertEquals(MAX, ranking.get(0).playerId());
        assertEquals(CLAN, ledger.clanRanking(ActivityPeriod.LIFETIME, WED, 5).get(0).clanId());
        // a player's own activity adds up over every clan he earned it in
        assertEquals(105, PlayerActivity.of(ALEX, ledger.playerTotals(ALEX, ActivityPeriod.LIFETIME, WED)).total());
    }

    @Test
    void nonPositiveAmountsAreIgnored() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.add(CLAN, ALEX, ActivityCategory.WAR, 0, WED);
        ledger.add(CLAN, ALEX, ActivityCategory.WAR, -5, WED);
        assertTrue(ledger.drainDirty().isEmpty());
    }

    @Test
    void dirtyCellsAreDrainedOnceAndRequeuedOnFailure() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.add(CLAN, ALEX, ActivityCategory.SIEGE, 30, WED);
        List<ActivityLedger.Cell> cells = ledger.drainDirty();
        assertEquals(3, cells.size()); // lifetime, week, month
        assertTrue(ledger.drainDirty().isEmpty());
        ledger.requeue(cells);
        assertEquals(3, ledger.drainDirty().size());
    }

    @Test
    void upsertSqlRunsOnSqliteAndOverwritesWithAbsoluteValue() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(ActivitySchema.CREATE_ACTIVITY);
                st.executeUpdate(ActivitySchema.CREATE_PROCESSED);
            }
            for (long value : new long[]{10, 25}) {
                try (PreparedStatement ps = c.prepareStatement(ActivitySchema.upsertSql(false))) {
                    ps.setString(1, CLAN.toString());
                    ps.setString(2, ALEX.toString());
                    ps.setString(3, "L");
                    ps.setString(4, "WAR");
                    ps.setLong(5, value);
                    ps.executeUpdate();
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*), MAX(points) FROM clan_activity")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
                assertEquals(25, rs.getLong(2));
            }
            for (int i = 0; i < 2; i++) { // the second insert of the same key is ignored, not an error
                try (PreparedStatement ps = c.prepareStatement(ActivitySchema.insertProcessedSql(false))) {
                    ps.setString(1, "war:1");
                    ps.setLong(2, 1);
                    ps.executeUpdate();
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clan_activity_processed")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
