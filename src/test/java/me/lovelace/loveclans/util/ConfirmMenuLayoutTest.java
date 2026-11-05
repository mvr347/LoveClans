package me.lovelace.loveclans.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import me.lovelace.loveclans.gui.BannerPurchaseConfirmMenu;
import me.lovelace.loveclans.gui.ClanConfirmMenu;
import org.junit.jupiter.api.Test;

class ConfirmMenuLayoutTest {
    @Test
    void zonesAreTwoDisjointThreeByThreeBlocksInsideTwentySevenSlots() {
        Set<Integer> all = new HashSet<>();
        for (int s : ConfirmMenuLayout.CONFIRM_ZONE) assertTrue(all.add(s) && s >= 0 && s < 27);
        for (int s : ConfirmMenuLayout.CANCEL_ZONE) assertTrue(all.add(s) && s >= 0 && s < 27);
        assertEquals(18, all.size());
        assertTrue(ConfirmMenuLayout.isConfirm(1) && ConfirmMenuLayout.isConfirm(21));
        assertTrue(ConfirmMenuLayout.isCancel(5) && ConfirmMenuLayout.isCancel(25));
        assertFalse(ConfirmMenuLayout.isConfirm(4) || ConfirmMenuLayout.isCancel(4));
    }

    @Test
    void theSmallWindowKeepsItsOwnSlotsAndIgnoresTheLargeZones() {
        // In the 9-slot window slots 3 and 5 are glass: they must not answer.
        assertFalse(BannerPurchaseConfirmMenu.isConfirmSlot(3, 9));
        assertFalse(BannerPurchaseConfirmMenu.isCancelSlot(5, 9));
        assertTrue(BannerPurchaseConfirmMenu.isConfirmSlot(1, 9));
        assertTrue(BannerPurchaseConfirmMenu.isCancelSlot(7, 9));
        assertTrue(BannerPurchaseConfirmMenu.isConfirmSlot(11, 27));
        assertTrue(ClanConfirmMenu.isAnswerSlot(15, 27));
        assertFalse(ClanConfirmMenu.isAnswerSlot(3, 9));
    }

    @Test
    void anUnresolvedBackgroundPlaceholderKeepsTheVisibleWindow() {
        assertTrue(ConfirmMenuLayout.backgroundResolved("\uE001\uE002"));
        assertFalse(ConfirmMenuLayout.backgroundResolved("%img_confirm_menu%"));
        assertFalse(ConfirmMenuLayout.backgroundResolved("<white>%img_offset_-8%</white>"));
        assertFalse(ConfirmMenuLayout.backgroundResolved("<white>\uE001</white><white>:confirm_menu:</white>"));
        assertFalse(ConfirmMenuLayout.backgroundResolved(""));
        assertFalse(ConfirmMenuLayout.backgroundResolved(null));
    }
}
