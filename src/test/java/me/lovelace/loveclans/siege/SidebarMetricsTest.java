package me.lovelace.loveclans.siege;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

class SidebarMetricsTest {
    @Test
    void colourCodesTakeNoWidth() {
        assertEquals(SidebarMetrics.width("abc"), SidebarMetrics.width("§7a§fb§cc"));
    }

    @Test
    void boldAddsOnePixelPerGlyph() {
        assertEquals(SidebarMetrics.width("abc") + 3, SidebarMetrics.width("§labc"));
    }

    @Test
    void centredLineIsNotNarrowerThanTheTargetAndUsesNonBreakingSpaces() {
        var line = (TextComponent) SidebarMetrics.center("§cVOID", 170);
        assertTrue(SidebarMetrics.width(line) >= 160);
        assertTrue(line.content().startsWith(" "));
        assertTrue(line.content().endsWith(" "));
    }
}
