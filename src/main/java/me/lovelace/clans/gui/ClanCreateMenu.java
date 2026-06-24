package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class ClanCreateMenu implements InventoryHolder {
    private final ClansPlugin plugin;
    private final Player player;
    private String name = "";
    private String tag = "";
    private boolean open = true;

    public ClanCreateMenu(ClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        Inventory inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.create.title", player));

        fillGlass(inventory);

        // Name button
        String nameDisplay = name.isEmpty()
                ? plugin.getMessages().raw("gui.create.name.not-set")
                : name;
        inventory.setItem(10, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTY3ZDgxM2FlN2ZmZTViZTk1MWE0ZjQxZjJhYTYxOWE1ZTM4OTRlODVlYTVkNDk4NmY4NDk0OWM2M2Q3NjcyZSJ9fX0=")
                .name(plugin.getMessages().component("gui.create.name.button", player))
                .lore(plugin.getMessages().component("gui.create.name.lore",
                        Map.of("value", nameDisplay), player))
                .build());

        // Tag button
        String tagDisplay = tag.isEmpty()
                ? plugin.getMessages().raw("gui.create.tag.not-set")
                : tag;
        inventory.setItem(12, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFiYzJiY2ZiMmJkMzc1OWU2YjFlODZmYzdhNzk1ODVlMTEyN2RkMzU3ZmMyMDI4OTNmOWRlMjQxYmM5ZTUzMCJ9fX0=")
                .name(plugin.getMessages().component("gui.create.tag.button", player))
                .lore(plugin.getMessages().component("gui.create.tag.lore",
                        Map.of("value", tagDisplay), player))
                .build());

        // Open/Closed toggle
        inventory.setItem(14, ItemBuilder.head(open ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQ4YmI0ZTQ0MzVjMmMyMWQ3ZjYxODNiMzhhMmI3MzcyNjUzZjM1NDBiZTAyMjU5ZGQ0N2JmNTI0OTJkZTY2OSJ9fX0=" : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmJmNDZiZjM5ZGZjNzE4ZTdlYTMxZGI0MzQ3N2ZjNmI3ZGNhNTg4ZmUwYTc4OTFkNDgxYzVkZGE5ZTE2ZjUyMCJ9fX0=")
                .name(plugin.getMessages().component(
                        open ? "gui.create.type.open" : "gui.create.type.closed", player))
                .lore(plugin.getMessages().components("gui.create.type.lore", player))
                .build());

        // Create button
        boolean ready = !name.isEmpty() && !tag.isEmpty();
        inventory.setItem(16, ItemBuilder.head(ready ? ItemBuilder.HEAD_DELETE_YES : ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component(
                        ready ? "gui.create.create.ready" : "gui.create.create.not-ready", player))
                .build());

        // Close
        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        return inventory;
    }

    public void open() {
        player.openInventory(getInventory());
    }

    public void handleInventoryClick(int slot) {
        switch (slot) {
            case 10 -> promptName();
            case 12 -> promptTag();
            case 14 -> { open = !open; open(); }
            case 16 -> tryCreate();
            case 22 -> player.closeInventory();
        }
    }

    private void promptName() {
        player.closeInventory();
        plugin.getMessages().send(player, "gui.create.name.prompt");
        // Отправляем подсказку об отмене
        plugin.getMessages().send(player, "general.chat-input-cancel");
        plugin.expectChatInput(player.getUniqueId(), (input, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(this::open);
                return;
            }
            String trimmed = input.trim();
            int min = plugin.getConfig().getInt("clans.name.min-length", 4);
            int max = plugin.getConfig().getInt("clans.name.max-length", 10);
            if (trimmed.length() < min || trimmed.length() > max) {
                plugin.getMessages().send(player, "clan.invalid-name",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                plugin.runSync(this::open);
                return;
            }
            this.name = trimmed;
            plugin.runSync(this::open);
        });
    }

    private void promptTag() {
        player.closeInventory();
        plugin.getMessages().send(player, "gui.create.tag.prompt");
        // Отправляем подсказку об отмене
        plugin.getMessages().send(player, "general.chat-input-cancel");
        plugin.expectChatInput(player.getUniqueId(), (input, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(this::open);
                return;
            }
            String trimmed = input.trim();
            int min = plugin.getConfig().getInt("clans.tag.min-length", 3);
            int max = plugin.getConfig().getInt("clans.tag.max-length", 6);
            String pattern = plugin.getConfig().getString("clans.tag.pattern", "^[A-Za-z0-9_]+$");
            if (trimmed.length() < min || trimmed.length() > max || !trimmed.matches(pattern)) {
                plugin.getMessages().send(player, "clan.invalid-tag",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                plugin.runSync(this::open);
                return;
            }
            this.tag = trimmed;
            plugin.runSync(this::open);
        });
    }

    private void tryCreate() {
        if (name.isEmpty() || tag.isEmpty()) return;
        player.closeInventory();
        plugin.getClanManager().createClanAsync(name, tag, player.getUniqueId(), open)
                .thenAccept(clan -> plugin.runSync(() ->
                        plugin.getMessages().send(player, "clan.created", Map.of("tag", clan.tag()))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> {
                        plugin.sendOperationError(player, throwable);
                        open(); // reopen on error
                    });
                    return null;
                });
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
