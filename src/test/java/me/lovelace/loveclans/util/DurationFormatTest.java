package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormatTest {

    @Test
    void formatsSeconds() {
        assertEquals("1 сек.", DurationFormat.format(0));
        assertEquals("1 сек.", DurationFormat.format(500));
        assertEquals("1 сек.", DurationFormat.format(1000));
        assertEquals("45 сек.", DurationFormat.format(45_000));
        assertEquals("59 сек.", DurationFormat.format(58_100));
    }

    @Test
    void formatsMinutesAndSeconds() {
        assertEquals("1 мин. 0 сек.", DurationFormat.format(60_000));
        assertEquals("2 мин. 15 сек.", DurationFormat.format(135_000));
        assertEquals("59 мин. 59 сек.", DurationFormat.format(3599_000));
    }

    @Test
    void formatsHoursAndMinutes() {
        assertEquals("1 ч. 0 мин.", DurationFormat.format(3600_000));
        assertEquals("1 ч. 30 мин.", DurationFormat.format(5400_000));
        assertEquals("23 ч. 15 мин.", DurationFormat.format((23 * 3600 + 15 * 60) * 1000L));
    }

    @Test
    void formatsDaysAndHours() {
        assertEquals("1 д. 0 ч.", DurationFormat.format(86400_000L));
        assertEquals("1 д. 5 ч.", DurationFormat.format((86400 + 5 * 3600) * 1000L));
        assertEquals("7 д. 12 ч.", DurationFormat.format((7 * 86400 + 12 * 3600) * 1000L));
    }
}
