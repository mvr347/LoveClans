package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.quest.ContractType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Calendar boundaries of a contract period. Pure functions of (date, zone), so they are stable across
 * restarts and trivially testable. A week runs Monday..Sunday, a month is a calendar month; the end is
 * the START of the next period (exclusive), computed through {@code atStartOfDay} so a DST shift never
 * produces a period that is an hour short or long.
 */
public final class PeriodMath {

    private PeriodMath() {}

    public static LocalDate periodStart(ContractType type, LocalDate date) {
        return type == ContractType.MONTHLY
                ? date.withDayOfMonth(1)
                : date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate nextPeriodStart(ContractType type, LocalDate date) {
        LocalDate start = periodStart(type, date);
        return type == ContractType.MONTHLY ? start.plusMonths(1) : start.plusWeeks(1);
    }

    public static long startMillis(ContractType type, LocalDate date, ZoneId zone) {
        return periodStart(type, date).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static long endMillis(ContractType type, LocalDate date, ZoneId zone) {
        return nextPeriodStart(type, date).atStartOfDay(zone).toInstant().toEpochMilli();
    }
}
