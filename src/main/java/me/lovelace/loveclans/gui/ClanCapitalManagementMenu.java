package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.service.CapitalRelocationService;
import me.lovelace.loveclans.service.GuildShieldService;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capital management (gui_gen v2.1, 27 slots): two buttons in the work row. «Щит» raises the Guild Shield and
 * «Перенос» moves the capital; both are used by clicking the button with the matching item on the cursor, so the item
 * is "inserted" into the button. Nothing is spent unless the checks pass, and the lore of each button always says what
 * is missing.
 */
public class ClanCapitalManagementMenu implements InventoryHolder {
    static final int SLOT_SHIELD = 12;
    static final int SLOT_RELOCATE = 14;
    private static final int SLOT_FILLER = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;

    public ClanCapitalManagementMenu(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.capital.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_CAPITAL)
                .name(plugin.getMessages().component("gui.capital.header.name", player))
                .lore(plugin.getMessages().components("gui.capital.header.lore", Map.of(), player))
                .build());
        refreshButtons();

        // Footer slot next to Close: glass, never empty (gui_gen v2.1). The menu is standalone, so no Back.
        inventory.setItem(SLOT_FILLER, GuiFrames.glassPane());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private void refreshButtons() {
        inventory.setItem(SLOT_SHIELD, shieldItem());
        inventory.setItem(SLOT_RELOCATE, relocateItem());
    }

    private GuildShieldService shields() {
        return plugin.getGuildShieldService();
    }

    private CapitalRelocationService relocation() {
        return plugin.getCapitalRelocationService();
    }

    private ItemStack shieldItem() {
        var messages = plugin.getMessages();
        var mods = plugin.getModifierManager();
        if (mods.hasGuildShield(clan.id())) {
            return ItemBuilder.head(ItemBuilder.HEAD_RELATION_ALLIED)
                    .name(messages.component("gui.capital.shield.name-active", player))
                    .lore(messages.components("gui.capital.shield.lore-active", Map.of(
                            "time", TimeUtil.formatDuration(mods.getGuildShieldRemaining(clan.id()))), player))
                    .glow(true).build();
        }
        Optional<GuildShieldService.Block> block = shields().check(clan, player.getUniqueId());
        ItemBuilder builder = ItemBuilder.head(block.isPresent() ? ItemBuilder.HEAD_INACTIVE : ItemBuilder.HEAD_RELATION_ALLIED)
                .name(block.isPresent() ? LoreBlocks.disabledName(messages, player, "gui.capital.shield.name")
                        : messages.component("gui.capital.shield.name", player))
                .lore(messages.components("gui.capital.shield.lore", Map.of(
                        "hours", String.valueOf(plugin.getConfig().getLong("diplomacy.guild-shield.duration-hours", 24L)),
                        "days", String.valueOf(plugin.getConfig().getLong("diplomacy.guild-shield.cooldown-days", 7L))), player));
        builder.lore(Component.empty());
        LoreBlocks.Hint use = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.capital.shield.action");
        if (block.isPresent()) {
            builder.lore(LoreBlocks.blocked(messages, player, "gui.style.status.unavailable",
                    block.get().key(), block.get().placeholders(), use));
        } else {
            builder.lore(LoreBlocks.clicks(messages, player, List.of(use)));
        }
        return builder.build();
    }

    private ItemStack relocateItem() {
        var messages = plugin.getMessages();
        Optional<CapitalRelocationService.Block> block = relocation().check(clan, player.getUniqueId());
        ItemBuilder builder = ItemBuilder.head(block.isPresent() ? ItemBuilder.HEAD_INACTIVE : ItemBuilder.HEAD_RELOCATE_TERRITORY)
                .name(block.isPresent() ? LoreBlocks.disabledName(messages, player, "gui.capital.relocate.name")
                        : messages.component("gui.capital.relocate.name", player))
                .lore(messages.components("gui.capital.relocate.lore", Map.of(
                        "hours", String.valueOf(plugin.getConfig().getLong("clans.capital.relocate-cooldown-hours", 72L))), player));
        builder.lore(Component.empty());
        LoreBlocks.Hint use = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.capital.relocate.action");
        if (block.isPresent()) {
            builder.lore(LoreBlocks.blocked(messages, player, "gui.style.status.unavailable",
                    block.get().key(), block.get().placeholders(), use));
        } else {
            builder.lore(LoreBlocks.clicks(messages, player, List.of(use)));
        }
        return builder.build();
    }

    public void handleInventoryClick(Player clicker, int slot) {
        switch (slot) {
            case SLOT_CLOSE -> clicker.closeInventory();
            case SLOT_SHIELD -> useShield(clicker);
            case SLOT_RELOCATE -> useSeal(clicker);
            default -> { }
        }
    }

    /**
     * Runs one tick later: the click event is cancelled by the menu router, and the server restores the cursor after a
     * cancelled click, which would undo spending the item right here.
     */
    private void useShield(Player clicker) {
        plugin.runSync(() -> {
            ItemStack cursor = clicker.getItemOnCursor();
            if (!plugin.getItemFactory().isGuildShieldItem(cursor)) {
                plugin.getMessages().send(clicker, "gui.capital.shield.need-item");
                return;
            }
            if (shields().activate(clicker, clan)) {
                cursor.setAmount(cursor.getAmount() - 1);
                clicker.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
                refreshButtons();
            }
        });
    }

    private void useSeal(Player clicker) {
        plugin.runSync(() -> {
            ItemStack cursor = clicker.getItemOnCursor();
            if (!plugin.getItemFactory().isRelocationSeal(cursor)) {
                plugin.getMessages().send(clicker, "gui.capital.relocate.need-item");
                return;
            }
            relocation().relocate(clicker, clan, cursor);
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
