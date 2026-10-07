package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.stateorder.StateOrderCategory;
import me.lovelace.loveclans.manager.stateorder.StateOrderPicker;
import me.lovelace.loveclans.manager.stateorder.StateOrderSchedule;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.storage.ClanStorage.StateOrderRow;
import me.lovelace.loveclans.util.CoinFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * State orders ("Скупки"): on a schedule (Mon/Wed/Fri at {@code start-hour} by default) the state opens one
 * order of a random category (never the previous one), asking for a few random materials of that category. The
 * volume is shared by the whole server; clans deliver from the inventory and the clan treasury is paid a share of
 * the LoveCore model price per item. All state is owned by the main thread; the database only mirrors it, and the
 * writes are chained so they reach the database in the order they happened.
 */
public final class ServerTradeManager {
    private static final String PATH = "clans.trade.state-orders.";

    /** The live order. Mutated only on the main thread. */
    public static final class StateOrder {
        private final String id;
        private final StateOrderCategory category;
        private final List<Material> materials;
        private final int total;
        private int filled;
        private final long startsAt;
        private final long endsAt;
        private final Map<UUID, Integer> contributions;

        StateOrder(String id, StateOrderCategory category, List<Material> materials, int total, int filled,
                   long startsAt, long endsAt, Map<UUID, Integer> contributions) {
            this.id = id;
            this.category = category;
            this.materials = List.copyOf(materials);
            this.total = total;
            this.filled = filled;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
            this.contributions = new HashMap<>(contributions);
        }

        public String id() { return id; }
        public StateOrderCategory category() { return category; }
        public List<Material> materials() { return materials; }
        public int total() { return total; }
        public int filled() { return filled; }
        public int remaining() { return Math.max(0, total - filled); }
        public long startsAt() { return startsAt; }
        public long endsAt() { return endsAt; }
        public boolean isFull() { return filled >= total; }
        public int contributionOf(UUID clanId) { return contributions.getOrDefault(clanId, 0); }

        StateOrderRow toRow() {
            List<String> names = new ArrayList<>();
            for (Material material : materials) names.add(material.name());
            return new StateOrderRow(id, category.name(), String.join(",", names), total, filled, startsAt, endsAt,
                    Collections.unmodifiableMap(new HashMap<>(contributions)));
        }
    }

    private final LoveClansPlugin plugin;
    private volatile StateOrder current;
    private boolean loaded;
    private CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

