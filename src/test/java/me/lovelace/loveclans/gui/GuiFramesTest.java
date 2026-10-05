package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiFramesTest {

    @Test
    void controlButtonsAreCenteredInHeaderSlotsTwoToSeven() {
        assertArrayEquals(new int[0], GuiFrames.controlSlots(0));
        assertArrayEquals(new int[]{4}, GuiFrames.controlSlots(1));
        assertArrayEquals(new int[]{3, 5}, GuiFrames.controlSlots(2));
        assertArrayEquals(new int[]{2, 4, 6}, GuiFrames.controlSlots(3));
    }

    @Test
    void everyLayoutStaysInsideTheControlZoneAndIsSymmetric() {
        for (int count = 1; count <= 6; count++) {
            int[] slots = GuiFrames.controlSlots(count);
            assertEquals(count, slots.length, "count " + count);
            for (int i = 0; i < slots.length; i++) {
                assertTrue(slots[i] >= 2 && slots[i] <= 7, "slot " + slots[i] + " is outside 2..7");
                if (i > 0) assertTrue(slots[i] > slots[i - 1], "slots must be increasing");
                // up to five buttons the layout is a mirror image around the middle of the row (slot 4)
                if (count <= 5) assertEquals(8, slots[i] + slots[slots.length - 1 - i], "count " + count);
            }
        }
    }

    @Test
    void moreThanSixButtonsFallBackToTheWholeZone() {
        assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7}, GuiFrames.controlSlots(9));
    }
}
