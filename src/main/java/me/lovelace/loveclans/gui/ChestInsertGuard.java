package me.lovelace.loveclans.gui;

import org.bukkit.event.inventory.InventoryAction;

/**
 * Decides which stack a click would drop INTO the clan item chest, so the chest can refuse coins whatever way they come
 * in: placed from the cursor, shift-clicked from the inventory, or swapped in with a hotbar key or the offhand key.
 * Generic over the stack type so the rules are testable without a running server.
 */
public final class ChestInsertGuard {
    private ChestInsertGuard() {
    }

    /**
     * @param clickedChestSlot   the click landed on a live storage slot of the chest
     * @param clickedPlayerSlot  the click landed on the player's own inventory (bottom half)
     * @param offhandSwap        the click type is the offhand swap key
     * @return the stack that would enter the chest, or {@code null} when nothing does
     */
    public static <T> T incoming(InventoryAction action, boolean clickedChestSlot, boolean clickedPlayerSlot,
                                 boolean offhandSwap, T cursor, T clicked, T hotbar, T offhand) {
        if (clickedChestSlot) {
            return switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> cursor;
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> offhandSwap ? offhand : hotbar;
                default -> null;
            };
        }
        if (clickedPlayerSlot && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return clicked;
        }
        return null;
    }
}
