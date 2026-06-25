package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.SpiritHistoryEntry;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class ClanSpiritHistoryMenu implements InventoryHolder {
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    private final List<SpiritHistoryEntry> entries;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private Inventory inventory;

    public ClanSpiritHistoryMenu(LoveClansPlugin plugin, Player player, Clan clan, List<SpiritHistoryEntry> entries) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
        this.entries = entries;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 45,
                plugin.getMessages().component("gui.spirit.history-menu.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        int end = Math.min(entries.size(), CONTENT_SLOTS.length);
        for (int index = 0; index < end; index++) {
            SpiritHistoryEntry entry = entries.get(index);
            boolean positive = entry.amount() >= 0;
            inventory.setItem(CONTENT_SLOTS[index], ItemBuilder.of(positive ? Material.LIME_DYE : Material.RED_DYE)
                    .name(plugin.getMessages().component("gui.spirit.history-menu.entry.name",
                            Map.of("action", entry.action()), player))
                    .lore(plugin.getMessages().component("gui.spirit.history-menu.entry.amount",
                            Map.of("amount", (positive ? "+" : "") + entry.amount()), player))
                    .lore(plugin.getMessages().component("gui.spirit.history-menu.entry.date",
                            Map.of("date", dateFormat.format(new Date(entry.timestamp()))), player))
                    .build());
        }

        if (entries.isEmpty()) {
            inventory.setItem(22, ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("gui.spirit.history-menu.empty", player))
                    .build());
        }

        inventory.setItem(40, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == 40) {
            plugin.getGuiManager().openSpiritMenu(player, clan);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
