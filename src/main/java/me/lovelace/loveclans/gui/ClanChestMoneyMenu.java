package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import net.kyori.adventure.text.Component;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.CoinStacks;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import me.lovelace.loveclans.util.TreasuryCoins;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Казна (§2): the clan's money shown as real coin stacks that members move with the mouse.
 *
 * <p>Layout (gui_gen v2.1, 54 slots): slot 0 = balance/tax head, 1-17 glass, work zone 19-25, 28-34,
 * 37-43 = the balance split greedily into coin stacks ({@link TreasuryCoins}), side walls empty,
 * footer 45-51 glass, 52 Back, 53 Close.</p>
 *
 * <p>Deposit (BANK permission): click the work zone with coins on the cursor, or shift-click coins in your own
 * inventory. Withdraw (BANK permission, treasury not tax-locked): click a stack to take it on the cursor, shift-click
 * to move it into the inventory. Everything runs synchronously on the main thread in the order
 * "take the coins, then credit" / "debit, then give", so no click sequence can duplicate money.
 * Every change re-renders all open treasury views of that clan.</p>
 */
public final class ClanChestMoneyMenu implements Listener {
    static final int SIZE = 54;
    static final int INFO_SLOT = 0;
    static final int BACK_SLOT = 52;
    static final int CLOSE_SLOT = 53;
    /** Work zone of a 54-slot gui_gen menu below the two header rows, without the side walls. */
    static final int[] COIN_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int DEFAULT_MAX_STACK = 64;

    private final LoveClansPlugin plugin;

    public ClanChestMoneyMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Holder marking a treasury view; remembers which stack is drawn in which slot. */
    static final class TreasuryHolder implements InventoryHolder {
        private final UUID clanId;
        private Inventory inventory;
        private TreasuryCoins.Pile[] piles = new TreasuryCoins.Pile[SIZE];

        TreasuryHolder(UUID clanId) {
            this.clanId = clanId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, Clan clan) {
        TreasuryHolder holder = new TreasuryHolder(clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                plugin.getMessages().component("gui.chest-money-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.inventory = inventory;
        render(holder, player, clan);
        player.openInventory(inventory);
    }

    private static Optional<LoveEconomy> economy() {
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private void render(TreasuryHolder holder, Player viewer, Clan clan) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.piles = new TreasuryCoins.Pile[SIZE];
        for (int slot = 1; slot <= 17; slot++) inventory.setItem(slot, GuiFrames.glassPane());
        for (int slot = 45; slot <= 51; slot++) inventory.setItem(slot, GuiFrames.glassPane());
        inventory.setItem(BACK_SLOT, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", viewer)).build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", viewer)).build());

        long shown = 0;
        LoveEconomy economy = economy().orElse(null);
        if (economy != null) {
            List<TreasuryCoins.Pile> piles = TreasuryCoins.breakdown(clan.chestMoney(), economy.denominations(),
                    DEFAULT_MAX_STACK, COIN_SLOTS.length);
            for (int i = 0; i < piles.size(); i++) {
                TreasuryCoins.Pile pile = piles.get(i);
                Optional<ItemStack> stack = CoinStacks.of(economy, pile.denomination(), pile.count());
                if (stack.isEmpty()) {
                    continue; // old LoveCore or unknown item: the balance is still shown on the info head
                }
                ItemStack coins = stack.get();
                // The stack may be clamped below the requested count by the item's max stack size.
                TreasuryCoins.Pile actual = new TreasuryCoins.Pile(pile.denomination(), coins.getAmount());
                holder.piles[COIN_SLOTS[i]] = actual;
                shown += actual.value();
                inventory.setItem(COIN_SLOTS[i], coins);
            }
        }

        ItemBuilder info = ItemBuilder.head(clan.isChestTaxLocked() ? ItemBuilder.HEAD_CHEST_LOCKED : ItemBuilder.HEAD_CHEST_MONEY)
                .name(plugin.getMessages().component("gui.chest.money.balance.name", viewer))
                .lore(plugin.getMessages().component("gui.chest.money.balance.description", viewer))
                .lore(plugin.getMessages().component("gui.chest.money.balance.lore",
                        Map.of("amount", CoinFormat.format(clan.chestMoney())), viewer));
        long hidden = clan.chestMoney() - shown;
        if (hidden > 0) {
            info.lore(plugin.getMessages().component("gui.chest.money.balance.hidden",
                    Map.of("amount", CoinFormat.format(hidden)), viewer));
        }
        if (!plugin.getClanManager().isTaxApplicable(clan)) {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-none", viewer));
        } else if (clan.isChestTaxLocked()) {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-locked", viewer));
        } else {
            info.lore(plugin.getMessages().component("gui.chest.info.tax-ok", viewer));
            long remaining = clan.lastTaxAt() + java.time.Duration.ofDays(7).toMillis() - System.currentTimeMillis();
            info.lore(plugin.getMessages().component("gui.chest.info.next-tax",
                    Map.of("time", TimeUtil.formatDuration(Math.max(0, remaining))), viewer));
        }
        info.lore(net.kyori.adventure.text.Component.empty());
        if (clan.hasPermission(viewer.getUniqueId(), ClanPermission.BANK)) {
            info.lore(plugin.getMessages().component("gui.chest.money.balance.deposit-hint", viewer));
            info.lore(plugin.getMessages().component("gui.chest.money.balance.withdraw-hint", viewer));
        }
        inventory.setItem(INFO_SLOT, info.build());
    }