    public ServerTradeManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private StateOrderSchedule schedule() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String raw : plugin.getConfig().getStringList(PATH + "days")) {
            try {
                days.add(DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("State orders: unknown weekday '" + raw + "' in " + PATH + "days");
            }
        }
        if (days.isEmpty()) days = StateOrderSchedule.DEFAULT_DAYS;
        return new StateOrderSchedule(days, plugin.getConfig().getInt(PATH + "start-hour", 18));
    }

    private int totalItems() {
        return Math.max(1, plugin.getConfig().getInt(PATH + "total-items", 3000));
    }

    public int perClanCap() {
        return Math.max(0, plugin.getConfig().getInt(PATH + "per-clan-cap", 0));
    }

    private double payoutPercent() {
        return Math.max(0.0, plugin.getConfig().getDouble(PATH + "payout-percent", 40.0));
    }

    private List<Material> pool(StateOrderCategory category) {
        List<Material> materials = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList(PATH + "categories." + category.key())) {
            Material material = Material.matchMaterial(raw.trim());
            if (material != null && material.isItem() && !material.isAir()) {
                materials.add(material);
            } else {
                plugin.getLogger().warning("State orders: unknown item '" + raw + "' in category " + category.key());
            }
        }
        return materials;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Loads the stored order, then checks the schedule every minute on the main thread. */
    public void start() {
        plugin.getStorage().loadLatestStateOrderAsync().whenComplete((row, error) -> plugin.runSync(() -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to load the state order", error);
            } else {
                row.ifPresent(r -> current = fromRow(r));
            }
            loaded = true;
            tick();
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                try {
                    tick();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "State order tick failed", t);
                }
            }, 20L * 60L, 20L * 60L);
        }));
    }

    private StateOrder fromRow(StateOrderRow row) {
        List<Material> materials = new ArrayList<>();
        for (String name : row.materials().split(",")) {
            Material material = Material.matchMaterial(name.trim());
            if (material != null) materials.add(material);
        }
        return new StateOrder(row.id(), StateOrderCategory.parse(row.category(), StateOrderCategory.FOOD), materials,
                row.total(), row.filled(), row.startsAt(), row.endsAt(), row.contributions());
    }

    /** Opens a new order when the latest schedule slot is newer than the current order. */
    void tick() {
        if (!loaded) return;
        StateOrderSchedule schedule = schedule();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime slot = schedule.latestSlotAtOrBefore(now);
        StateOrder order = current;
        if (order != null && order.startsAt() >= slot.toInstant().toEpochMilli()) return;
        openOrder(slot, schedule.nextSlotAfter(slot), order == null ? null : order.category());
    }

    private void openOrder(ZonedDateTime slot, ZonedDateTime end, StateOrderCategory previous) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StateOrderCategory category = StateOrderPicker.pickCategory(previous, random);
        List<Material> pool = pool(category);
        if (pool.isEmpty()) {
            // A misconfigured category must not stall the rotation: fall back to any category that has items.
            for (StateOrderCategory other : StateOrderCategory.values()) {
                if (other != category && !pool(other).isEmpty()) {
                    category = other;
                    pool = pool(other);
                    break;
                }
            }
        }
        int count = Math.max(1, plugin.getConfig().getInt(PATH + "materials-per-order", 4));
        List<Material> materials = StateOrderPicker.pickMaterials(pool, count, random);
        StateOrder order = new StateOrder(UUID.randomUUID().toString(), category, materials, totalItems(), 0,
                slot.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), Map.of());
        current = order;
        StateOrderRow row = order.toRow();
        enqueue(() -> plugin.getStorage().saveStateOrderAsync(row));
        if (!materials.isEmpty()) announce(order);
    }

    private void enqueue(java.util.function.Supplier<CompletableFuture<Void>> write) {
        writeChain = writeChain
                .exceptionally(t -> null)
                .thenCompose(ignored -> write.get())
                .exceptionally(t -> {
                    plugin.getLogger().log(Level.WARNING, "Unable to save the state order", t);
                    return null;
                });
    }

    private void announce(StateOrder order) {
        String category = plugin.getMessages().raw("trade.state.category." + order.category().key());
        Map<String, String> placeholders = Map.of(
                "category", category,
                "items", itemList(order),
                "total", String.valueOf(order.total()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getClanManager().getPlayerClan(online.getUniqueId()).isPresent()) {
                plugin.getMessages().send(online, "trade.state.announce", placeholders);
            }
        }
    }

    private static String itemList(StateOrder order) {
        List<String> names = new ArrayList<>();
        for (Material material : order.materials()) names.add(itemName(material));
        return String.join("<gray>, <white>", names);
    }

    /** MiniMessage for the client-side translated item name. */
    public static String itemName(Material material) {
        return "<lang:" + material.translationKey() + ">";
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** The order players can deliver to right now, or null (none opened yet, or the window has passed). */
    public StateOrder currentOrder() {
        StateOrder order = current;
        if (order == null || System.currentTimeMillis() >= order.endsAt()) return null;
        return order;
    }

    /** Price the treasury gets per item, or empty while the LoveCore price model does not know the item. */
    public OptionalLong unitPayout(Material material) {
        try {
            var oracle = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.PriceOracle.class);
            if (oracle.isPresent() && oracle.get().ready()) {
                OptionalLong unit = oracle.get().value(material);
                if (unit.isPresent() && unit.getAsLong() > 0) {
                    return OptionalLong.of(Math.max(1L, Math.round(unit.getAsLong() * payoutPercent() / 100.0)));
                }
            }
        } catch (Throwable t) {
            // LoveCore price API unavailable
        }
        return OptionalLong.empty();
    }

    /** How many more items this clan may deliver to the order (order remainder and the per-clan cap). */
    public int allowance(StateOrder order, UUID clanId) {
        int allowed = order.remaining();
        int cap = perClanCap();
        if (cap > 0) allowed = Math.min(allowed, Math.max(0, cap - order.contributionOf(clanId)));
        return allowed;
    }

    private static boolean isPlain(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && stack.isSimilar(new ItemStack(material));
    }

    /** Plain items of {@code material} in the player's storage (renamed/enchanted ones are not accepted). */
    public static int countPlain(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) count += stack.getAmount();
        }
        return count;
    }

    private static int firstPlainStack(PlayerInventory inventory, Material material) {
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) return stack.getAmount();
        }
        return 0;
    }

    // ── Delivery ──────────────────────────────────────────────────────────────

    /**
     * Delivers {@code material} from the player's inventory: one stack, or everything ({@code all}) bounded by
     * what the order and the clan cap still accept. Items are taken first, money is credited after.
     *
     * @return true when something was delivered
     */
    public boolean deliver(Player player, Clan clan, Material material, boolean all) {
        if (!clan.hasCapital()) {
            plugin.getMessages().send(player, "clan.no-territory");
            return false;
        }
        if (!clan.isRecognized()) {
            plugin.getMessages().send(player, "trade.server.unrecognized");
            return false;
        }
        StateOrder order = currentOrder();
        if (order == null || !order.materials().contains(material)) {
            plugin.getMessages().send(player, "trade.state.no-order");
            return false;
        }
        if (order.isFull()) {
            plugin.getMessages().send(player, "trade.state.filled");
            return false;
        }
        int allowance = allowance(order, clan.id());
        if (allowance <= 0) {
            plugin.getMessages().send(player, "trade.state.cap-reached", Map.of("cap", String.valueOf(perClanCap())));
            return false;
        }
        OptionalLong unit = unitPayout(material);
        if (unit.isEmpty()) {
            plugin.getMessages().send(player, "trade.state.price-unavailable");
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int offered = all ? countPlain(inventory, material) : firstPlainStack(inventory, material);
        if (offered <= 0) {
            plugin.getMessages().send(player, "trade.state.no-items", Map.of("item", itemName(material)));
            return false;
        }
        int amount = Math.min(offered, allowance);
        Map<Integer, ItemStack> leftover = inventory.removeItem(new ItemStack(material, amount));
        for (ItemStack rest : leftover.values()) amount -= rest.getAmount();
        if (amount <= 0) return false;

        long reward = unit.getAsLong() * amount;
        clan.addChestMoney(reward);
        plugin.getStorage().updateClanChestMoney(clan.id(), clan.chestMoney());

        order.filled += amount;
        int clanTotal = order.contributions.merge(clan.id(), amount, Integer::sum);
        String orderId = order.id();
        int filled = order.filled;
        UUID clanId = clan.id();
        enqueue(() -> plugin.getStorage().updateStateOrderProgressAsync(orderId, filled, clanId, clanTotal));

        plugin.getMessages().send(player, "trade.state.delivered", Map.of(
                "amount", String.valueOf(amount),
                "item", itemName(material),
                "reward", CoinFormat.format(reward),
                "filled", String.valueOf(order.filled()),
                "total", String.valueOf(order.total())));
        if (order.isFull()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getClanManager().getPlayerClan(online.getUniqueId()).isPresent()) {
                    plugin.getMessages().send(online, "trade.state.completed-broadcast");
                }
            }
        }
        return true;
    }
}
