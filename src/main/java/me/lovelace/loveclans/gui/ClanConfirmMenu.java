package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public final class ClanConfirmMenu {
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 3;

    private final LoveClansPlugin plugin;

    public ClanConfirmMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan, Component title, Component lore) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.CONFIRM, clan.id()), InventoryType.HOPPER, title);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        ItemBuilder yes = ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                .name(plugin.getMessages().component("gui.confirm.yes", player));
        if (lore != null && !lore.equals(Component.empty())) {
            yes.lore(lore);
        }
        inventory.setItem(CONFIRM_SLOT, yes.build());

        inventory.setItem(CANCEL_SLOT, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                .name(plugin.getMessages().component("gui.confirm.no", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot, Runnable onYes, Runnable onNo) {
        if (slot == CONFIRM_SLOT) {
            player.closeInventory();
            if (onYes != null) onYes.run();
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            if (onNo != null) onNo.run();
        }
    }
}