    /** Redraws every open treasury of {@code clan} so all viewers see the same stacks as the balance. */
    private void refreshAll(Clan clan) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof TreasuryHolder holder
                    && holder.clanId.equals(clan.id())) {
                render(holder, online, clan);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TreasuryHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TreasuryHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw >= SIZE) {
            // Own inventory: normal clicks are fine, shift-click is the "put these coins in" gesture.
            if (event.isShiftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                if (event.isShiftClick()) {
                    withClan(holder, player, clan -> depositFromSlot(event, player, clan));
                }
            }
            return;
        }
        event.setCancelled(true);
        if (raw == CLOSE_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (raw == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getClanManager().getClanById(holder.clanId)
                    .ifPresent(clan -> plugin.getGuiManager().openMain(player, clan)));
            return;
        }
        withClan(holder, player, clan -> {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                depositCursor(player, clan, cursor);
                return;
            }
            TreasuryCoins.Pile pile = holder.piles[raw];
            if (pile != null) {
                withdraw(player, clan, pile, event.isShiftClick());
            }
        });
    }

    private void withClan(TreasuryHolder holder, Player player, java.util.function.Consumer<Clan> action) {
        Clan clan = plugin.getClanManager().getClanById(holder.clanId).orElse(null);
        if (clan == null || clan.member(player.getUniqueId()).isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        action.accept(clan);
    }

    private void depositCursor(Player player, Clan clan, ItemStack cursor) {
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.BANK)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        LoveEconomy economy = economy().orElse(null);
        if (economy == null) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        long unit = economy.valueOf(cursor);
        if (unit <= 0) {
            plugin.getMessages().send(player, "chest.not-coin");
            return;
        }
        long amount = unit * cursor.getAmount();
        player.setItemOnCursor(null); // take the coins first...
        credit(player, clan, amount); // ...then credit
    }

    private void depositFromSlot(InventoryClickEvent event, Player player, Clan clan) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || event.getClickedInventory() == null) {
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.BANK)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        LoveEconomy economy = economy().orElse(null);
        if (economy == null) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        long unit = economy.valueOf(item);
        if (unit <= 0) {
            plugin.getMessages().send(player, "chest.not-coin");
            return;
        }
        long amount = unit * item.getAmount();
        event.getClickedInventory().setItem(event.getSlot(), null);
        credit(player, clan, amount);
    }

    private void credit(Player player, Clan clan, long amount) {
        long balance = plugin.getClanManager().depositTreasuryCoins(clan, amount);
        plugin.getMessages().send(player, "chest.deposit-success",
                Map.of("amount", CoinFormat.format(amount), "balance", CoinFormat.format(balance)));
        refreshAll(clan);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private void withdraw(Player player, Clan clan, TreasuryCoins.Pile pile, boolean toInventory) {
        LoveEconomy economy = economy().orElse(null);
        if (economy == null) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        Optional<ItemStack> stack = CoinStacks.of(economy, pile.denomination(), pile.count());
        long amount = stack.map(s -> pile.denomination().value() * s.getAmount()).orElse(pile.value());
        long threshold = plugin.getConfig().getLong("economy.large-withdraw-confirm-threshold", 500L);
        if (threshold > 0 && amount >= threshold) {
            plugin.getGuiManager().openConfirm(player, clan,
                    plugin.getMessages().component("gui.confirm.withdraw.title", Map.of("amount", CoinFormat.format(amount)), player),
                    Component.empty(),
                    () -> performWithdraw(player, clan, pile, true),
                    () -> plugin.runSync(() -> open(player, clan))
            );
            return;
        }
        performWithdraw(player, clan, pile, toInventory);
    }

    private void performWithdraw(Player player, Clan clan, TreasuryCoins.Pile pile, boolean toInventory) {
        LoveEconomy economy = economy().orElse(null);
        if (economy == null) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        Optional<ItemStack> stack = CoinStacks.of(economy, pile.denomination(), pile.count());
        long amount = stack.map(s -> pile.denomination().value() * s.getAmount()).orElse(pile.value());
        if ((toInventory || stack.isEmpty()) && !economy.canFit(player, amount)) {
            plugin.getMessages().send(player, "chest.no-space");
            return;
        }
        String refusal = plugin.getClanManager().withdrawTreasuryCoins(clan, player.getUniqueId(), amount);
        if (refusal != null) {
            plugin.getMessages().send(player, refusal);
            return;
        }
        // Debited above - only now hand the coins out.
        if (stack.isEmpty()) {
            economy.give(player, amount); // LoveCore without coinStack: plain payout
        } else if (toInventory) {
            for (ItemStack extra : player.getInventory().addItem(stack.get()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        } else {
            player.setItemOnCursor(stack.get());
        }
        plugin.getMessages().send(player, "chest.withdraw-success", Map.of("amount", CoinFormat.format(amount)));
        refreshAll(clan);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }
}
