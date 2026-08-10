package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.trade.ClanTrade;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists this clan's pending trade invites (§4.2): ones it sent (with a cancel button) and ones
 * it received (with accept/decline), plus a shortcut to reopen a live negotiation session if one
 * is already running and the representative closed the window by accident. Previously the only
 * way to cancel a sent invite was the click-to-cancel chat message at send time - if that scrolled
 * out of chat there was no other way to back out of it.
 */
public final class ClanTradeRequestsMenu {
    private static final int SESSION_SLOT = 18;
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int BACK_SLOT = 52;
    private static final int CLOSE_SLOT = 53;

    private final LoveClansPlugin plugin;

    public ClanTradeRequestsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.TRADE_REQUESTS, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.getMessages().component("gui.trade-requests.title", player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        plugin.getClanTradeSessionManager().activeSessionForClan(clan.id()).ifPresent(session ->
                inventory.setItem(SESSION_SLOT, ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                        .name(plugin.getMessages().component("gui.trade-requests.active-session.name", player))
                        .lore(plugin.getMessages().component("gui.trade-requests.active-session.lore", player))
                        .glow(true)
                        .build()));

        List<ClanTrade> trades = new ArrayList<>(plugin.getClanTradeManager().pendingTradesFor(clan.id()));

        if (trades.isEmpty()) {
            inventory.setItem(CONTENT_SLOTS[CONTENT_SLOTS.length / 2], ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.trade-requests.empty", player))
                    .build());
        } else {
            for (int i = 0; i < trades.size() && i < CONTENT_SLOTS.length; i++) {
                ClanTrade trade = trades.get(i);
                boolean sent = trade.fromClanId().equals(clan.id());
                Clan counterpart = plugin.getClanManager().getClanById(sent ? trade.toClanId() : trade.fromClanId()).orElse(null);
                String tag = counterpart != null ? counterpart.tag() : "?";
                String color = counterpart != null ? counterpart.tagColor() : "<gray>";
                var builder = ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                        .name(plugin.getMessages().component(sent ? "gui.trade-requests.sent.name" : "gui.trade-requests.received.name",
                                Map.of("tag", tag, "color", color), player))
                        .lore(plugin.getMessages().component(sent ? "gui.trade-requests.sent.lore" : "gui.trade-requests.received.lore", player));
                inventory.setItem(CONTENT_SLOTS[i], builder.build());
            }
        }

        inventory.setItem(BACK_SLOT, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan) {
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            plugin.getGuiManager().openChestHub(player, clan);
            return;
        }
        if (slot == SESSION_SLOT) {
            if (!plugin.getClanTradeSessionManager().reopenFor(player)) {
                plugin.getMessages().send(player, "trade.session.reopen-unavailable");
            }
            return;
        }

        int index = -1;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) {
                index = i;
                break;
            }
        }
        if (index < 0) return;

        List<ClanTrade> trades = new ArrayList<>(plugin.getClanTradeManager().pendingTradesFor(clan.id()));
        if (index >= trades.size()) return;
        ClanTrade trade = trades.get(index);
        boolean sent = trade.fromClanId().equals(clan.id());

        if (sent) {
            plugin.getClanTradeManager().cancelTradeAsync(trade.id(), clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "trade.cancelled-confirm");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }

        if (event.isRightClick()) {
            plugin.getClanTradeManager().declineTradeAsync(trade.id(), clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "trade.declined-confirm");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        } else {
            player.closeInventory();
            plugin.getClanTradeManager().acceptTradeAsync(trade.id(), clan, player.getUniqueId())
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        }
    }
}
