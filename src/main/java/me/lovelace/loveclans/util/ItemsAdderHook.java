package me.lovelace.loveclans.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Безопасный хук для ItemsAdder через рефлексию (без compile-time зависимости).
 * Позволяет получать кастомные предметы (CustomStack) и проверять их наличие.
 */
public final class ItemsAdderHook {

    private static final Logger LOGGER = Logger.getLogger("LoveClans");

    private ItemsAdderHook() {}

    /**
     * Проверяет, включён ли плагин ItemsAdder.
     */
    public static boolean isAvailable() {
        return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * Создаёт копию ItemStack кастомного предмета ItemsAdder по его ID (например, "loveclans:casus_belli_scroll").
     * Возвращает {@code null}, если ItemsAdder не установлен или предмет с таким ID не зарегистрирован.
     */
    public static ItemStack createCustomStack(String id) {
        if (id == null || id.isBlank() || !isAvailable()) {
            return null;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstanceMethod.invoke(null, id);
            if (customStack != null) {
                Method getItemStackMethod = customStackClass.getMethod("getItemStack");
                Object itemObj = getItemStackMethod.invoke(customStack);
                if (itemObj instanceof ItemStack itemStack) {
                    return itemStack.clone();
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.FINEST, "ItemsAdder CustomStack class not found: " + e.getMessage());
        } catch (Throwable t) {
            LOGGER.log(Level.FINEST, "ItemsAdder createCustomStack failed for id '" + id + "': " + t.getMessage());
        }
        return null;
    }

    /**
     * Проверяет, является ли предмет указанным кастомным предметом ItemsAdder.
     */
    public static boolean isCustomStack(ItemStack item, String id) {
        if (item == null || id == null || id.isBlank() || !isAvailable()) {
            return false;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method byItemStackMethod = customStackClass.getMethod("byItemStack", ItemStack.class);
            Object customStack = byItemStackMethod.invoke(null, item);
            if (customStack != null) {
                Method getNamespacedIDMethod = customStackClass.getMethod("getNamespacedID");
                Object namespacedId = getNamespacedIDMethod.invoke(customStack);
                return id.equalsIgnoreCase(String.valueOf(namespacedId));
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINEST, "ItemsAdder isCustomStack check failed: " + t.getMessage());
        }
        return false;
    }
}
