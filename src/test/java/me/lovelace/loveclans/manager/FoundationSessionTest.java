package me.lovelace.loveclans.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FoundationSessionTest {

    @Test
    void transitionsPhasesCorrectly() {
        UUID playerId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        FoundationSession session = new FoundationSession(playerId, "Warriors", "WAR", true, null, FoundationPhase.DATA_READY, now);

        assertEquals(FoundationPhase.DATA_READY, session.phase());
        assertEquals("Warriors", session.name());
        assertEquals("WAR", session.tag());
        assertTrue(session.open());
        assertNull(session.previewLocation());

        FoundationSession previewed = session.withPhase(FoundationPhase.PREVIEWED);
        assertEquals(FoundationPhase.PREVIEWED, previewed.phase());
        assertEquals("Warriors", previewed.name());
        assertEquals("WAR", previewed.tag());
    }

    @Test
    void expirationWorks() {
        UUID playerId = UUID.randomUUID();
        long old = System.currentTimeMillis() - 15 * 60 * 1000L; // 15 mins ago
        FoundationSession session = new FoundationSession(playerId, "Warriors", "WAR", true, null, FoundationPhase.DATA_READY, old);

        assertTrue(session.isExpired(10 * 60 * 1000L));

        FoundationSession fresh = new FoundationSession(playerId, "Warriors", "WAR", true, null, FoundationPhase.DATA_READY, System.currentTimeMillis());
        assertFalse(fresh.isExpired(10 * 60 * 1000L));
    }
}
