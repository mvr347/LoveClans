package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Клановый сундук (§2) — точка входа: показывает статус налога и открывает деньги/предметы.
 * Физическое хранилище (ClanChestMenu) и деньги (ClanChestMoneyMenu) — отдельные подменю.
 */
public final class ClanChestHubMenu {
    private static final int INFO_SLOT = 4;
    private static final int MONEY_SLOT = 11;
    private static final int ITEMS_SLOT = 15;
    private static final int TRADE_SLOT = 13;
    private static final int BACK_SLOT = 25;
    private static final int CLOSE_SLOT = 26;

    private final LoveClansPlugin plugin;

    public ClanChestHubMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.CHEST_HUB, clan.id()), 27,
                plugin.getMessages().component("gui.chest-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        boolean taxApplicable = plugin.getClanManager().isTaxApplicable(clan);
        boolean locked = clan.isChestTaxLocked();
        int unlockLevel = plugin.getConfig().getInt("clans.chest.tax.tax-free-until-level", 3);

        ItemBuilder info = ItemBuilder.of(locked ? Material.BARRIER : Material.CHEST)
                .name(plugin.getMessages().component("gui.chest.info.name", player))
                .lore(plugin.getMessages().components("gui.chest.info.lore", Map.of(
                        "money", String.valueOf(clan.chestMoney()),
                        "rows", String.valueOf(clan.chestRows()),
                        "max", String.valueOf(plugin.getClanManager().maxChestRows())
                ), player));
        if (!taxApplicable) {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-none", Map.of("level", String.valueOf(unlockLevel)), player));
        } else if (locked) {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-locked", player));
        } else {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-ok", player));
            long nextTaxAt = clan.lastTaxAt() + java.time.Duration.ofDays(7).toMillis();
            long remaining = nextTaxAt - System.currentTimeMillis();
            info.lore(plugin.getMessages().component("gui.chest.info.next-tax",
                    Map.of("time", TimeUtil.formatDuration(Math.max(0, remaining))), player));
        }
        inventory.setItem(INFO_SLOT, info.build());

        inventory.setItem(MONEY_SLOT, ItemBuilder.of(Material.GOLD_INGOT)
                .name(plugin.getMessages().component("gui.chest.money-button.name", player))
                .lore(plugin.getMessages().component("gui.chest.money-button.lore", Map.of("amount", String.valueOf(clan.chestMoney())), player))
                .build());

        if (locked) {
            inventory.setItem(ITEMS_SLOT, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.chest.locked-warning.name", player))
                    .lore(plugin.getMessages().components("gui.chest.locked-warning.lore", Map.of(), player))
                    .build());
        } else {
            inventory.setItem(ITEMS_SLOT, ItemBuilder.of(Material.CHEST)
                    .name(plugin.getMessages().component("gui.chest.items-button.name", player))
                    .lore(plugin.getMessages().component("gui.chest.items-button.lore", player))
                    .build());
        }

        // Полноценный выбор клана для торговли (§6.3) — в отдельной задаче по переделке UI
        // дипломатии/торговли; пока кнопка лишь подсказывает команду.
        inventory.setItem(TRADE_SLOT, ItemBuilder.of(Material.EMERALD)
                .name(plugin.getMessages().component("gui.chest.trade-button.name", player))
                .lore(plugin.getMessages().component("gui.chest.trade-button.lore", player))
                .build());

        inventory.setItem(BACK_SLOT, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == BACK_SLOT) {
            plugin.getGuiManager().openMain(player, clan);
        } else if (slot == MONEY_SLOT) {
            plugin.getGuiManager().openChestMoney(player, clan);
        } else if (slot == TRADE_SLOT) {
            player.closeInventory();
            plugin.getMessages().send(player, "gui.chest.trade-button.hint");
        } else if (slot == ITEMS_SLOT) {
            if (clan.isChestTaxLocked()) {
                plugin.getMessages().send(player, "chest.tax-locked");
                return;
            }
            player.closeInventory();
            ClanChestMenu.open(plugin, clan, player);
        }
    }
}
