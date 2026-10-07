package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Shared gui_gen v2.1 layout of the 54-slot clan storage screens (Склад, raid loot, trade picker):
 * rows 0-4 (slots 0-44) hold items, the last row is a non-interactive footer - glass on 45-51,
 * Back on 52 (when the screen has somewhere to go back to), Close on 53.
 */
public final class ChestLayout {
    public static final int SIZE = 54;
    /** Item rows available for storage; the sixth row is the footer. */
    public static final int STORAGE_ROWS = 5;
    public static final int STORAGE_SLOTS = STORAGE_ROWS * 9;
    public static final int EXTRA_SLOT = 51;
    public static final int BACK_SLOT = 52;
    public static final int CLOSE_SLOT = 53;

    private ChestLayout() {}

    /** Number of live storage slots for a clan with {@code chestRows} unlocked rows, never reaching the footer. */
    public static int unlockedSlots(int chestRows, int contentsLength) {
        int rows = Math.max(0, Math.min(chestRows, STORAGE_ROWS));
        return Math.min(contentsLength, rows * 9);
    }

    public static boolean isFooter(int rawSlot) {
        return rawSlot >= STORAGE_SLOTS && rawSlot < SIZE;
    }

    /** Draws the footer row. Without a back target slot 52 stays glass, as gui_gen requires. */
    public static void drawFooter(Inventory inventory, LoveClansPlugin plugin, Player player, boolean withBack) {
        for (int slot = STORAGE_SLOTS; slot < CLOSE_SLOT; slot++) {
            inventory.setItem(slot, GuiFrames.glassPane());
        }
        if (withBack) {
            inventory.setItem(BACK_SLOT, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                    .name(plugin.getMessages().component("gui.back", player))
                    .build());
        }
        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());
    }
}
