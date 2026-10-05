package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * gui_gen v1.3 стандартные рамки. Заливают ТОЛЬКО чисто рамочные полосы, никогда
 * не трогая рабочую/контент-зону — так неиспользуемые слоты там остаются реально
 * пустыми автоматически, без отдельного шага "расчистить забытое" (правило 10).
 */
public final class GuiFrames {
    private GuiFrames() {}

    /** 27-слотовое: стекло в 1-8 и 18-24 (footer). Слоты 0, 9-17, 25, 26 не трогает. */
    public static void fillFrame27(Inventory inv) {
        for (int i = 1; i <= 8; i++) inv.setItem(i, glassPane());
        for (int i = 18; i <= 24; i++) inv.setItem(i, glassPane());
    }

    /** 54-слотовое: стекло в 1-8, 9-17, 45-52. Слоты 0, 18-44 (вся рабочая зона), 53 не трогает. */
    public static void fillFrame54(Inventory inv) {
        for (int i = 1; i <= 8; i++) inv.setItem(i, glassPane());
        for (int i = 9; i <= 17; i++) inv.setItem(i, glassPane());
        for (int i = 45; i <= 52; i++) inv.setItem(i, glassPane());
    }

    /**
     * gui_gen v2.1, п. 8: control buttons live only in header slots 2-7 and are centered on the row, so the
     * layout stays symmetric whether the screen shows one button or all of them. An even count skips the
     * middle slot (2 buttons -> 3 and 5), an odd one is centered on slot 4. Not-shown buttons leave glass behind.
     */
    public static int[] controlSlots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            default -> new int[]{2, 3, 4, 5, 6, 7};
        };
    }

    public static ItemStack glassPane() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build();
    }
}
