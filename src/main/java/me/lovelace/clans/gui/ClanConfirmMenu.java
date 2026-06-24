package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class ClanConfirmMenu {
    private final ClansPlugin plugin;

    public ClanConfirmMenu(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    // Modified signature to accept Component for title and lore
    public void open(Player player, Clan clan, Component title, Component lore) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.CONFIRM, clan.id()), 27,
                title); // Use the Component title directly

        fillGlass(inventory);

        inventory.setItem(11, ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                .name(plugin.getMessages().component("gui.confirm.yes", player))
                .lore(lore) // Add the lore component
                .build());

        inventory.setItem(15, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                .name(plugin.getMessages().component("gui.confirm.no", player))
                .lore(lore) // Add the lore component
                .build());

        player.openInventory(inventory);
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}