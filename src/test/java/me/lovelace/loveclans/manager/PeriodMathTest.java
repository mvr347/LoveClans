package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ContractType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodMathTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void weekRunsMondayToNextMonday() {
        // 2026-10-07 is a Wednesday
        LocalDate wednesday = LocalDate.of(2026, 10, 7);
        assertEquals(LocalDate.of(2026, 10, 5), PeriodMath.periodStart(ContractType.WEEKLY, wednesday));
        assertEquals(LocalDate.of(2026, 10, 12), PeriodMath.nextPeriodStart(ContractType.WEEKLY, wednesday));
    }

    @Test
    void mondayBelongsToItsOwnWeekAndSundayToThePreviousMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), PeriodMath.periodStart(ContractType.WEEKLY, LocalDate.of(2026, 10, 5)));
        assertEquals(LocalDate.of(2026, 10, 5), PeriodMath.periodStart(ContractType.WEEKLY, LocalDate.of(2026, 10, 11)));
    }

    @Test
    void weekCrossesYearBoundary() {
        // 2026-12-31 is a Thursday; its week ends on Monday 2027-01-04
        LocalDate thursday = LocalDate.of(2026, 12, 31);
        assertEquals(LocalDate.of(2026, 12, 28), PeriodMath.periodStart(ContractType.WEEKLY, thursday));
        assertEquals(LocalDate.of(2027, 1, 4), PeriodMath.nextPeriodStart(ContractType.WEEKLY, thursday));
    }

    @Test
    void monthLengthsAndLeapYear() {
        assertEquals(LocalDate.of(2026, 11, 1), PeriodMath.nextPeriodStart(ContractType.MONTHLY, LocalDate.of(2026, 10, 31)));
        assertEquals(LocalDate.of(2026, 10, 1), PeriodMath.nextPeriodStart(ContractType.MONTHLY, LocalDate.of(2026, 9, 30)));
        assertEquals(LocalDate.of(2027, 3, 1), PeriodMath.nextPeriodStart(ContractType.MONTHLY, LocalDate.of(2027, 2, 28)));
        assertEquals(LocalDate.of(2028, 3, 1), PeriodMath.nextPeriodStart(ContractType.MONTHLY, LocalDate.of(2028, 2, 29)));
    }

    @Test
    void monthCrossesYearBoundary() {
        LocalDate date = LocalDate.of(2026, 12, 15);
        assertEquals(LocalDate.of(2026, 12, 1), PeriodMath.periodStart(ContractType.MONTHLY, date));
        assertEquals(LocalDate.of(2027, 1, 1), PeriodMath.nextPeriodStart(ContractType.MONTHLY, date));
    }

    @Test
    void everyDayOfAPeriodSharesTheSameStartAndEnd() {
        for (ContractType type : ContractType.values()) {
            long start = PeriodMath.startMillis(type, LocalDate.of(2026, 10, 7), UTC);
            long end = PeriodMath.endMillis(type, LocalDate.of(2026, 10, 7), UTC);
            LocalDate day = PeriodMath.periodStart(type, LocalDate.of(2026, 10, 7));
            LocalDate next = PeriodMath.nextPeriodStart(type, LocalDate.of(2026, 10, 7));
            for (LocalDate d = day; d.isBefore(next); d = d.plusDays(1)) {
                assertEquals(start, PeriodMath.startMillis(type, d, UTC), type + " start on " + d);
                assertEquals(end, PeriodMath.endMillis(type, d, UTC), type + " end on " + d);
            }
            assertTrue(end > start);
        }
    }

    @Test
    void endIsMidnightEvenAcrossDstShift() {
        // Europe/Berlin leaves summer time on 2026-10-25 (a 25-hour day) - the week containing it must still
        // end exactly at 00:00 local time on the next Monday, not an hour off.
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        long end = PeriodMath.endMillis(ContractType.WEEKLY, LocalDate.of(2026, 10, 22), berlin);
        ZonedDateTime at = java.time.Instant.ofEpochMilli(end).atZone(berlin);
        assertEquals(LocalDate.of(2026, 10, 26), at.toLocalDate());
        assertEquals(0, at.getHour());
        assertEquals(0, at.getMinute());
    }
}
