package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanRecognitionService;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Confirmation of the paid clan recognition. A 9-slot confirm screen exactly like
 * {@link BannerPurchaseConfirmMenu} (gui_gen v2.1, exception 1): glass on the edges, confirm in slot 1, the price
 * in the middle, cancel in slot 7. Without enough coins the screen still opens - the price is visible - but
 * the confirm button is replaced by glass (a frame never has empty slots).
 */
public final class ClanRecognitionConfirmMenu {
    private static final int SIZE = 9;
    private static final int SLOT_CONFIRM = 1;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CANCEL = 7;

    private final LoveClansPlugin plugin;

    public ClanRecognitionConfirmMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** The price shown on the screen is the price that is charged - it is not read from the config again. */
    public static final class Holder extends ClanMenuHolder {
        private final long cost;
        private final boolean affordable;

        Holder(UUID clanId, long cost, boolean affordable) {
            super(ClanMenuType.RECOGNITION_CONFIRM, clanId);
            this.cost = cost;
            this.affordable = affordable;
        }

        long cost() {
            return cost;
        }

        boolean affordable() {
            return affordable;
        }
    }

    public void open(Player player, Clan clan) {
        if (!ClanRecognitionService.isOwner(clan, player.getUniqueId())) {
            plugin.getMessages().send(player, "recognition.owner-only");
            return;
        }
        if (clan.isRecognized()) {
            plugin.getMessages().send(player, "recognition.already");
            return;
        }
        long cost = plugin.getRecognitionService().cost();
        boolean affordable = cost <= 0 || LoveCore.service(LoveEconomy.class).map(eco -> eco.has(player, cost)).orElse(false);

        Holder holder = new Holder(clan.id(), cost, affordable);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component("gui.recognition.title", player));
        holder.setInventory(inventory);

        ItemStack glass = GuiFrames.glassPane();
        for (int slot : new int[]{0, 2, 3, 5, 6, 8}) {
            inventory.setItem(slot, glass);
        }
        inventory.setItem(SLOT_CONFIRM, affordable
                ? ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES).name(plugin.getMessages().component("gui.confirm.yes", player)).build()
                : glass);

        Map<String, String> placeholders = Map.of("cost", CoinFormat.format(cost), "tag", clan.tag(), "color", clan.tagColor());
        List<Component> lore = new ArrayList<>(plugin.getMessages().components("gui.recognition.info-lore", placeholders, player));
        if (!affordable) {
            lore.add(Component.empty());
            lore.add(plugin.getMessages().component("gui.recognition.not-enough", placeholders, player));
        }
        inventory.setItem(SLOT_INFO, ItemBuilder.of(Material.WHITE_BANNER)
                .name(plugin.getMessages().component("gui.recognition.info-name", placeholders, player))
                .lore(lore)
                .build());
        inventory.setItem(SLOT_CANCEL, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                .name(plugin.getMessages().component("gui.confirm.no", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot, Holder holder) {
        if (slot == SLOT_CONFIRM && holder.affordable()) {
            // Close first so a second click cannot confirm twice; the service re-checks everything anyway.
            player.closeInventory();
            plugin.getRecognitionService().recognize(player, holder.cost());
        } else if (slot == SLOT_CANCEL) {
            var clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            if (plugin.getGuiManager().inGuildmasterSession(player) && clan.isPresent()) {
                plugin.getGuiManager().openGuildmaster(player, clan.get());
            } else {
                player.closeInventory();
            }
        }
    }
}
