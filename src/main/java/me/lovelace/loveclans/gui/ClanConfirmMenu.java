package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ConfirmMenuLayout;
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
        // The server's 27-slot confirm menu when its background and invisible item exist, otherwise the 9-slot window
        // (gui_gen v2.1 confirm: glass 0,2-6,8, yes at 1, no at 7).
        boolean large = ConfirmMenuLayout.available();
        Inventory inventory = Bukkit.createInventory(holder, large ? ConfirmMenuLayout.SIZE : SIZE,
                large ? ConfirmMenuLayout.title(player) : title);
        holder.setInventory(inventory);
        if (large && openLarge(player, inventory, title, effects)) {
            player.openInventory(inventory);
            return;
        }
        if (large) { // the invisible item vanished between the check and now: fall back to the small window
            holder = new ClanMenuHolder(ClanMenuType.CONFIRM, clan.id());
            inventory = Bukkit.createInventory(holder, SIZE, title);
            holder.setInventory(inventory);
        }

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

    private boolean openLarge(Player player, Inventory inventory, Component question, List<Component> effects) {
        var messages = plugin.getMessages();
        List<Component> yesLore = new java.util.ArrayList<>();
        // The window title is only the background glyph here, so the question moves into the button.
        yesLore.add(question);
        yesLore.add(messages.component("gui.confirm.subtitle", player));
        if (!effects.isEmpty()) {
            yesLore.add(Component.empty());
            yesLore.add(messages.component("gui.confirm.effects-title", player));
            yesLore.addAll(effects);
        }
        yesLore.addAll(LoreBlocks.clicks(messages, player, List.of(
                LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.confirm.yes-action"))));
        var yes = ConfirmMenuLayout.button(messages.component("gui.confirm.yes", player), yesLore);
        List<Component> noLore = new java.util.ArrayList<>(messages.components("gui.confirm.no-lore", player));
        noLore.addAll(LoreBlocks.clicks(messages, player, List.of(
                LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.confirm.no-action"))));
        var no = ConfirmMenuLayout.button(messages.component("gui.confirm.no", player), noLore);
        if (yes == null || no == null) return false;
        ConfirmMenuLayout.fill(inventory, yes, no);
        return true;
    }

    public static boolean isAnswerSlot(int slot, int size) {
        return yes(size, slot) || no(size, slot);
    }

    /** Whether the clicked slot answers "yes" in the window with {@code size} slots (9-slot or the 27-slot menu). */
    private static boolean yes(int size, int slot) {
        return size == ConfirmMenuLayout.SIZE ? ConfirmMenuLayout.isConfirm(slot) : slot == CONFIRM_SLOT;
    }

    private static boolean no(int size, int slot) {
        return size == ConfirmMenuLayout.SIZE ? ConfirmMenuLayout.isCancel(slot) : slot == CANCEL_SLOT;
    }

    public void handleInventoryClick(Player player, int slot, Runnable onYes, Runnable onNo) {
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (yes(size, slot)) {
            player.closeInventory();
            if (onYes != null) onYes.run();
        } else if (no(size, slot)) {
            player.closeInventory();
            if (onNo != null) onNo.run();
        }
    }
}
