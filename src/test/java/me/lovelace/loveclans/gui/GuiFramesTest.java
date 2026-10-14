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
        assertArrayEquals(new int[]{2, 3, 5, 6}, GuiFrames.controlSlots(4));
        assertArrayEquals(new int[]{2, 3, 4, 6, 7}, GuiFrames.controlSlots(5));
    }

    @Test
    void everyLayoutStaysInsideTheControlZoneAndIsSymmetric() {
        for (int count = 1; count <= 6; count++) {
            int[] slots = GuiFrames.controlSlots(count);
            assertEquals(count, slots.length, "count " + count);
            for (int i = 0; i < slots.length; i++) {
                assertTrue(slots[i] >= 2 && slots[i] <= 7, "slot " + slots[i] + " is outside 2..7");
                if (i > 0) assertTrue(slots[i] > slots[i - 1], "slots must be increasing");
                // up to four buttons the layout mirrors around slot 4; five buttons cannot be symmetric inside the six-slot zone
                // 2..7 (the middle button would have to sit on the gap), so {2,3,4,6,7} is asserted explicitly above
                if (count <= 4) assertEquals(8, slots[i] + slots[slots.length - 1 - i], "count " + count);
            }
        }
    }

    @Test
    void moreThanSixButtonsFallBackToTheWholeZone() {
        assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7}, GuiFrames.controlSlots(9));
    }

    @Test
    void workRowButtonsStayInTheContentRowAndAreSymmetric() {
        for (int count = 1; count <= 4; count++) {
            int[] slots = GuiFrames.workRowSlots(count);
            assertEquals(count, slots.length);
            for (int i = 0; i < slots.length; i++) {
                assertTrue(slots[i] >= 10 && slots[i] <= 16, "slot " + slots[i] + " touches a side wall or other zone");
                assertEquals(26, slots[i] + slots[slots.length - 1 - i], "count " + count);
            }
        }
    }
}
