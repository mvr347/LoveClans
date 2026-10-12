package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Whether a stack is LoveEconomy money. Same test the treasury menu uses ({@code valueOf(stack) > 0}), so what the
 * treasury takes as coins is exactly what the item chest refuses.
 */
public final class CurrencyDetector {
    private CurrencyDetector() {
    }

    public static boolean isCurrency(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        return economy().map(economy -> economy.valueOf(stack) > 0).orElse(false);
    }

    private static Optional<LoveEconomy> economy() {
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
