package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanRecognitionService;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Guildmaster NPC's menu (gui_gen v2.1, 27 slots). Only members who may see something besides the clan
 * list get it: vows need {@link ClanPermission#CONTRACTS}, recognition is for the owner of a not yet recognized
 * clan. Everybody else is taken straight to the clan list by {@code GuildmasterListener}. The buttons are
 * header controls, centered by {@link GuiFrames#controlSlots}; a button that does not apply is simply not
 * there and the glass stays. Which button sits where is remembered in the holder, so a click is judged by
 * what the player was actually shown, not by what the layout would be a moment later.
 */
public final class GuildmasterMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_FOOTER_FILL = 25;
    private static final int SLOT_CLOSE = 26;

    enum Action { VOWS, RECOGNITION, CLAN_LIST }

    public static final class Holder extends ClanMenuHolder {
        private final Map<Integer, Action> actions;

        Holder(UUID clanId, Map<Integer, Action> actions) {
            super(ClanMenuType.GUILDMASTER, clanId);
            this.actions = actions;
        }

        Action actionAt(int slot) {
            return actions.get(slot);
        }
    }

    private final LoveClansPlugin plugin;
    private final ClanRecognitionConfirmMenu recognitionMenu;

    public GuildmasterMenu(LoveClansPlugin plugin, ClanRecognitionConfirmMenu recognitionMenu) {
        this.plugin = plugin;
        this.recognitionMenu = recognitionMenu;
    }

    /** True when the player gets this menu rather than the plain clan list. */
    public static boolean hasMenu(Clan clan, UUID playerId) {
        return clan.hasPermission(playerId, ClanPermission.CONTRACTS)
                || (ClanRecognitionService.isOwner(clan, playerId) && !clan.isRecognized());
    }

    private List<Action> buttonsFor(Clan clan, UUID playerId) {
        List<Action> buttons = new ArrayList<>();
        if (clan.hasPermission(playerId, ClanPermission.CONTRACTS)) {
            buttons.add(Action.VOWS);
        }
        if (ClanRecognitionService.isOwner(clan, playerId) && !clan.isRecognized()) {
            buttons.add(Action.RECOGNITION);
        }
        buttons.add(Action.CLAN_LIST);
        return buttons;
    }

    public void open(Player player, Clan clan) {
        List<Action> buttons = buttonsFor(clan, player.getUniqueId());
        int[] slots = GuiFrames.controlSlots(buttons.size());
        Map<Integer, Action> layout = new LinkedHashMap<>();
        for (int i = 0; i < buttons.size(); i++) {
            layout.put(slots[i], buttons.get(i));
        }

        Holder holder = new Holder(clan.id(), layout);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                plugin.getMessages().component("gui.guildmaster.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.guildmaster.info.title", player))
                .lore(plugin.getMessages().components("gui.guildmaster.info.lore", player))
                .build());
        layout.forEach((slot, action) -> inventory.setItem(slot, buildButton(action, clan, player)));

        // The footer is one full row: glass next to the close button, never an empty slot.
        inventory.setItem(SLOT_FOOTER_FILL, GuiFrames.glassPane());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildButton(Action action, Clan clan, Player player) {
        return switch (action) {
            case VOWS -> ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                    .name(plugin.getMessages().component("gui.guildmaster.button.vows.name", player))
                    .lore(plugin.getMessages().components("gui.guildmaster.button.vows.lore", player))
                    .build();
            case RECOGNITION -> ItemBuilder.head(ItemBuilder.HEAD_LEVEL_INFO)
                    .name(plugin.getMessages().component("gui.guildmaster.button.recognition.name", player))
                    .lore(plugin.getMessages().components("gui.guildmaster.button.recognition.lore",
                            Map.of("cost", CoinFormat.format(plugin.getRecognitionService().cost())), player))
                    .build();
            case CLAN_LIST -> ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                    .name(plugin.getMessages().component("gui.guildmaster.button.clans.name", player))
                    .lore(plugin.getMessages().components("gui.guildmaster.button.clans.lore", player))
                    .build();
        };
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, Holder holder) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        Action action = holder.actionAt(slot);
        if (action == null) return;
        switch (action) {
            case VOWS -> {
                if (!clan.hasPermission(player.getUniqueId(), ClanPermission.CONTRACTS)) {
                    plugin.getMessages().send(player, "general.no-permission");
                    return;
                }
                plugin.getGuiManager().openContracts(player, clan);
            }
            // open() re-checks owner / not yet recognized
            case RECOGNITION -> recognitionMenu.open(player, clan);
            case CLAN_LIST -> plugin.getGuiManager().openClanList(player);
        }
    }
}
