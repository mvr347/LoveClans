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
    void reasonLineIsALabelPlusGrayTextWithoutPrefixOrForeignColors() {
        String template = MessageService.reasonTemplate("<prefix><dark_gray>Нужны права");
        assertEquals("<red>Причина:</red> <gray>Нужны права</gray>", template);
    }

    @Test
    void customPlaceholderTagsSurviveColorStripping() {
        String template = MessageService.reasonTemplate("<prefix><red>Сумма больше <max>");
        assertEquals("<red>Причина:</red> <gray>Сумма больше <max></gray>", template);
    }
}
