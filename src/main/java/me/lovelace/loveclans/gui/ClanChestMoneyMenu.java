package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/** Money side of the clan chest (§2.3): balance display plus deposit/withdraw chat prompts. */
public final class ClanChestMoneyMenu {
    private static final int BALANCE_SLOT = 4;
    private static final int DEPOSIT_SLOT = 11;
    private static final int WITHDRAW_SLOT = 15;
    private static final int BACK_SLOT = 25;
    private static final int CLOSE_SLOT = 26;

    private final LoveClansPlugin plugin;

    public ClanChestMoneyMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.CHEST_MONEY, clan.id()), 27,
                plugin.getMessages().component("gui.chest-money-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        inventory.setItem(BALANCE_SLOT, ItemBuilder.of(Material.GOLD_INGOT)
                .name(plugin.getMessages().component("gui.chest.money.balance.name", player))
                .lore(plugin.getMessages().component("gui.chest.money.balance.lore", Map.of("amount", String.valueOf(clan.chestMoney())), player))
                .build());

        inventory.setItem(DEPOSIT_SLOT, ItemBuilder.of(Material.LIME_DYE)
                .name(plugin.getMessages().component("gui.chest.money.deposit.name", player))
                .lore(plugin.getMessages().component("gui.chest.money.deposit.lore", player))
                .build());

        inventory.setItem(WITHDRAW_SLOT, ItemBuilder.of(Material.RED_DYE)
                .name(plugin.getMessages().component("gui.chest.money.withdraw.name", player))
                .lore(plugin.getMessages().component("gui.chest.money.withdraw.lore", player))
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
            plugin.getGuiManager().openChestHub(player, clan);
        } else if (slot == DEPOSIT_SLOT) {
            promptAmount(player, clan, "gui.chest.money.deposit.prompt", true);
        } else if (slot == WITHDRAW_SLOT) {
            promptAmount(player, clan, "gui.chest.money.withdraw.prompt", false);
        }
    }

    private void promptAmount(Player player, Clan clan, String promptKey, boolean deposit) {
        player.closeInventory();
        plugin.getMessages().send(player, promptKey);
        plugin.expectChatInput(player.getUniqueId(), (input, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(() -> open(player, clan));
                return;
            }
            long amount;
            try {
                amount = Long.parseLong(input.trim());
            } catch (NumberFormatException exception) {
                plugin.getMessages().send(player, "chest.invalid-amount");
                plugin.runSync(() -> open(player, clan));
                return;
            }
            var future = deposit
                    ? plugin.getClanManager().depositChestMoneyAsync(clan, player.getUniqueId(), player, amount)
                            .thenAccept(balance -> plugin.getMessages().send(player, "chest.deposit-success",
                                    Map.of("amount", String.valueOf(amount), "balance", String.valueOf(balance))))
                    : plugin.getClanManager().withdrawChestMoneyAsync(clan, player.getUniqueId(), player, amount)
                            .thenRun(() -> plugin.getMessages().send(player, "chest.withdraw-success", Map.of("amount", String.valueOf(amount))));
            future.thenRun(() -> plugin.runSync(() ->
                            plugin.getClanManager().getClanById(clan.id()).ifPresent(updated -> open(player, updated))))
                    .exceptionally(t -> {
                        plugin.runSync(() -> {
                            plugin.sendOperationError(player, t);
                            open(player, clan);
                        });
                        return null;
                    });
        });
    }
}
