package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.trade.ClanTrade;
import me.lovelace.loveclans.util.InventorySerialization;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view of an incoming trade offer (§4.2/§6.3) with Accept/Decline buttons. Offered
 * items are shown as plain display copies - all clicks in this menu are cancelled globally by
 * GuiManager (see ClanMenuHolder dispatch), so nothing here is actually draggable.
 */
public final class ClanTradeReviewMenu {
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_MONEY = 22;
    private static final int SLOT_ACCEPT = 29;
    private static final int SLOT_DECLINE = 33;
    private static final int SLOT_CLOSE = 35;

    private final LoveClansPlugin plugin;
    private final Map<UUID, UUID> openReviews = new ConcurrentHashMap<>();

    public ClanTradeReviewMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, UUID tradeId) {
        ClanTrade trade = plugin.getClanTradeManager().pendingTrade(tradeId).orElse(null);
        if (trade == null) {
            plugin.getMessages().send(player, "trade.not-found");
            return;
        }
        var viewerClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (viewerClanOpt.isEmpty() || !viewerClanOpt.get().id().equals(trade.toClanId())) {
            plugin.getMessages().send(player, "trade.not-found");
            return;
        }
        Clan fromClan = plugin.getClanManager().getClanById(trade.fromClanId()).orElse(null);
        if (fromClan == null) {
            plugin.getMessages().send(player, "trade.not-found");
            return;
        }

        openReviews.put(player.getUniqueId(), tradeId);

        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.TRADE_REVIEW, fromClan.id()), 36,
                plugin.getMessages().component("gui.trade-review.title", Map.of("tag", fromClan.tag(), "color", fromClan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        ItemStack[] items = InventorySerialization.deserialize(trade.items(), me.lovelace.loveclans.manager.ClanManager.CHEST_MAX_SIZE);
        int slotIndex = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || slotIndex >= ITEM_SLOTS.length) continue;
            inventory.setItem(ITEM_SLOTS[slotIndex], item.clone());
            slotIndex++;
        }
        if (slotIndex == 0) {
            inventory.setItem(ITEM_SLOTS[0], ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("gui.trade-review.no-items", player))
                    .build());
        }

        inventory.setItem(SLOT_MONEY, ItemBuilder.of(Material.SUNFLOWER)
                .name(plugin.getMessages().component("gui.trade-review.money.name", player))
                .lore(plugin.getMessages().component("gui.trade-review.money.lore", Map.of("amount", String.valueOf(trade.money())), player))
                .build());

        inventory.setItem(SLOT_ACCEPT, ItemBuilder.head(ItemBuilder.HEAD_COMPLETED_QUESTS)
                .name(plugin.getMessages().component("gui.trade-review.accept.name", player))
                .lore(plugin.getMessages().component("gui.trade-review.accept.lore", player))
                .build());
        inventory.setItem(SLOT_DECLINE, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component("gui.trade-review.decline.name", player))
                .lore(plugin.getMessages().component("gui.trade-review.decline.lore", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan fromClan, int slot) {
        UUID tradeId = openReviews.get(player.getUniqueId());
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (tradeId == null) {
            return;
        }
        var viewerClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (viewerClanOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Clan viewerClan = viewerClanOpt.get();

        if (slot == SLOT_ACCEPT) {
            plugin.getClanTradeManager().acceptTradeAsync(tradeId, viewerClan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(player::closeInventory))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        } else if (slot == SLOT_DECLINE) {
            plugin.getClanTradeManager().declineTradeAsync(tradeId, viewerClan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(player::closeInventory))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        }
    }

    public void clearPlayer(UUID playerId) {
        openReviews.remove(playerId);
    }
}
