package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.gui.ChestOrder.Slot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestOrderTest {

    private static List<Slot<String>> full(String kind, int slots) {
        List<Slot<String>> list = new ArrayList<>();
        for (int i = 0; i < slots; i++) list.add(new Slot<>(kind, 64));
        return list;
    }

    @Test
    void oneKindInFullStacksIsPerfect() {
        assertTrue(ChestOrder.perfect(full("DIAMOND", 18), 18));
        assertTrue(ChestOrder.perfect(full("DIAMOND", 45), 18));
    }

    @Test
    void tooFewOccupiedSlotsDoNotCount() {
        assertFalse(ChestOrder.perfect(full("DIAMOND", 17), 18));
        assertFalse(ChestOrder.perfect(List.of(), 18));
        assertFalse(ChestOrder.perfect(null, 18));
    }

    @Test
    void aMinimumBelowOneStillNeedsAtLeastOneSlot() {
        assertFalse(ChestOrder.perfect(List.of(), 0));
        assertTrue(ChestOrder.perfect(full("DIAMOND", 1), 0));
    }

    @Test
    void aTwoKindMixIsNotPerfect() {
        List<Slot<String>> mixed = full("DIAMOND", 18);
        mixed.set(7, new Slot<>("GOLD_INGOT", 64));
        assertFalse(ChestOrder.perfect(mixed, 18));
    }

    @Test
    void oneShortStackSpoilsIt() {
        List<Slot<String>> slots = full("DIAMOND", 18);
        slots.set(17, new Slot<>("DIAMOND", 63));
        assertFalse(ChestOrder.perfect(slots, 18));
    }

    @Test
    void shippedConfigAndLangKnowThePerfectionists() throws Exception {
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("config.yml"), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(config.getBoolean("easter-eggs.perfectionists.enabled"));
        assertEquals(18, config.getInt("easter-eggs.perfectionists.min-slots"));
        var lang = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("lang.yml"), java.nio.charset.StandardCharsets.UTF_8));
        String key = me.lovelace.loveclans.model.upgrade.GuildReward.PERFECTIONISTS.langKey();
        assertNotNull(lang.getString("gui.rewards.entry." + key + ".name"));
        assertNotNull(lang.getString("gui.rewards.entry." + key + ".bonus"));
        assertEquals(0.0, me.lovelace.loveclans.model.upgrade.GuildReward.PERFECTIONISTS.percent());
    }
}
