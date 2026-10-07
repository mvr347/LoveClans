package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Calls {@code LoveEconomy#coinStack(Denomination, int)} when the running LoveCore has it. Looked up
 * reflectively so LoveClans keeps working (with the plain {@code give} payout) on a LoveCore that predates
 * the method, and so the compile-time API pin does not have to move in lockstep with the server jar.
 */
public final class CoinStacks {
    private CoinStacks() {}

    public static Optional<ItemStack> of(LoveEconomy economy, Denomination denomination, int amount) {
        if (economy == null || denomination == null || amount <= 0) {
            return Optional.empty();
        }
        try {
            Method method = economy.getClass().getMethod("coinStack", Denomination.class, int.class);
            Object result = method.invoke(economy, denomination, amount);
            if (result instanceof Optional<?> optional && optional.orElse(null) instanceof ItemStack stack) {
                return Optional.of(stack);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // older LoveCore: no coin stacks, callers fall back to economy.give
        }
        return Optional.empty();
    }
}
