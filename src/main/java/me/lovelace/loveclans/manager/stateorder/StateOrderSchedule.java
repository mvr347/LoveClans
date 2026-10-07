package me.lovelace.loveclans.manager.stateorder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * When state orders open: on the configured weekdays (Mon/Wed/Fri by default) at the configured hour, in the
 * server's time zone. An order lives from its slot until the next slot. Pure time arithmetic, no Bukkit.
 */
public final class StateOrderSchedule {
    public static final Set<DayOfWeek> DEFAULT_DAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    private final Set<DayOfWeek> days;
    private final int hour;

    public StateOrderSchedule(Set<DayOfWeek> days, int hour) {
        Objects.requireNonNull(days, "days");
        this.days = days.isEmpty() ? EnumSet.copyOf(DEFAULT_DAYS) : EnumSet.copyOf(days);
        this.hour = Math.max(0, Math.min(23, hour));
    }

    private boolean isSlotDay(LocalDate date) {
        return days.contains(date.getDayOfWeek());
    }

    private ZonedDateTime slotOn(LocalDate date, ZonedDateTime zoneSource) {
        return ZonedDateTime.of(date, LocalTime.of(hour, 0), zoneSource.getZone());
    }

    /** The most recent slot at or before {@code now} (always exists: at most 7 days back). */
    public ZonedDateTime latestSlotAtOrBefore(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        for (int i = 0; i <= 7; i++) {
            LocalDate candidate = date.minusDays(i);
            if (!isSlotDay(candidate)) continue;
            ZonedDateTime slot = slotOn(candidate, now);
            if (!slot.isAfter(now)) return slot;
        }
        throw new IllegalStateException("no slot within a week");
    }

    /** The first slot strictly after {@code moment}. */
    public ZonedDateTime nextSlotAfter(ZonedDateTime moment) {
        LocalDate date = moment.toLocalDate();
        for (int i = 0; i <= 8; i++) {
            LocalDate candidate = date.plusDays(i);
            if (!isSlotDay(candidate)) continue;
            ZonedDateTime slot = slotOn(candidate, moment);
            if (slot.isAfter(moment)) return slot;
        }
        throw new IllegalStateException("no slot within a week");
    }
}
