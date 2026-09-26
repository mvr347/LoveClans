package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Подтверждение покупки Знамени основания клана у Плотника (NPC), см.
 * {@link me.lovelace.loveclans.listener.ClanBannerListener#onNpcInteract}. 9-слотовое GUI по
 * стандарту gui_gen v2.1, Исключение 1 (hopper-подтверждение): стекло по краям, подтвердить в
 * слоте 1, инфо о цене по центру, отменить в слоте 7. Не переиспользует {@link ClanConfirmMenu}/
 * {@link me.lovelace.loveclans.manager.GuiManager}, потому что та плоскость целиком завязана на
 * уже существующий {@code Clan} (см. {@code ClanMenuHolder#clanId()}), а до покупки знамени у
 * игрока клана ещё нет.
 */
public final class BannerPurchaseConfirmMenu {

    private static final int SLOT_CONFIRM = 1;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CANCEL = 7;
    private static final int SIZE = 9;

    private BannerPurchaseConfirmMenu() {}

    public static final class Holder implements InventoryHolder {
        private final long cost;
        private Inventory inventory;

        private Holder(long cost) {
            this.cost = cost;
        }

        /** Цена, зафиксированная на момент открытия меню - подтверждение списывает именно её, а не перечитывает конфиг заново. */
        public long cost() {
            return cost;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(Player player, LoveClansPlugin plugin, long cost) {
        Holder holder = new Holder(cost);
        Component title = plugin.getMessages().component("clan.banner.confirm-title", player);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack glass = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build();
        for (int slot : new int[]{0, 2, 3, 5, 6, 8}) {
            inventory.setItem(slot, glass);
        }

        Map<String, String> placeholders = Map.of("cost", String.valueOf(cost));
        ItemStack info = ItemBuilder.of(Material.WHITE_BANNER)
                .name(plugin.getMessages().component("clan.banner.confirm-item-name", placeholders, player))
                .lore(plugin.getMessages().components("clan.banner.confirm-item-lore", placeholders, player))
                .build();
        inventory.setItem(SLOT_INFO, info);

        inventory.setItem(SLOT_CONFIRM, ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                .name(plugin.getMessages().component("gui.confirm.yes", player))
                .build());
        inventory.setItem(SLOT_CANCEL, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                .name(plugin.getMessages().component("gui.confirm.no", player))
                .build());

        player.openInventory(inventory);
    }

    public static boolean isConfirmSlot(int slot) {
        return slot == SLOT_CONFIRM;
    }

    public static boolean isCancelSlot(int slot) {
        return slot == SLOT_CANCEL;
    }
}
