package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ServerTradeManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Map;

public final class ClanServerTradeMenu implements InventoryHolder {

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    private Inventory inventory;

    public ClanServerTradeMenu(LoveClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.server-trade.title", Map.of("tag", clan.tag()), player));

        GuiFrames.fillFrame27(inventory);

        plugin.getServerTradeManager().checkAndResetWeek(clan);

        boolean recognized = clan.isRecognized();
        int sold = clan.getServerTradeWeeklyStacks();
        int max = ServerTradeManager.MAX_WEEKLY_STACKS;

        // Slot 0: Информация о торговле с сервером
        ItemBuilder infoItem = ItemBuilder.head(recognized ? ItemBuilder.HEAD_CHEST_MONEY : ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component("gui.server-trade.info.name", player));

        if (!recognized) {
            infoItem.lore(plugin.getMessages().components("gui.server-trade.info.unrecognized-lore", player));
        } else {
            infoItem.lore(plugin.getMessages().components("gui.server-trade.info.recognized-lore", Map.of(
                    "sold", String.valueOf(sold),
                    "max", String.valueOf(max),
                    "treasury", String.valueOf(clan.chestMoney())
            ), player));
        }
        inventory.setItem(0, infoItem.build());

        // Slots for current rotating trade offers (10, 12, 14, 16)
        List<ServerTradeManager.TradeOffer> offers = plugin.getServerTradeManager().currentOffers();
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < offers.size() && i < slots.length; i++) {
            ServerTradeManager.TradeOffer offer = offers.get(i);
            int slot = slots[i];

            boolean canSell = recognized && sold < max;
            ItemBuilder offerItem = ItemBuilder.of(offer.material())
                    .name(plugin.getMessages().component("gui.server-trade.offer.name", Map.of("item", offer.displayName()), player))
                    .lore(plugin.getMessages().components(
                            canSell ? "gui.server-trade.offer.lore-active" : "gui.server-trade.offer.lore-disabled",
                            Map.of(
                                    "reward", String.valueOf(offer.rewardMoney()),
                                    "amount", String.valueOf(offer.amount())
                            ), player));

            inventory.setItem(slot, offerItem.build());
        }

        // Slot 26: Close / Back
        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(int slot) {
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        List<ServerTradeManager.TradeOffer> offers = plugin.getServerTradeManager().currentOffers();
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < offers.size() && i < slots.length; i++) {
            if (slots[i] == slot) {
                ServerTradeManager.TradeOffer offer = offers.get(i);
                boolean success = plugin.getServerTradeManager().sellOffer(player, clan, offer);
                if (success) {
                    open(); // обновить GUI
                }
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
