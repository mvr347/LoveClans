package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.upgrade.UpgradeOfferManager;
import me.lovelace.loveclans.manager.upgrade.UpgradeOption;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.upgrade.UpgradeOffer;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One "pick one of two" decision (gui_gen v2.1, 27 slots): the offer head in slot 0, the two big
 * buttons centred in the work row, Back and Close in the footer. The pair comes from the stored offer,
 * so reopening the menu never changes it, and closing it spends nothing. Always shows the oldest
 * pending offer - choices are made in the order the levels were earned.
 */
public final class ClanUpgradeOfferMenu {
    private static final int SIZE = 27;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    public static final class Holder extends ClanMenuHolder {
        private final UUID offerId;
        private final List<UpgradeOption> options;

        Holder(UUID clanId, UUID offerId, List<UpgradeOption> options) {
            super(ClanMenuType.UPGRADE_OFFER, clanId);
            this.offerId = offerId;
            this.options = options;
        }
    }

    private final LoveClansPlugin plugin;

    public ClanUpgradeOfferMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        UpgradeOfferManager manager = plugin.getUpgradeOffers();
        manager.topUp(clan);
        List<UpgradeOffer> pending = manager.pending(clan);
        if (pending.isEmpty()) {
            plugin.getGuiManager().openUpgrades(player, clan);
            return;
        }
        UpgradeOffer offer = pending.get(0);
        List<UpgradeOption> options = new ArrayList<>();
        for (String name : new String[]{offer.option1(), offer.option2()}) {
            if (name == null) continue;
            try {
                options.add(UpgradeOption.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // an option that no longer exists is simply not shown
            }
        }

        Holder holder = new Holder(clan.id(), offer.id(), options);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component(
                "gui.upgrades.offer.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame27(inventory);

        Map<String, String> info = new HashMap<>();
        info.put("index", "1");
        info.put("total", String.valueOf(pending.size()));
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                .name(plugin.getMessages().component("gui.upgrades.offer.info.name", player))
                .lore(plugin.getMessages().components("gui.upgrades.offer.info.lore", info, player))
                .build());

        int[] slots = GuiFrames.workRowSlots(options.size());
        for (int i = 0; i < options.size(); i++) {
            inventory.setItem(slots[i], optionButton(player, clan, options.get(i)));
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack optionButton(Player player, Clan clan, UpgradeOption option) {
        var messages = plugin.getMessages();
        String key = ClanUpgradesMenu.langKey(option);
        String reason = plugin.getUpgradeOffers().unavailableReason(clan, option);
        Map<String, String> values = plugin.getUpgradeOffers().describe(clan, option);

        ItemBuilder builder = ItemBuilder.head(reason == null ? ClanUpgradesMenu.headFor(option) : ItemBuilder.HEAD_INACTIVE)
                .name(reason == null ? messages.component("gui.upgrades.option." + key + ".name", player)
                        : LoreBlocks.disabledName(messages, player, "gui.upgrades.option." + key + ".name"))
                .lore(messages.component("gui.upgrades.option." + key + ".desc", player));
        if (reason == null) {
            builder.lore(Component.empty())
                    .lore(messages.components("gui.upgrades.option." + key + ".change", values, player))
                    .lore(messages.component("gui.upgrades.option.level", values, player))
                    .lore(Component.empty())
                    .lore(messages.components("gui.upgrades.option.choose", player));
        } else {
            builder.lore(messages.component("gui.upgrades.option." + key + ".now", values, player))
                    .lore(Component.empty())
                    .lore(LoreBlocks.blockedReason(messages, player, "gui.upgrades.unavailable." + reason, Map.of(),
                            LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.upgrades.option.action")));
        }
        return builder.build();
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, Holder holder) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openUpgrades(player, clan);
            return;
        }
        int[] slots = GuiFrames.workRowSlots(holder.options.size());
        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            UpgradeOption option = holder.options.get(i);
            String reason = plugin.getUpgradeOffers().unavailableReason(clan, option);
            if (reason != null) {
                plugin.getMessages().sendDeny(player, "gui.upgrades.unavailable." + reason, Map.of());
                return;
            }
            UpgradeOfferManager.Result result = plugin.getUpgradeOffers().choose(clan, player, holder.offerId, option);
            switch (result) {
                case OK -> {
                    plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                    plugin.getMessages().send(player, "gui.upgrades.chosen",
                            Map.of("upgrade", option.upgrade().displayName()));
                    // The next accumulated offer, or the hub when this was the last one.
                    if (plugin.getUpgradeOffers().pendingCount(clan) > 0) {
                        open(player, clan);
                    } else {
                        plugin.getGuiManager().openUpgrades(player, clan);
                    }
                }
                case NO_PERMISSION -> plugin.getMessages().sendDeny(player, "gui.upgrades.error.no-permission", Map.of());
                case NOT_OLDEST -> {
                    plugin.getMessages().sendDeny(player, "gui.upgrades.error.not-oldest", Map.of());
                    open(player, clan);
                }
                case UNAVAILABLE -> plugin.getMessages().sendDeny(player, "gui.upgrades.error.unavailable", Map.of());
                case NO_POINTS -> plugin.getMessages().sendDeny(player, "gui.upgrades.error.no-points", Map.of());
                case RESIZE_REFUSED -> plugin.getMessages().sendDeny(player, "gui.upgrades.error.resize-refused", Map.of());
                case ALREADY_HANDLED, LOCKED -> {
                    // A double click or a second member got there first: nothing is spent, just refresh.
                    plugin.getMessages().send(player, "gui.upgrades.error.already-handled");
                    plugin.getGuiManager().openUpgrades(player, clan);
                }
            }
            return;
        }
    }
}
