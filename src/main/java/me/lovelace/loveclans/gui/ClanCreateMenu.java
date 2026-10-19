package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.textures.HeadTextures;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Map;

public final class ClanCreateMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Player player;
    private Inventory inventory;
    private String name = "";
    private String tag = "";
    private boolean open = true;

    public ClanCreateMenu(LoveClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.create.title", player));

        GuiFrames.fillFrame27(inventory);
        // Slot 0 is the theme head - the header never has an empty slot.
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_INFO).name(plugin.getMessages().component("gui.create.title", player)).build());
        inventory.setItem(25, GuiFrames.glassPane());

        inventory.setItem(10, nameButton(plugin, player, name));
        inventory.setItem(12, tagButton(plugin, player, tag));
        inventory.setItem(14, typeButton(plugin, player, open));
        inventory.setItem(16, createButton(plugin, player, name, tag, "gui.create.create.ready", "gui.create.create.action"));

        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    // The name/tag/type/create buttons are shared with the foundation-banner screen (ClanBannerCreationMenu).

    static ItemStack nameButton(LoveClansPlugin plugin, Player player, String name) {
        String shown = name.isEmpty() ? plugin.getMessages().raw("gui.create.name.not-set") : name;
        return ItemBuilder.head(HeadTextures.HEAD_NAME)
                .name(plugin.getMessages().component("gui.create.name.button", player))
                .lore(plugin.getMessages().components("gui.create.name.lore", Map.of("value", shown,
                        "min", String.valueOf(plugin.getConfig().getInt("clans.name.min-length", 4)),
                        "max", String.valueOf(plugin.getConfig().getInt("clans.name.max-length", 10))), player))
                .build();
    }

    static ItemStack tagButton(LoveClansPlugin plugin, Player player, String tag) {
        String shown = tag.isEmpty() ? plugin.getMessages().raw("gui.create.tag.not-set") : tag;
        return ItemBuilder.head(HeadTextures.HEAD_TAG)
                .name(plugin.getMessages().component("gui.create.tag.button", player))
                .lore(plugin.getMessages().components("gui.create.tag.lore", Map.of("value", shown,
                        "min", String.valueOf(plugin.getConfig().getInt("clans.tag.min-length", 3)),
                        "max", String.valueOf(plugin.getConfig().getInt("clans.tag.max-length", 6))), player))
                .build();
    }

    static ItemStack typeButton(LoveClansPlugin plugin, Player player, boolean open) {
        return ItemBuilder.head(open ? HeadTextures.HEAD_OPEN : HeadTextures.HEAD_CLOSED)
                .name(plugin.getMessages().component(open ? "gui.create.type.open" : "gui.create.type.closed", player))
                .lore(plugin.getMessages().components(open ? "gui.create.type.lore-open" : "gui.create.type.lore-closed", player))
                .build();
    }

    /** The final button: a summary of the entered data when ready, a blocked button with the reason otherwise. */
    static ItemStack createButton(LoveClansPlugin plugin, Player player, String name, String tag, String readyNameKey, String actionKey) {
        var messages = plugin.getMessages();
        LoreBlocks.Hint action = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, actionKey);
        if (!name.isEmpty() && !tag.isEmpty()) {
            return ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                    .name(messages.component(readyNameKey, player))
                    .lore(messages.components("gui.create.create.ready-lore", Map.of("name", name, "tag", tag), player))
                    .lore(LoreBlocks.clicks(messages, player, List.of(action)))
                    .build();
        }
        return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                .name(messages.component("gui.create.create.not-ready", player))
                .lore(LoreBlocks.blockedReason(messages, player, "gui.create.create.not-ready-reason", Map.of(), action))
                .build();
    }

    public void handleInventoryClick(int slot) {
        switch (slot) {
            case 10 -> promptName();
            case 12 -> promptTag();
            case 14 -> {
                open = !open;
                plugin.getSoundService().playGui(player, GuiSounds.Kind.TOGGLE);
                open();
            }
            case 16 -> tryCreate();
            case 26 -> player.closeInventory();
            default -> {}
        }
    }

    private void promptName() {
        player.closeInventory();
        plugin.getMessages().send(player, "gui.create.name.prompt");
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
        if (name.isEmpty() || tag.isEmpty()) {
            plugin.getSoundService().playGui(player, GuiSounds.Kind.DENIED);
            return;
        }
        player.closeInventory();
        plugin.getClanManager().createClanAsync(name, tag, player.getUniqueId(), open)
                .thenAccept(created -> plugin.runSync(() -> {
                    plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                    plugin.getMessages().send(player, "gui.create.success", Map.of("tag", created.tag(), "name", created.name(), "color", created.tagColor()));
                    plugin.getGuiManager().openMain(player, created);
                }))
                .exceptionally(t -> {
                    plugin.runSync(() -> {
                        // The menu is closed while the clan is being created, so the generic error sound stays silent
                        plugin.getSoundService().playGui(player, GuiSounds.Kind.ERROR);
                        plugin.sendOperationError(player, t);
                        open();
                    });
                    return null;
                });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
