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

    private static String plain(String raw) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw));
    }

    @Test
    void expandedColorClosesAndDoesNotLeakLiteralTag() {
        String raw = me.lovelace.loveclans.util.ClanColorTags.expand("<green>Клан <color>ABC</color> создан", "color", "<gold>");
        assertEquals("Клан ABC создан", plain(raw));
        var c = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw);
        // last child must be green again, not gold
        var last = c.children().get(c.children().size() - 1);
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN, last.color() != null ? last.color() : c.color());
    }

    @Test
    void hexAndNumberedKeys() {
        String raw = me.lovelace.loveclans.util.ClanColorTags.expand("<color1>A</color1> vs <color2>B</color2>", "color1", "<#ff8800>");
        raw = me.lovelace.loveclans.util.ClanColorTags.expand(raw, "color2", "<red>");
        assertEquals("A vs B", plain(raw));
    }
}
