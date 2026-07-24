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

    public static ItemStack glassPane() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build();
    }
}
