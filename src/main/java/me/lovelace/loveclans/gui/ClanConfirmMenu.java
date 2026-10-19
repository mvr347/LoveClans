package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

/** Yes/no confirmation, 9 slots (gui_gen v2.1, exception 1): confirm in slot 1, cancel in slot 7, glass elsewhere. */
public final class ClanConfirmMenu {
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;
    private static final int SIZE = 9;

    private final LoveClansPlugin plugin;

    public ClanConfirmMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The consequence lines of one confirm kind ({@code gui.confirm.<kind>.effects}) with their placeholders filled in;
     * empty when the lang file has no such list. Callers hand the result to {@link #open}.
     */
    public static List<Component> effects(LoveClansPlugin plugin, Player player, String kind, Map<String, String> placeholders) {
        String key = "gui.confirm." + kind + ".effects";
        return plugin.getMessages().has(key) ? plugin.getMessages().components(key, placeholders, player) : List.of();
    }

    /** Single-line variant kept for callers that pass one ready component (an empty one means "no consequences"). */
    public void open(Player player, Clan clan, Component title, Component lore) {
        open(player, clan, title, lore == null || lore.equals(Component.empty()) ? List.<Component>of() : List.of(lore));
    }

    public void open(Player player, Clan clan, Component title, List<Component> effects) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.CONFIRM, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, SIZE, title); // gui_gen v2.1 confirm: glass 0,2-6,8 (the middle is a plain separator), yes at 1, no at 7
        holder.setInventory(inventory);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        var messages = plugin.getMessages();
        ItemBuilder yes = ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                .name(messages.component("gui.confirm.yes", player))
                .lore(messages.component("gui.confirm.subtitle", player));
        if (!effects.isEmpty()) {
            yes.lore(Component.empty()).lore(messages.component("gui.confirm.effects-title", player)).lore(effects);
        }
        yes.lore(LoreBlocks.clicks(messages, player, List.of(
                LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.confirm.yes-action"))));
        inventory.setItem(CONFIRM_SLOT, yes.build());

        inventory.setItem(CANCEL_SLOT, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                .name(messages.component("gui.confirm.no", player))
                .lore(messages.components("gui.confirm.no-lore", player))
                .lore(LoreBlocks.clicks(messages, player, List.of(
                        LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.confirm.no-action"))))
                .build());

        player.openInventory(inventory);
    }

    public static boolean isAnswerSlot(int slot) {
        return slot == CONFIRM_SLOT || slot == CANCEL_SLOT;
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
