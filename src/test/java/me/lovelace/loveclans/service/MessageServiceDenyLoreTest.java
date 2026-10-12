package me.lovelace.loveclans.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageServiceDenyLoreTest {
    @Test
    void prefixTagNeverReachesItemLore() {
        assertEquals("<red>Недостаточно прав", MessageService.stripPrefixTag("<prefix><red>Недостаточно прав"));
        assertFalse(MessageService.stripPrefixTag(" <prefix>текст ").contains("prefix"));
    }

    @Test
    void reasonLineStartsWithARedCrossAndHasNoPrefix() {
        String template = MessageService.reasonTemplate("<prefix><dark_gray>Нужны права");
        assertEquals("<red>✘</red> <dark_gray>Нужны права", template);
    }
}
