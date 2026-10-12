package me.lovelace.loveclans.gui;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChestInsertGuardTest {
    private static String incoming(InventoryAction action, boolean chest, boolean player, boolean offhandSwap) {
        return ChestInsertGuard.incoming(action, chest, player, offhandSwap, "cursor", "clicked", "hotbar", "offhand");
    }

    @Test
    void placingFromTheCursorIntoTheChest() {
        for (InventoryAction action : new InventoryAction[]{InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
                InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR}) {
            assertEquals("cursor", incoming(action, true, false, false), action.name());
        }
    }

    @Test
    void shiftClickFromTheInventoryMovesTheClickedStack() {
        assertEquals("clicked", incoming(InventoryAction.MOVE_TO_OTHER_INVENTORY, false, true, false));
    }

    @Test
    void hotbarAndOffhandSwapsBringTheirOwnStack() {
        assertEquals("hotbar", incoming(InventoryAction.HOTBAR_SWAP, true, false, false));
        assertEquals("offhand", incoming(InventoryAction.HOTBAR_SWAP, true, false, true));
    }

    @Test
    void takingItemsOutOrClickingElsewhereBringsNothing() {
        assertNull(incoming(InventoryAction.PICKUP_ALL, true, false, false));
        assertNull(incoming(InventoryAction.MOVE_TO_OTHER_INVENTORY, true, false, false), "shift-click inside the chest takes OUT");
        assertNull(incoming(InventoryAction.PLACE_ALL, false, true, false), "placing into own inventory");
        assertNull(incoming(InventoryAction.NOTHING, false, false, false));
    }
}
