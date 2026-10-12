package me.lovelace.loveclans.gui.conflict;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictReadinessTest {
    @Test
    void allGreenIsReady() {
        ConflictReadiness r = ConflictReadiness.builder()
                .blockIf(false, "deny.a")
                .require("x", true, "deny.x")
                .require("y", true, "deny.y")
                .build();
        assertTrue(r.ready());
        assertTrue(r.firstDeny().isEmpty());
    }

    @Test
    void firstHardBlockWinsAndBeatsRequirements() {
        ConflictReadiness r = ConflictReadiness.builder()
                .blockIf(true, "deny.first")
                .blockIf(true, "deny.second")
                .require("x", false, "deny.x")
                .build();
        assertEquals("deny.first", r.firstDeny().orElseThrow().key());
    }

    @Test
    void firstFailingRequirementInOrderIsTheReason() {
        ConflictReadiness r = ConflictReadiness.builder()
                .require("a", true, "deny.a")
                .require("b", false, Map.of("min", "3"), "deny.b", Map.of("min", "3"))
                .require("c", false, "deny.c")
                .build();
        assertFalse(r.ready());
        assertEquals("deny.b", r.firstDeny().orElseThrow().key());
        assertEquals("3", r.firstDeny().orElseThrow().placeholders().get("min"));
        assertEquals(3, r.requirements().size());
    }
}
