package me.lovelace.loveclans.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceColorTest {

    @Test
    void missingColorValueDropsTheBareTag() {
        assertEquals("Клан <tag>!", MessageService.stripUnboundColorTag("Клан <color><tag></color>!", Map.of("tag", "ABC")));
    }

    @Test
    void presentColorValueIsLeftForTheResolver() {
        String raw = "Клан <color><tag></color>!";
        assertEquals(raw, MessageService.stripUnboundColorTag(raw, Map.of("tag", "ABC", "color", "<gold>")));
    }

    @Test
    void textWithoutColorIsUntouched() {
        assertEquals("<green>ok", MessageService.stripUnboundColorTag("<green>ok", Map.of()));
    }
}
