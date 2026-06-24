package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class ClanCreateMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private String pendingName;

    public ClanCreateMenu(LoveClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.create.title", player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        inventory.setItem(13, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(plugin.getMessages().component("gui.create.start.name", player))
                .lore(plugin.getMessages().component("gui.create.start.lore", player))
                .build());

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(int slot) {
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        if (slot == 13) {
            player.closeInventory();
            promptName();
        }
    }

    private void promptName() {
        plugin.getMessages().send(player, "gui.create.prompt-name");
        plugin.expectChatInput(player.getUniqueId(), (name, isCancelled) -> {
            if (isCancelled) return;
            int min = plugin.getConfig().getInt("clans.name.min-length", 4);
            int max = plugin.getConfig().getInt("clans.name.max-length", 10);
            if (name.trim().length() < min || name.trim().length() > max) {
                plugin.getMessages().send(player, "clan.invalid-name",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                return;
            }
            this.pendingName = name.trim();
            promptTag();
        });
    }

    private void promptTag() {
        plugin.getMessages().send(player, "gui.create.prompt-tag");
        plugin.expectChatInput(player.getUniqueId(), (tag, isCancelled) -> {
            if (isCancelled) return;
            int min = plugin.getConfig().getInt("clans.tag.min-length", 3);
            int max = plugin.getConfig().getInt("clans.tag.max-length", 6);
            String pattern = plugin.getConfig().getString("clans.tag.pattern", "^[A-Za-z0-9_]+$");
            if (tag.trim().length() < min || tag.trim().length() > max || !tag.trim().matches(pattern)) {
                plugin.getMessages().send(player, "clan.invalid-tag",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                return;
            }
            plugin.getClanManager().createClanAsync(pendingName, tag.trim(), player.getUniqueId(), true)
                    .thenAccept(created -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "gui.create.success", Map.of("tag", created.tag(), "name", created.name()));
                        plugin.getGuiManager().openMain(player, created);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
