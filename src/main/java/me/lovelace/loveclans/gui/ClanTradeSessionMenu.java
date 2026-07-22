package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanTradeSessionManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live, two-sided clan trade negotiation window - opened for both clans' representatives at once
 * on the SAME shared {@link Inventory} instance right after a trade invite is accepted (see
 * ClanTradeManager). The 54-slot layout is split down the middle: the left block (cols 0-3) is
 * clan A's offer, the right block (cols 5-8) is clan B's offer, each side can only touch its own
 * half, and money is set per side via a chat prompt. Either side toggling "ready" is remembered;
 * any further change to either half resets both sides back to not-ready. The trade only executes
 * once both sides are ready at the same time - mirrors a normal player-to-player LoveTrades trade,
 * just clan-vs-clan.
 */
public final class ClanTradeSessionMenu implements Listener {
    private static final int[] ITEMS_A = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] ITEMS_B = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int INFO_A_SLOT = 0;
    private static final int INFO_B_SLOT = 8;
    private static final int TRADE_ICON_SLOT = 4;
    private static final int MONEY_A_SLOT = 38;
    private static final int MONEY_B_SLOT = 42;
    private static final int READY_A_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int READY_B_SLOT = 53;
    private static final int INVENTORY_SIZE = 54;

    private final LoveClansPlugin plugin;
    private final ClanTradeSessionManager sessionManager;
    private final UUID clanAId;
    private final UUID clanBId;
    private final UUID playerAId;
    private final UUID playerBId;
    private final String clanATag;
    private final String clanBTag;
    private final String clanATagColor;
    private final String clanBTagColor;
    private final Material clanAEmblem;
    private final Material clanBEmblem;
    private final Inventory inventory;

    private long moneyA = 0L;
    private long moneyB = 0L;
    private boolean readyA = false;
    private boolean readyB = false;
    private boolean resolved = false;

    public ClanTradeSessionMenu(LoveClansPlugin plugin, ClanTradeSessionManager sessionManager,
                                 Clan clanA, Clan clanB, Player playerA, Player playerB) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.clanAId = clanA.id();
        this.clanBId = clanB.id();
        this.playerAId = playerA.getUniqueId();
        this.playerBId = playerB.getUniqueId();
        this.clanATag = clanA.tag();
        this.clanBTag = clanB.tag();
        this.clanATagColor = clanA.tagColor();
        this.clanBTagColor = clanB.tagColor();
        this.clanAEmblem = clanA.emblem();
        this.clanBEmblem = clanB.emblem();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessages().component("gui.trade-session.title",
                        Map.of("tagA", clanA.tag(), "tagB", clanB.tag()), playerA));
        setupStaticSlots();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openForBoth() {
        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) playerA.openInventory(inventory);
        if (playerB != null) playerB.openInventory(inventory);
    }

    private void setupStaticSlots() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
        for (int slot : ITEMS_A) inventory.setItem(slot, null);
        for (int slot : ITEMS_B) inventory.setItem(slot, null);
        inventory.setItem(TRADE_ICON_SLOT, ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                .name(plugin.getMessages().component("gui.trade-session.icon.name")).build());
        render();
    }

    private void render() {
        inventory.setItem(INFO_A_SLOT, ItemBuilder.of(clanAEmblem.name().endsWith("_BANNER") ? clanAEmblem : Material.WHITE_BANNER)
                .name(plugin.getMessages().component("gui.trade-session.side.name", Map.of("tag", clanATag, "color", clanATagColor)))
                .lore(plugin.getMessages().component(readyA ? "gui.trade-session.side.ready" : "gui.trade-session.side.not-ready"))
                .build());
        inventory.setItem(INFO_B_SLOT, ItemBuilder.of(clanBEmblem.name().endsWith("_BANNER") ? clanBEmblem : Material.WHITE_BANNER)
                .name(plugin.getMessages().component("gui.trade-session.side.name", Map.of("tag", clanBTag, "color", clanBTagColor)))
                .lore(plugin.getMessages().component(readyB ? "gui.trade-session.side.ready" : "gui.trade-session.side.not-ready"))
                .build());

        inventory.setItem(MONEY_A_SLOT, ItemBuilder.of(Material.GOLD_INGOT)
                .name(plugin.getMessages().component("gui.trade-session.money.name"))
                .lore(plugin.getMessages().component("gui.trade-session.money.lore", Map.of("amount", String.valueOf(moneyA))))
                .build());
        inventory.setItem(MONEY_B_SLOT, ItemBuilder.of(Material.GOLD_INGOT)
                .name(plugin.getMessages().component("gui.trade-session.money.name"))
                .lore(plugin.getMessages().component("gui.trade-session.money.lore", Map.of("amount", String.valueOf(moneyB))))
                .build());

        inventory.setItem(READY_A_SLOT, ItemBuilder.head(readyA ? ItemBuilder.HEAD_WOOL_LIME : ItemBuilder.HEAD_WOOL_RED)
                .name(plugin.getMessages().component(readyA ? "gui.trade-session.ready.on" : "gui.trade-session.ready.off"))
                .build());
        inventory.setItem(READY_B_SLOT, ItemBuilder.head(readyB ? ItemBuilder.HEAD_WOOL_LIME : ItemBuilder.HEAD_WOOL_RED)
                .name(plugin.getMessages().component(readyB ? "gui.trade-session.ready.on" : "gui.trade-session.ready.off"))
                .build());

        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.trade-session.cancel.name"))
                .build());
    }

    private boolean isItemSlotOf(int slot, boolean sideA) {
        for (int s : (sideA ? ITEMS_A : ITEMS_B)) {
            if (s == slot) return true;
        }
        return false;
    }

    private void resetReadyAndRerender() {
        readyA = false;
        readyB = false;
        render();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // InventoryClickEvent#getInventory() is slot-aware (returns whichever of top/bottom the
        // clicked slot belongs to), unlike Drag/Close - so unlike those two, this must compare
        // against the top inventory specifically, or every bottom-inventory click (including the
        // shift-clicks this handler needs to block below) would skip this handler entirely.
        if (!event.getView().getTopInventory().equals(inventory)) return;
        UUID clickerId = event.getWhoClicked().getUniqueId();
        boolean isA = clickerId.equals(playerAId);
        boolean isB = clickerId.equals(playerBId);
        if (!isA && !isB) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= inventory.getSize()) {
            // Клик в собственном инвентаре игрока - шифт-клик запрещаем полностью (см. класс doc),
            // чтобы Bukkit не мог сам подобрать слот в чужой половине через quick-move.
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot == CLOSE_SLOT) {
            event.setCancelled(true);
            abort((Player) event.getWhoClicked());
            return;
        }
        if (rawSlot == READY_A_SLOT) {
            event.setCancelled(true);
            if (isA) { readyA = !readyA; onReadyChanged(); }
            return;
        }
        if (rawSlot == READY_B_SLOT) {
            event.setCancelled(true);
            if (isB) { readyB = !readyB; onReadyChanged(); }
            return;
        }
        if (rawSlot == MONEY_A_SLOT) {
            event.setCancelled(true);
            if (isA) promptMoney(true);
            return;
        }
        if (rawSlot == MONEY_B_SLOT) {
            event.setCancelled(true);
            if (isB) promptMoney(false);
            return;
        }
        if (isItemSlotOf(rawSlot, true)) {
            if (!isA) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, this::resetReadyAndRerender);
            return;
        }
        if (isItemSlotOf(rawSlot, false)) {
            if (!isB) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, this::resetReadyAndRerender);
            return;
        }
        // Любой другой (застеклённый/декоративный) слот верхнего инвентаря - не трогаем.
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        UUID draggerId = event.getWhoClicked().getUniqueId();
        boolean isA = draggerId.equals(playerAId);
        boolean isB = draggerId.equals(playerBId);
        if (!isA && !isB) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= inventory.getSize()) continue;
            if (!isItemSlotOf(slot, isA)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, this::resetReadyAndRerender);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (resolved) return;
        if (event.getPlayer() instanceof Player player) {
            abort(player);
        }
    }

    private void onReadyChanged() {
        render();
        if (readyA && readyB) {
            execute();
        }
    }

    private void promptMoney(boolean sideA) {
        UUID promptedId = sideA ? playerAId : playerBId;
        Player player = Bukkit.getPlayer(promptedId);
        if (player == null) return;
        plugin.getMessages().send(player, "gui.trade-session.money.prompt");
        plugin.expectChatInput(promptedId, (input, cancelled) -> {
            if (cancelled || resolved) return;
            long amount;
            try {
                amount = Long.parseLong(input.trim());
            } catch (NumberFormatException exception) {
                plugin.getMessages().send(player, "general.invalid-number");
                return;
            }
            if (amount < 0) {
                plugin.getMessages().send(player, "general.invalid-number");
                return;
            }
            if (sideA) moneyA = amount; else moneyB = amount;
            plugin.runSync(this::resetReadyAndRerender);
        });
    }

    private List<ItemStack> collect(int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private void execute() {
        if (resolved) return;
        resolved = true;

        var clanManager = plugin.getClanManager();
        Clan freshA = clanManager.getClanById(clanAId).orElse(null);
        Clan freshB = clanManager.getClanById(clanBId).orElse(null);
        if (freshA == null || freshB == null) {
            // One of the two clans is gone (disbanded mid-negotiation) - there is no chest left to
            // trade with, so unlike the other failure cases below this can't be retried; abort outright.
            Player playerA = Bukkit.getPlayer(playerAId);
            Player playerB = Bukkit.getPlayer(playerBId);
            if (playerA != null) plugin.getMessages().send(playerA, "trade.session.clan-gone");
            if (playerB != null) plugin.getMessages().send(playerB, "trade.session.clan-gone");
            resolved = false;
            abort(null);
            return;
        }
        if (freshA.chestMoney() < moneyA) {
            resolved = false;
            readyA = false;
            failAndKeepOpen("trade.session.insufficient-funds-a");
            return;
        }
        if (freshB.chestMoney() < moneyB) {
            resolved = false;
            readyB = false;
            failAndKeepOpen("trade.session.insufficient-funds-b");
            return;
        }

        List<ItemStack> itemsA = collect(ITEMS_A);
        List<ItemStack> itemsB = collect(ITEMS_B);

        clanManager.removeChestMoneyAsync(freshA, moneyA)
                .thenCompose(ignored -> clanManager.removeChestMoneyAsync(freshB, moneyB))
                .thenCompose(ignored -> clanManager.depositRewardToChestAsync(freshA, moneyB))
                .thenCompose(ignored -> clanManager.depositRewardToChestAsync(freshB, moneyA))
                .thenCompose(ignored -> clanManager.depositItemsToChestAsync(freshA, itemsB))
                .thenCompose(leftoverA -> clanManager.depositItemsToChestAsync(freshB, itemsA)
                        .thenApply(leftoverB -> new ItemStack[][]{leftoverA.toArray(new ItemStack[0]), leftoverB.toArray(new ItemStack[0])}))
                .thenAccept(leftovers -> plugin.runSync(() -> finishExecution(leftovers[0], leftovers[1])))
                .exceptionally(t -> {
                    plugin.runSync(() -> {
                        resolved = false;
                        plugin.getLogger().warning("Clan trade session execution failed: " + t.getMessage());
                        failAndKeepOpen("trade.session.failed");
                    });
                    return null;
                });
    }

    private void finishExecution(ItemStack[] leftoverA, ItemStack[] leftoverB) {
        for (int slot : ITEMS_A) inventory.setItem(slot, null);
        for (int slot : ITEMS_B) inventory.setItem(slot, null);
        dropLeftovers(playerAId, leftoverA);
        dropLeftovers(playerBId, leftoverB);

        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) {
            plugin.getMessages().send(playerA, "trade.session.completed", Map.of("tag", clanBTag));
            playerA.closeInventory();
        }
        if (playerB != null) {
            plugin.getMessages().send(playerB, "trade.session.completed", Map.of("tag", clanATag));
            playerB.closeInventory();
        }
        cleanup();
    }

    private void dropLeftovers(UUID playerId, ItemStack[] leftovers) {
        if (leftovers.length == 0) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        for (ItemStack item : leftovers) {
            if (item == null || item.getType().isAir()) continue;
            var overflow = player.getInventory().addItem(item);
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private void failAndKeepOpen(String messageKey) {
        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) plugin.getMessages().send(playerA, messageKey);
        if (playerB != null) plugin.getMessages().send(playerB, messageKey);
        render();
    }

    /** Either side backing out (Cancel button, closing the window, or disconnecting) ends the whole negotiation. */
    public void abort(Player initiator) {
        if (resolved) return;
        resolved = true;

        returnItemsTo(playerAId, ITEMS_A);
        returnItemsTo(playerBId, ITEMS_B);

        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) {
            plugin.getMessages().send(playerA, "trade.session.cancelled", Map.of("tag", clanBTag));
            if (!playerA.equals(initiator)) playerA.closeInventory();
        }
        if (playerB != null) {
            plugin.getMessages().send(playerB, "trade.session.cancelled", Map.of("tag", clanATag));
            if (!playerB.equals(initiator)) playerB.closeInventory();
        }
        cleanup();
    }

    private void returnItemsTo(UUID playerId, int[] slots) {
        Player player = Bukkit.getPlayer(playerId);
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            inventory.setItem(slot, null);
            if (item == null || item.getType().isAir()) continue;
            if (player == null) {
                plugin.getLogger().warning("Could not return a clan trade item: representative " + playerId + " is offline.");
                continue;
            }
            var overflow = player.getInventory().addItem(item);
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private void cleanup() {
        HandlerList.unregisterAll(this);
        sessionManager.unregister(this, clanAId, clanBId, playerAId, playerBId);
    }
}
