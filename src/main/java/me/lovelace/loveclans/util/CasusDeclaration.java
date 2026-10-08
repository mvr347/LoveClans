package me.lovelace.loveclans.util;

import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Declares a war or siege against a casus belli: the scroll is taken out of the inventory BEFORE the declaration
 * starts and goes back if the declaration is refused. Taking it first closes the window in which the scroll could be
 * dropped or traded while the (asynchronous) declaration was still running, and the inventory is only touched on the
 * main thread.
 */
public final class CasusDeclaration {
    private CasusDeclaration() {
    }

    /** Main thread. {@code conflictType} is {@code "WAR"} or {@code "SIEGE"}. */
    public static <T> void declare(LoveClansPlugin plugin, Player player, boolean required, UUID targetClanId,
                                   String conflictType, Supplier<CompletableFuture<T>> start) {
        Optional<ItemStack> taken = Optional.empty();
        if (required) {
            taken = plugin.getClanManager().getClanItemFactory().takeCasusBelli(player, targetClanId, conflictType);
            if (taken.isEmpty()) {
                plugin.sendOperationError(player, new IllegalStateException("casus.missing." + conflictType.toLowerCase()));
                return;
            }
        }
        Optional<ItemStack> scroll = taken;
        start.get().whenComplete((result, failure) -> plugin.runSync(() -> {
            if (failure == null) {
                if (scroll.isPresent()) player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
                return;
            }
            scroll.ifPresent(item -> {
                // A full inventory must not eat the scroll: whatever does not fit falls at the player's feet
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                overflow.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            });
            plugin.sendOperationError(player, failure);
        }));
    }
}
