package me.lovelace.loveclans.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class GuiFramesContentRowTest {
    @Test
    void buttonsAreCenteredAndSideWallsStayEmpty() {
        assertArrayEquals(new int[]{22}, GuiFrames.contentRowSlots(18, 1));
        assertArrayEquals(new int[]{30, 32}, GuiFrames.contentRowSlots(27, 2));
        assertArrayEquals(new int[]{38, 40, 42}, GuiFrames.contentRowSlots(36, 3));
        assertArrayEquals(new int[]{37, 39, 41, 43}, GuiFrames.contentRowSlots(36, 4));
        for (int count = 1; count <= 9; count++) {
            for (int slot : GuiFrames.contentRowSlots(18, count)) {
                int col = slot - 18;
                if (col < 1 || col > 7) throw new AssertionError("wall slot used for " + count + ": " + slot);
            }
        }
    }
}
