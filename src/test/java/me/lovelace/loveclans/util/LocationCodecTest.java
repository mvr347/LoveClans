package me.lovelace.loveclans.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocationCodecTest {
    @Test void roundTripUnderRussianLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"));
            String s = LocationCodec.format("world", 12.5, 64.0, -7.25, 90.5f, -10.0f);
            assertFalse(s.contains(","));
            LocationCodec.Parts p = LocationCodec.parse(s);
            assertNotNull(p);
            assertEquals("world", p.world());
            assertEquals(12.5, p.x());
            assertEquals(-7.25, p.z());
            assertEquals(90.5f, p.yaw());
        } finally { Locale.setDefault(old); }
    }
    @Test void legacyCommaInputIsAccepted() {
        LocationCodec.Parts p = LocationCodec.parse("world;12,500000;64,000000;-7,250000;90,500000;-10,000000");
        assertNotNull(p);
        assertEquals(12.5, p.x());
        assertEquals(-10.0f, p.pitch());
    }
    @Test void malformedReturnsNull() {
        assertNull(LocationCodec.parse(null));
        assertNull(LocationCodec.parse("a;b"));
        assertNull(LocationCodec.parse("w;x;1;2;3;4"));
    }
}
