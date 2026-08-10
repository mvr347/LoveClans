package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanTradeSessionManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live, two-sided clan trade negotiation window (§4.2, background-job model). Sessions run as
 * background tasks independent of who's online. Lifecycle: EDITING (both sides stage offers) →
 * both ready → LOCKED (3-minute window to cancel/modify, either side can still edit and needs
 * new confirms) → EXECUTING (items/money transferred out of clans, entering delivery phase) →
 * DELIVERING (10-minute async delivery to receiving chests; if full, queued as pending items).
 * Items/money are escrowed from chests immediately as they're staged (ClanTradeItemPickerMenu,
 * money prompts), preventing dupe windows. Permission checks ensure only TRADE-permission holders
 * can view/reopen sessions. A 5% tax applies to money on execution.
 */
public final class ClanTradeSessionMenu implements Listener {
    // Раскладка gui_gen v1.4. Раньше готовность/деньги стояли в шапке (1-8), что нарушает
    // правило 8 - контент/кнопки действия должны жить в рабочей зоне (18-44), а не в рамке.
    // Средний столбец сетки предметов (18/27/36 и 26/35/44) раньше пустовал - теперь там
    // управляющие кнопки каждой стороны, а 22/31/40 остаётся разделителем (31 занят статусом).
    private static final int[] ITEMS_A = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] ITEMS_B = {23, 24, 25, 32, 33, 34, 41, 42, 43};
    private static final int TRADE_ICON_SLOT = 0;
    private static final int ADD_ITEM_A_SLOT = 18;
    private static final int MONEY_A_SLOT = 27;
    private static final int READY_A_SLOT = 36;
    private static final int STATUS_SLOT = 31;
    private static final int ADD_ITEM_B_SLOT = 26;
    private static final int MONEY_B_SLOT = 35;
    private static final int READY_B_SLOT = 44;
    private static final int CLOSE_SLOT = 53;
    private static final int INVENTORY_SIZE = 54;
    private static final long LOCK_WINDOW_MINUTES = 3L;
    private static final long DELIVERY_WINDOW_MINUTES = 10L;
    private static final double TAX_RATE = 0.05;

    private enum Stage { EDITING, LOCKED, EXECUTING, DELIVERING, COMPLETE }

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
    private final Inventory inventory;
    private final Set<UUID> suppressCloseFor = ConcurrentHashMap.newKeySet();

    private long moneyA = 0L; // already withdrawn from clan A's chest, staged for this trade
    private long moneyB = 0L;
    private boolean readyA = false;
    private boolean readyB = false;
    private Stage stage = Stage.EDITING;
    private boolean resolved = false;
    private long lockedUntilMs = 0L; // when lock window expires, allowing execution
    private long deliveryUntilMs = 0L; // when delivery period completes, session closes
    private BukkitTask lockTask;
    private BukkitTask deliveryTask;
    private BukkitTask statusTask;

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
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessages().component("gui.trade-session.title",
                        Map.of("tagA", clanA.tag(), "tagB", clanB.tag()), playerA));
        GuiFrames.fillFrame54(inventory);
        render();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        scheduleStatusTask();
    }

    public void openForBoth() {
        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) playerA.openInventory(inventory);
        if (playerB != null) playerB.openInventory(inventory);
    }

    /** Lets a participant who closed the window (or joined late) get back into a still-open session. */
    public boolean reopenFor(Player player) {
        if (resolved) return false;
        UUID id = player.getUniqueId();
        if (!id.equals(playerAId) && !id.equals(playerBId)) return false;
        player.openInventory(inventory);
        return true;
    }

    private void scheduleStatusTask() {
        statusTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!resolved) inventory.setItem(STATUS_SLOT, statusItem());
        }, 20L * 30L, 20L * 30L);
    }

    private void scheduleLockTask() {
        long ticks = 20L * 60L * LOCK_WINDOW_MINUTES;
        lockedUntilMs = System.currentTimeMillis() + Duration.ofMinutes(LOCK_WINDOW_MINUTES).toMillis();
        lockTask = Bukkit.getScheduler().runTaskLater(plugin, this::onLockExpire, ticks);
    }

    private void onLockExpire() {
        if (resolved || stage != Stage.LOCKED) return;
        execute();
    }

    private void scheduleDeliveryTask() {
        long ticks = 20L * 60L * DELIVERY_WINDOW_MINUTES;
        deliveryUntilMs = System.currentTimeMillis() + Duration.ofMinutes(DELIVERY_WINDOW_MINUTES).toMillis();
        deliveryTask = Bukkit.getScheduler().runTaskLater(plugin, this::onDeliveryComplete, ticks);
    }

    private void onDeliveryComplete() {
        if (resolved || stage != Stage.DELIVERING) return;
        stage = Stage.COMPLETE;
        cleanup();
    }

    private void notifyBoth(String key) {
        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        if (playerA != null) plugin.getMessages().send(playerA, key);
        if (playerB != null) plugin.getMessages().send(playerB, key);
    }

    private void render() {
        inventory.setItem(TRADE_ICON_SLOT, ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                .name(plugin.getMessages().component("gui.trade-session.icon.name")).build());

        boolean locked = stage == Stage.LOCKED;
        inventory.setItem(READY_A_SLOT, readyButton(true, locked));
        inventory.setItem(READY_B_SLOT, readyButton(false, locked));

        inventory.setItem(ADD_ITEM_A_SLOT, addItemButton());
        inventory.setItem(ADD_ITEM_B_SLOT, addItemButton());

        inventory.setItem(MONEY_A_SLOT, moneyButton(moneyA));
        inventory.setItem(MONEY_B_SLOT, moneyButton(moneyB));

        inventory.setItem(STATUS_SLOT, statusItem());

        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.trade-session.cancel.name")).build());
    }

    private ItemStack readyButton(boolean sideA, boolean locked) {
        String tag = sideA ? clanATag : clanBTag;
        String color = sideA ? clanATagColor : clanBTagColor;
        boolean ready = sideA ? readyA : readyB;
        if (locked) {
            String nameKey = "gui.trade-session.lock.name";
            String loreKey = ready ? "gui.trade-session.lock.ready" : "gui.trade-session.lock.not-ready";
            return ItemBuilder.head(ready ? ItemBuilder.HEAD_WOOL_LIME : ItemBuilder.HEAD_WOOL_RED)
                    .name(plugin.getMessages().component(nameKey, Map.of("tag", tag, "color", color)))
                    .lore(plugin.getMessages().component(loreKey))
                    .build();
        }
        String nameKey = "gui.trade-session.side.name";
        String loreKey = ready ? "gui.trade-session.side.ready" : "gui.trade-session.side.not-ready";
        return ItemBuilder.head(ready ? ItemBuilder.HEAD_WOOL_LIME : ItemBuilder.HEAD_WOOL_RED)
                .name(plugin.getMessages().component(nameKey, Map.of("tag", tag, "color", color)))
                .lore(plugin.getMessages().component(loreKey))
                .build();
    }

    private ItemStack addItemButton() {
        return ItemBuilder.head(ItemBuilder.HEAD_CHEST)
                .name(plugin.getMessages().component("gui.trade-session.add-item.name"))
                .lore(plugin.getMessages().component("gui.trade-session.add-item.lore"))
                .build();
    }

    private ItemStack moneyButton(long amount) {
        return ItemBuilder.head(ItemBuilder.HEAD_CHEST_MONEY)
                .name(plugin.getMessages().component("gui.trade-session.money.name"))
                .lore(plugin.getMessages().component("gui.trade-session.money.lore", Map.of("amount", String.valueOf(amount))))
                .build();
    }

    private ItemStack statusItem() {
        return switch (stage) {
            case EDITING -> ItemBuilder.head(ItemBuilder.HEAD_INFO)
                    .name(plugin.getMessages().component("gui.trade-session.status.editing"))
                    .lore(plugin.getMessages().component("gui.trade-session.status.edit-info"))
                    .build();
            case LOCKED -> {
                long remainingMs = Math.max(0, lockedUntilMs - System.currentTimeMillis());
                yield ItemBuilder.head(ItemBuilder.HEAD_INFO)
                        .name(plugin.getMessages().component("gui.trade-session.status.locked"))
                        .lore(plugin.getMessages().component("gui.trade-session.status.locked-countdown",
                            Map.of("time", TimeUtil.formatDuration(remainingMs))))
                        .build();
            }
            case EXECUTING -> ItemBuilder.head(ItemBuilder.HEAD_INFO)
                    .name(plugin.getMessages().component("gui.trade-session.status.executing"))
                    .lore(plugin.getMessages().component("gui.trade-session.status.executing-info"))
                    .build();
            case DELIVERING -> {
                long remainingMs = Math.max(0, deliveryUntilMs - System.currentTimeMillis());
                yield ItemBuilder.head(ItemBuilder.HEAD_INFO)
                        .name(plugin.getMessages().component("gui.trade-session.status.delivering"))
                        .lore(plugin.getMessages().component("gui.trade-session.status.delivery-countdown",
                            Map.of("time", TimeUtil.formatDuration(remainingMs))))
                        .build();
            }
            case COMPLETE -> ItemBuilder.head(ItemBuilder.HEAD_INFO)
                    .name(plugin.getMessages().component("gui.trade-session.status.complete"))
                    .build();
        };
    }

    private boolean isItemSlotOf(int slot, boolean sideA) {
        for (int s : (sideA ? ITEMS_A : ITEMS_B)) {
            if (s == slot) return true;
        }
        return false;
    }

    /** Any change to either side's offer drops the negotiation back to the editable stage. */
    private void revertToEditing() {
        boolean wasLocked = stage == Stage.LOCKED;
        if (wasLocked && lockTask != null) {
            lockTask.cancel();
            lockTask = null;
        }
        stage = Stage.EDITING;
        readyA = false;
        readyB = false;
        render();
        if (wasLocked) notifyBoth("gui.trade-session.modified");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // InventoryClickEvent#getInventory() is slot-aware (returns whichever of top/bottom the
        // clicked slot belongs to), unlike Drag/Close - so unlike those two, this must compare
        // against the top inventory specifically.
        if (!event.getView().getTopInventory().equals(inventory)) return;
        UUID clickerId = event.getWhoClicked().getUniqueId();
        boolean isA = clickerId.equals(playerAId);
        boolean isB = clickerId.equals(playerBId);
        if (!isA && !isB) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        // Nothing in this GUI is drag-and-drop anymore (offers only change via the Add Item
        // picker and the Money prompt), so the player's own inventory is fully locked out too -
        // this also blocks shift-click quick-move from smuggling items into the trade grid.
        event.setCancelled(true);
        if (rawSlot >= inventory.getSize() || resolved || stage == Stage.EXECUTING || stage == Stage.DELIVERING || stage == Stage.COMPLETE) {
            return;
        }

        if (rawSlot == CLOSE_SLOT) {
            abort((Player) event.getWhoClicked());
            return;
        }
        if (rawSlot == READY_A_SLOT) {
            if (isA) toggleReady(true);
            return;
        }
        if (rawSlot == READY_B_SLOT) {
            if (isB) toggleReady(false);
            return;
        }
        if (rawSlot == ADD_ITEM_A_SLOT) {
            if (isA) openPicker(true);
            return;
        }
        if (rawSlot == ADD_ITEM_B_SLOT) {
            if (isB) openPicker(false);
            return;
        }
        if (rawSlot == MONEY_A_SLOT) {
            if (isA) promptMoney(true);
            return;
        }
        if (rawSlot == MONEY_B_SLOT) {
            if (isB) promptMoney(false);
            return;
        }
        if (isItemSlotOf(rawSlot, true)) {
            if (isA) removeItem(true, rawSlot);
            return;
        }
        if (isItemSlotOf(rawSlot, false)) {
            if (isB) removeItem(false, rawSlot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        // Opening the item picker replaces this inventory view for one side only, which fires a
        // close event for THAT player even though the negotiation itself isn't ending.
        if (suppressCloseFor.remove(event.getPlayer().getUniqueId())) return;
        if (resolved) return;
        if (event.getPlayer() instanceof Player player) {
            abort(player);
        }
    }

    private void toggleReady(boolean sideA) {
        if (sideA) readyA = !readyA; else readyB = !readyB;
        if (readyA && readyB) {
            if (stage == Stage.EDITING) {
                stage = Stage.LOCKED;
                readyA = false;
                readyB = false;
                scheduleLockTask();
                render();
                notifyBoth("gui.trade-session.locked");
            } else if (stage == Stage.LOCKED) {
                execute();
            }
        } else {
            render();
        }
    }

    private void openPicker(boolean sideA) {
        UUID clanId = sideA ? clanAId : clanBId;
        UUID playerId = sideA ? playerAId : playerBId;
        plugin.getClanManager().getClanById(clanId).ifPresent(clan -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            suppressCloseFor.add(playerId);
            ClanTradeItemPickerMenu.open(plugin, clan, player, (picker, item) -> onItemPicked(sideA, picker, item));
        });
    }

    private void onItemPicked(boolean sideA, Player picker, ItemStack item) {
        if (resolved) {
            // Session ended while the picker was open - nothing left to add it to.
            returnItemToChest(sideA, item, picker);
            return;
        }
        int[] slots = sideA ? ITEMS_A : ITEMS_B;
        int target = -1;
        for (int slot : slots) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                target = slot;
                break;
            }
        }
        if (target == -1) {
            plugin.getMessages().send(picker, "gui.trade-session.add-item.full");
            returnItemToChest(sideA, item, picker);
            Bukkit.getScheduler().runTask(plugin, () -> reopenFor(picker));
            return;
        }
        inventory.setItem(target, item);
        revertToEditing();
        Bukkit.getScheduler().runTask(plugin, () -> reopenFor(picker));
    }

    private void returnItemToChest(boolean sideA, ItemStack item, Player fallbackPlayer) {
        plugin.getClanManager().getClanById(sideA ? clanAId : clanBId).ifPresentOrElse(clan ->
                        plugin.getClanManager().depositItemsToChestAsync(clan, List.of(item)).thenAccept(leftovers ->
                                plugin.runSync(() -> dropLeftovers(fallbackPlayer, leftovers.toArray(new ItemStack[0])))),
                () -> plugin.runSync(() -> dropLeftovers(fallbackPlayer, new ItemStack[]{item})));
    }

    private void removeItem(boolean sideA, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir()) return;
        ItemStack removed = item.clone();
        inventory.setItem(slot, null);
        UUID clanId = sideA ? clanAId : clanBId;
        Player actor = Bukkit.getPlayer(sideA ? playerAId : playerBId);
        plugin.getClanManager().getClanById(clanId).ifPresentOrElse(clan ->
                        plugin.getClanManager().depositItemsToChestAsync(clan, List.of(removed)).thenAccept(leftovers ->
                                plugin.runSync(() -> dropLeftovers(actor, leftovers.toArray(new ItemStack[0])))),
                () -> plugin.runSync(() -> dropLeftovers(actor, new ItemStack[]{removed})));
        revertToEditing();
    }

    private void promptMoney(boolean sideA) {
        UUID promptedId = sideA ? playerAId : playerBId;
        UUID clanId = sideA ? clanAId : clanBId;
        Player player = Bukkit.getPlayer(promptedId);
        if (player == null) return;
        Clan freshClan = plugin.getClanManager().getClanById(clanId).orElse(null);
        if (freshClan == null) return;
        long escrowed = sideA ? moneyA : moneyB;
        long max = escrowed + freshClan.chestMoney();
        plugin.getMessages().send(player, "gui.trade-session.money.prompt");
        plugin.getMessages().send(player, "gui.trade-session.money.prompt-max", Map.of("amount", String.valueOf(max)));
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
            plugin.runSync(() -> applyMoneyChange(sideA, amount, player));
        });
    }

    private void applyMoneyChange(boolean sideA, long newAmount, Player player) {
        if (resolved) return;
        UUID clanId = sideA ? clanAId : clanBId;
        plugin.getClanManager().getClanById(clanId).ifPresentOrElse(freshClan -> {
            long escrowed = sideA ? moneyA : moneyB;
            long delta = newAmount - escrowed;
            if (delta == 0) return;
            if (delta > 0) {
                if (freshClan.chestMoney() < delta) {
                    plugin.getMessages().send(player, "gui.trade-session.money.invalid");
                    return;
                }
                plugin.getClanManager().removeChestMoneyAsync(freshClan, delta)
                        .thenRun(() -> plugin.runSync(() -> finalizeMoneyChange(sideA, newAmount)))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            } else {
                plugin.getClanManager().depositRewardToChestAsync(freshClan, -delta)
                        .thenRun(() -> plugin.runSync(() -> finalizeMoneyChange(sideA, newAmount)))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            }
        }, () -> plugin.getMessages().send(player, "trade.session.clan-gone"));
    }

    private void finalizeMoneyChange(boolean sideA, long newAmount) {
        if (sideA) moneyA = newAmount; else moneyB = newAmount;
        revertToEditing();
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
        if (resolved || stage != Stage.LOCKED) return;
        if (lockTask != null) lockTask.cancel();
        stage = Stage.EXECUTING;
        render();

        var clanManager = plugin.getClanManager();
        Clan freshA = clanManager.getClanById(clanAId).orElse(null);
        Clan freshB = clanManager.getClanById(clanBId).orElse(null);
        if (freshA == null || freshB == null) {
            resolved = true;
            abort(null);
            notifyBoth("trade.session.clan-gone");
            return;
        }

        List<ItemStack> itemsA = collect(ITEMS_A);
        List<ItemStack> itemsB = collect(ITEMS_B);
        long payoutToB = Math.round(moneyA * (1 - TAX_RATE));
        long payoutToA = Math.round(moneyB * (1 - TAX_RATE));

        // Move to DELIVERING stage and schedule the 10-minute delivery window
        stage = Stage.DELIVERING;
        scheduleDeliveryTask();
        render();
        notifyBoth("gui.trade-session.executing");

        // Deliver asynchronously after the window
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliverTrade(freshA, freshB, itemsA, itemsB, payoutToA, payoutToB),
                20L * 60L * DELIVERY_WINDOW_MINUTES);
    }

    private void deliverTrade(Clan clanA, Clan clanB, List<ItemStack> itemsA, List<ItemStack> itemsB, long payoutA, long payoutB) {
        if (resolved || stage != Stage.DELIVERING) return;

        var clanManager = plugin.getClanManager();
        clanManager.depositRewardToChestAsync(clanB, payoutB)
                .thenCompose(ignored -> clanManager.depositRewardToChestAsync(clanA, payoutA))
                .thenCompose(ignored -> clanManager.depositItemsToChestAsync(clanA, itemsB))
                .thenCompose(leftoverA -> clanManager.depositItemsToChestAsync(clanB, itemsA)
                        .thenApply(leftoverB -> new ItemStack[][]{leftoverA.toArray(new ItemStack[0]), leftoverB.toArray(new ItemStack[0])}))
                .thenAccept(leftovers -> plugin.runSync(() -> finishDelivery(leftovers[0], leftovers[1])))
                .exceptionally(t -> {
                    plugin.runSync(() -> {
                        plugin.getLogger().warning("Clan trade delivery failed: " + t.getMessage());
                        // Delivery failed - items/money stay queued for next delivery attempt
                    });
                    return null;
                });
    }

    private void finishDelivery(ItemStack[] leftoverA, ItemStack[] leftoverB) {
        if (resolved) return;
        resolved = true;
        stage = Stage.COMPLETE;
        for (int slot : ITEMS_A) inventory.setItem(slot, null);
        for (int slot : ITEMS_B) inventory.setItem(slot, null);

        Player playerA = Bukkit.getPlayer(playerAId);
        Player playerB = Bukkit.getPlayer(playerBId);
        dropLeftovers(playerA, leftoverA);
        dropLeftovers(playerB, leftoverB);

        if (playerA != null) {
            plugin.getMessages().send(playerA, "trade.session.delivered", Map.of("tag", clanBTag));
            playerA.closeInventory();
        }
        if (playerB != null) {
            plugin.getMessages().send(playerB, "trade.session.delivered", Map.of("tag", clanATag));
            playerB.closeInventory();
        }
        cleanup();
    }

    private void dropLeftovers(Player player, ItemStack[] leftovers) {
        if (player == null || leftovers == null || leftovers.length == 0) return;
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

    /** Either side backing out (Cancel button, closing the window, disconnecting or timing out) ends the negotiation. */
    public void abort(Player initiator) {
        if (resolved) return;
        resolved = true;
        cancelTasks();

        returnItemsToChest(clanAId, playerAId, ITEMS_A);
        returnItemsToChest(clanBId, playerBId, ITEMS_B);
        refundMoney(clanAId, moneyA);
        refundMoney(clanBId, moneyB);

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

    private void returnItemsToChest(UUID clanId, UUID playerId, int[] slots) {
        List<ItemStack> items = collect(slots);
        for (int slot : slots) inventory.setItem(slot, null);
        if (items.isEmpty()) return;
        Player player = Bukkit.getPlayer(playerId);
        plugin.getClanManager().getClanById(clanId).ifPresentOrElse(clan ->
                        plugin.getClanManager().depositItemsToChestAsync(clan, items).thenAccept(leftovers ->
                                plugin.runSync(() -> dropLeftovers(player, leftovers.toArray(new ItemStack[0])))),
                () -> plugin.runSync(() -> dropLeftovers(player, items.toArray(new ItemStack[0]))));
    }

    private void refundMoney(UUID clanId, long amount) {
        if (amount <= 0) return;
        plugin.getClanManager().getClanById(clanId).ifPresentOrElse(clan ->
                        plugin.getClanManager().depositRewardToChestAsync(clan, amount),
                () -> plugin.getLogger().warning("Could not refund " + amount + " to disbanded clan " + clanId + "'s chest."));
    }

    private void cancelTasks() {
        if (lockTask != null) lockTask.cancel();
        if (deliveryTask != null) deliveryTask.cancel();
        if (statusTask != null) statusTask.cancel();
    }

    private void cleanup() {
        cancelTasks();
        HandlerList.unregisterAll(this);
        sessionManager.unregister(this, clanAId, clanBId);
    }
}
