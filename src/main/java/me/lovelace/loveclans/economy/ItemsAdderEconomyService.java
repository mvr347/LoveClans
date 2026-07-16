package me.lovelace.loveclans.economy;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Checks, withdraws and grants ItemsAdder custom items used as an in-game currency for clan
 * creation costs and the clan bank/treasury. There is no Vault economy plugin on this server and
 * none should be assumed — all "money" handled by this plugin is backed by ItemsAdder items.
 *
 * <p>ItemsAdder has no built-in "remove/give N of item" API, so withdrawal manually scans and
 * decrements matching inventory slots (mirrors the approach used by LoveSubnames'
 * {@code ItemsAdderEconomyService}). Every call is guarded by {@link #isAvailable()} since
 * ItemsAdder populates its item registry asynchronously after its own {@code onEnable}, so
 * presence in {@code plugin.yml}'s softdepend list alone doesn't guarantee readiness.
 */
public final class ItemsAdderEconomyService {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /** Returns how many of the given ItemsAdder item the player is carrying. */
    public int countOwned(Player player, String namespacedId) {
        if (!isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            CustomStack customStack = CustomStack.byItemStack(stack);
            if (customStack != null && namespacedId.equals(customStack.getNamespacedID())) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean hasItem(Player player, String namespacedId, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (!isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return false;
        }
        return countOwned(player, namespacedId) >= amount;
    }

    /** Caller must have already verified {@link #hasItem} for this exact amount. */
    public void withdraw(Player player, String namespacedId, long amount) {
        if (amount <= 0 || !isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        long remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) {
                continue;
            }
            CustomStack customStack = CustomStack.byItemStack(stack);
            if (customStack == null || !namespacedId.equals(customStack.getNamespacedID())) {
                continue;
            }
            long take = Math.min(remaining, stack.getAmount());
            if (take >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount((int) (stack.getAmount() - take));
            }
            remaining -= take;
        }
        inventory.setContents(contents);
        player.updateInventory();
    }

    /**
     * Gives the player {@code amount} of the given ItemsAdder item, splitting across
     * max-stack-sized item stacks and dropping any overflow at the player's feet if their
     * inventory is full (mirrors how banner items are returned elsewhere in this plugin).
     */
    public void give(Player player, String namespacedId, long amount) {
        if (amount <= 0 || !isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return;
        }
        CustomStack template = CustomStack.getInstance(namespacedId);
        if (template == null) {
            return;
        }
        ItemStack templateStack = template.getItemStack();
        int maxStackSize = Math.max(1, templateStack.getMaxStackSize());
        long remaining = amount;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, maxStackSize);
            ItemStack toGive = templateStack.clone();
            toGive.setAmount(chunk);
            var overflow = player.getInventory().addItem(toGive);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= chunk;
        }
        player.updateInventory();
    }
}
