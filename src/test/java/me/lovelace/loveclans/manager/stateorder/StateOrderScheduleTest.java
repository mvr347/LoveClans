package me.lovelace.loveclans.manager.stateorder;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateOrderScheduleTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private final StateOrderSchedule schedule = new StateOrderSchedule(StateOrderSchedule.DEFAULT_DAYS, 18);

    private static ZonedDateTime at(int day, int hour, int minute) {
        // October 2026: the 5th is a Monday.
        return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZONE);
    }

    @Test
    void beforeMondaySlotTheLatestIsPreviousFriday() {
        assertEquals(at(2, 18, 0), schedule.latestSlotAtOrBefore(at(5, 17, 59)));
        assertEquals(DayOfWeek.FRIDAY, at(2, 18, 0).getDayOfWeek());
    }

    @Test
    void exactlyAtTheSlotTheSlotIsCurrent() {
        assertEquals(at(5, 18, 0), schedule.latestSlotAtOrBefore(at(5, 18, 0)));
    }

    @Test
    void tuesdayStillBelongsToMondayOrder() {
        assertEquals(at(5, 18, 0), schedule.latestSlotAtOrBefore(at(6, 23, 0)));
    }

    @Test
    void nextSlotSkipsNonOrderDays() {
        assertEquals(at(7, 18, 0), schedule.nextSlotAfter(at(5, 18, 0)));
        assertEquals(at(9, 18, 0), schedule.nextSlotAfter(at(7, 18, 0)));
        assertEquals(at(12, 18, 0), schedule.nextSlotAfter(at(9, 18, 0)));
        assertEquals(at(5, 18, 0), schedule.nextSlotAfter(at(5, 9, 0)));
    }
}
