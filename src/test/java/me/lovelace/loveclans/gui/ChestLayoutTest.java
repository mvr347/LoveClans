package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** gui_gen v2.1 layout of Склад (ChestLayout) and Казна (ClanChestMoneyMenu). */
class ChestLayoutTest {

    @Test
    void storageNeverReachesFooter() {
        assertEquals(45, ChestLayout.unlockedSlots(6, 54));
        assertEquals(45, ChestLayout.unlockedSlots(5, 54));
        assertEquals(27, ChestLayout.unlockedSlots(3, 54));
        assertEquals(0, ChestLayout.unlockedSlots(-1, 54));
        for (int slot = 0; slot < 45; slot++) assertFalse(ChestLayout.isFooter(slot));
        for (int slot = 45; slot < 54; slot++) assertTrue(ChestLayout.isFooter(slot));
        assertEquals(52, ChestLayout.BACK_SLOT);
        assertEquals(53, ChestLayout.CLOSE_SLOT);
    }

    @Test
    void treasuryCoinsOnlyInWorkZoneWithoutWalls() {
        Set<Integer> seen = new HashSet<>();
        for (int slot : ClanChestMoneyMenu.COIN_SLOTS) {
            assertTrue(slot >= 18 && slot < 45, "inside the work zone: " + slot);
            int column = slot % 9;
            assertTrue(column != 0 && column != 8, "side walls stay empty: " + slot);
            assertTrue(seen.add(slot));
        }
        assertEquals(21, seen.size());
        assertEquals(52, ClanChestMoneyMenu.BACK_SLOT);
        assertEquals(53, ClanChestMoneyMenu.CLOSE_SLOT);
        assertEquals(54, ClanChestMoneyMenu.SIZE);
    }
}
