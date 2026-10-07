package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ServerTradeManager;
import me.lovelace.loveclans.manager.ServerTradeManager.StateOrder;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.trade.ClanTrade;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Торговля" (gui_gen v2.1, 54 slots): one screen with three header tabs - other clans (LMB proposes a trade),
 * pending trade requests (accept / decline / cancel) and the state orders. The first two tabs add a filter and a
 * sort switch (5 controls: 2,3,4 tabs, 6 filter, 7 sort); the state tab has only the 3 tabs (3,4,5). Pagination
 * 36/44, footer: 51 active trade session (glass when none), 52 back, 53 close. The holder keeps the slot -> entry
 * map of what was actually shown, so a click is judged by what the player saw; every action is re-checked by the
 * managers.
 */
public final class ClanTradeMenu {
    public enum Tab { CLANS, REQUESTS, STATE }

    enum ClanFilter { ALL, OPEN, PEACE, UNRESTRICTED }

    enum ClanSort {
        INFLUENCE(Comparator.comparingLong(Clan::influence).reversed()),
        MEMBERS(Comparator.comparingInt((Clan c) -> c.members().size()).reversed()),
        NAME(Comparator.comparing((Clan c) -> c.name().toLowerCase(Locale.ROOT)));

        final Comparator<Clan> comparator;

        ClanSort(Comparator<Clan> comparator) {
            this.comparator = comparator;
        }
    }

    enum RequestFilter { ALL, INCOMING, OUTGOING }

    enum RequestSort { NEWEST, OLDEST }

    private record State(Tab tab, ClanFilter clanFilter, ClanSort clanSort,
                         RequestFilter requestFilter, RequestSort requestSort, int page) {
        static State initial() {
            return new State(Tab.CLANS, ClanFilter.ALL, ClanSort.INFLUENCE, RequestFilter.ALL, RequestSort.NEWEST, 0);
        }

        State withTab(Tab next) {
            return new State(next, clanFilter, clanSort, requestFilter, requestSort, 0);
        }

        State withPage(int next) {
            return new State(tab, clanFilter, clanSort, requestFilter, requestSort, next);
        }
    }

    /** What a content slot holds: another clan, a pending request or a state-order material. */
    private record Entry(UUID clanId, UUID tradeId, Material material) {}

    public static final class Holder extends ClanMenuHolder {
        private final Map<Integer, Entry> entries;

        Holder(UUID clanId, Map<Integer, Entry> entries) {
            super(ClanMenuType.TRADE, clanId);
            this.entries = entries;
        }

        Entry entryAt(int slot) {
            return entries.get(slot);
        }
    }

    private static final int SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int[] TAB_SLOTS_WITH_CONTROLS = {2, 3, 4};
    private static final int[] TAB_SLOTS_ALONE = {3, 4, 5};
    private static final int SLOT_FILTER = 6;
    private static final int SLOT_SORT = 7;
    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_SESSION = 51;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LoveClansPlugin plugin;
    private final Map<UUID, State> stateByPlayer = new ConcurrentHashMap<>();

    public ClanTradeMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearPlayer(UUID playerId) {
        stateByPlayer.remove(playerId);
    }

    public void open(Player player, Clan clan, Tab tab) {
        State previous = stateByPlayer.getOrDefault(player.getUniqueId(), State.initial());
        render(player, clan, tab == null ? previous : previous.withTab(tab));
    }

    public void open(Player player, Clan clan) {
        open(player, clan, null);
    }

    private static int[] tabSlots(Tab tab) {
        return tab == Tab.STATE ? TAB_SLOTS_ALONE : TAB_SLOTS_WITH_CONTROLS;
    }

    private void render(Player player, Clan clan, State state) {
        Map<Integer, Entry> slotMap = new HashMap<>();
        Holder holder = new Holder(clan.id(), slotMap);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component("gui.trade.title", player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame54(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                .name(plugin.getMessages().component("gui.trade.info.name", player))
                .lore(plugin.getMessages().component("gui.trade.info.description", player))
                .lore(plugin.getMessages().component("gui.trade.info.treasury",
                        Map.of("amount", CoinFormat.format(clan.chestMoney())), player))
                .build());

        int[] tabSlots = tabSlots(state.tab());
        for (Tab tab : Tab.values()) {
            inventory.setItem(tabSlots[tab.ordinal()], tabButton(tab, tab == state.tab(), player));
        }

        int page = switch (state.tab()) {
            case CLANS -> renderClans(inventory, slotMap, player, clan, state);
            case REQUESTS -> renderRequests(inventory, slotMap, player, clan, state);
            case STATE -> renderState(inventory, slotMap, player, clan);
        };
        stateByPlayer.put(player.getUniqueId(), state.withPage(page));

        plugin.getClanTradeSessionManager().activeSessionForClan(clan.id()).ifPresent(session ->
                inventory.setItem(SLOT_SESSION, ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                        .name(plugin.getMessages().component("gui.trade.active-session.name", player))
                        .lore(plugin.getMessages().component("gui.trade.active-session.lore", player))
                        .glow(true)
                        .build()));
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());

        player.openInventory(inventory);
    }

    private ItemStack tabButton(Tab tab, boolean selected, Player player) {
        String key = tab.name().toLowerCase(Locale.ROOT);
        String texture = switch (tab) {
            case CLANS -> ItemBuilder.HEAD_DIPLOMACY;
            case REQUESTS -> ItemBuilder.HEAD_LETTERS;
            case STATE -> ItemBuilder.HEAD_CHEST_MONEY;
        };
        return ItemBuilder.head(texture)
                .name(plugin.getMessages().component("gui.trade.tab." + key + ".name", player))
                .lore(plugin.getMessages().component("gui.trade.tab." + key + ".description", player))
                .lore(net.kyori.adventure.text.Component.empty())
                .lore(plugin.getMessages().component(selected ? "gui.trade.tab.selected" : "gui.trade.tab.open", player))
                .glow(selected)
                .build();
    }

    /** Places the page navigation and returns the clamped page. */
    private int paginate(Inventory inventory, Player player, int requestedPage, int size) {
        int pages = Math.max(1, (size + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }
        return page;
    }

    private void empty(Inventory inventory, Player player, String key) {
        inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                .name(plugin.getMessages().component(key + ".name", player))
                .lore(plugin.getMessages().component(key + ".lore", player))
                .build());
    }

    // ── Clans tab ─────────────────────────────────────────────────────────────

    /** Lang key of the reason trading with {@code target} is blocked, or null when it is allowed. */
    private String blockReason(Clan source, Clan target) {
        if (plugin.getClanManager().inConflictWith(source.id(), target.id())) return "gui.trade.clan.reason.war";
        if (plugin.getDiplomacyManager().isEmbargoed(source.id(), target.id())) return "gui.trade.clan.reason.embargo";
        if (plugin.getDiplomacyManager().isBlockading(source.id(), target.id())
                || plugin.getDiplomacyManager().isBlockading(target.id(), source.id())) return "gui.trade.clan.reason.blockade";
        if (!target.hasCapital()) return "gui.trade.clan.reason.no-territory";
        return null;
    }

    private boolean matches(ClanFilter filter, Clan source, Clan target) {
        return switch (filter) {
            case ALL -> true;
            case OPEN -> target.isOpen();
            case PEACE -> !plugin.getClanManager().inConflictWith(source.id(), target.id());
            case UNRESTRICTED -> blockReason(source, target) == null;
        };
    }

    private int renderClans(Inventory inventory, Map<Integer, Entry> slotMap, Player player, Clan clan, State state) {
        inventory.setItem(SLOT_FILTER, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.trade.filter-title", "gui.trade.clan.filter.", ClanFilter.values(), state.clanFilter()));
        inventory.setItem(SLOT_SORT, CycleButton.build(plugin, player, ItemBuilder.HEAD_SORT,
                "gui.trade.sort-title", "gui.trade.clan.sort.", ClanSort.values(), state.clanSort()));

        List<Clan> clans = plugin.getClanManager().getAllClans().stream()
                .filter(other -> !other.id().equals(clan.id()))
                .filter(other -> matches(state.clanFilter(), clan, other))
                .sorted(state.clanSort().comparator)
                .toList();
        int page = paginate(inventory, player, state.page(), clans.size());
        if (clans.isEmpty()) {
            empty(inventory, player, "gui.trade.clan.empty");
            return page;
        }
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && start + i < clans.size(); i++) {
            Clan target = clans.get(start + i);
            slotMap.put(CONTENT_SLOTS[i], new Entry(target.id(), null, null));
            inventory.setItem(CONTENT_SLOTS[i], clanItem(clan, target, player));
        }
        return page;
    }

    private ItemStack clanItem(Clan source, Clan target, Player player) {
        String reason = blockReason(source, target);
        Map<String, String> names = Map.of("tag", target.tag(), "color", target.tagColor(), "name", target.name());
        ItemBuilder builder = reason == null
                ? ItemBuilder.of(Material.PLAYER_HEAD)
                : ItemBuilder.head(ItemBuilder.HEAD_INACTIVE);
        builder.name(plugin.getMessages().component("gui.trade.clan.name", names, player))
                .lore(plugin.getMessages().component("gui.trade.clan.relation",
                        Map.of("relation", plugin.getMessages().relationName(source.relationTo(target.id()))), player))
                .lore(plugin.getMessages().component("gui.trade.clan.members",
                        Map.of("count", String.valueOf(target.members().size())), player))
                .lore(plugin.getMessages().component("gui.trade.clan.influence",
                        Map.of("influence", String.valueOf(target.influence())), player))
                .lore(net.kyori.adventure.text.Component.empty());
        if (reason != null) {
            builder.lore(plugin.getMessages().component("gui.trade.clan.blocked",
                    Map.of("reason", plugin.getMessages().raw(reason)), player));
        } else {
            builder.lore(plugin.getMessages().component("gui.trade.clan.hint", player));
            target.leaderId().ifPresent(leaderId -> {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
                builder.mutate(meta -> {
                    if (meta instanceof SkullMeta skull) skull.setOwningPlayer(leader);
                });
            });
        }
        return builder.build();
    }

    // ── Requests tab ──────────────────────────────────────────────────────────

    private List<ClanTrade> requests(Clan clan, State state) {
        Comparator<ClanTrade> byDate = Comparator.comparingLong(ClanTrade::createdAt);
        return plugin.getClanTradeManager().pendingTradesFor(clan.id()).stream()
                .filter(trade -> switch (state.requestFilter()) {
                    case ALL -> true;
                    case INCOMING -> trade.toClanId().equals(clan.id());
                    case OUTGOING -> trade.fromClanId().equals(clan.id());
                })
                .sorted(state.requestSort() == RequestSort.NEWEST ? byDate.reversed() : byDate)
                .toList();
    }

    private int renderRequests(Inventory inventory, Map<Integer, Entry> slotMap, Player player, Clan clan, State state) {
        inventory.setItem(SLOT_FILTER, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.trade.filter-title", "gui.trade.request.filter.", RequestFilter.values(), state.requestFilter()));
        inventory.setItem(SLOT_SORT, CycleButton.build(plugin, player, ItemBuilder.HEAD_SORT,
                "gui.trade.sort-title", "gui.trade.request.sort.", RequestSort.values(), state.requestSort()));

        List<ClanTrade> trades = requests(clan, state);
        int page = paginate(inventory, player, state.page(), trades.size());
        if (trades.isEmpty()) {
            empty(inventory, player, "gui.trade.request.empty");
            return page;
        }
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && start + i < trades.size(); i++) {
            ClanTrade trade = trades.get(start + i);
            boolean sent = trade.fromClanId().equals(clan.id());
            Clan counterpart = plugin.getClanManager().getClanById(sent ? trade.toClanId() : trade.fromClanId()).orElse(null);
            Map<String, String> names = Map.of(
                    "tag", counterpart != null ? counterpart.tag() : "?",
                    "color", counterpart != null ? counterpart.tagColor() : "<gray>");
            String kind = sent ? "sent" : "received";
            ItemStack item = ItemBuilder.head(sent ? ItemBuilder.HEAD_LETTER_SENT : ItemBuilder.HEAD_LETTER_UNREAD)
                    .name(plugin.getMessages().component("gui.trade.request." + kind + ".name", names, player))
                    .lore(plugin.getMessages().component("gui.trade.request.date",
                            Map.of("date", plugin.getMessages().formatDate(trade.createdAt())), player))
                    .lore(net.kyori.adventure.text.Component.empty())
                    .lore(plugin.getMessages().components("gui.trade.request." + kind + ".lore", player))
                    .build();
            slotMap.put(CONTENT_SLOTS[i], new Entry(null, trade.id(), null));
            inventory.setItem(CONTENT_SLOTS[i], item);
        }
        return page;
    }

    // ── State orders tab ──────────────────────────────────────────────────────

    /** Material slots centered on the middle work row; more than 4 fill the work zone in order. */
    static int[] materialSlots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{31};
            case 2 -> new int[]{30, 32};
            case 3 -> new int[]{29, 31, 33};
            case 4 -> new int[]{28, 30, 32, 34};
            default -> java.util.Arrays.copyOfRange(CONTENT_SLOTS, 0, Math.min(count, CONTENT_SLOTS.length));
        };
    }

    private int renderState(Inventory inventory, Map<Integer, Entry> slotMap, Player player, Clan clan) {
        ServerTradeManager manager = plugin.getServerTradeManager();
        StateOrder order = manager.currentOrder();
        if (!clan.isRecognized()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.trade.state.unrecognized.name", player))
                    .lore(plugin.getMessages().component("gui.trade.state.unrecognized.lore", player))
                    .build());
            return 0;
        }
        if (order == null || order.materials().isEmpty()) {
            empty(inventory, player, "gui.trade.state.none");
            return 0;
        }
        String category = plugin.getMessages().raw("trade.state.category." + order.category().key());
        long left = Math.max(0, order.endsAt() - System.currentTimeMillis());
        int cap = manager.perClanCap();
        int allowance = manager.allowance(order, clan.id());
        // The order summary rides on the theme head of the tab (slot 0), like the treasury/chest heads do.
        ItemBuilder info = ItemBuilder.head(ItemBuilder.HEAD_CHEST_MONEY)
                .name(plugin.getMessages().component("gui.trade.state.info.name", Map.of("category", category), player))
                .lore(plugin.getMessages().component("gui.trade.state.info.description", player))
                .lore(plugin.getMessages().component("gui.trade.state.info.progress",
                        Map.of("filled", String.valueOf(order.filled()), "total", String.valueOf(order.total())), player))
                .lore(plugin.getMessages().component("gui.trade.state.info.clan",
                        Map.of("amount", String.valueOf(order.contributionOf(clan.id()))), player));
        if (cap > 0) {
            info.lore(plugin.getMessages().component("gui.trade.state.info.cap", Map.of("cap", String.valueOf(cap)), player));
        }
        info.lore(plugin.getMessages().component(order.isFull() ? "gui.trade.state.info.full" : "gui.trade.state.info.ends",
                Map.of("time", TimeUtil.formatDuration(left)), player));
        inventory.setItem(SLOT_INFO, info.build());

        List<Material> materials = order.materials();
        int[] slots = materialSlots(materials.size());
        for (int i = 0; i < slots.length; i++) {
            Material material = materials.get(i);
            OptionalLong unit = manager.unitPayout(material);
            int have = ServerTradeManager.countPlain(player.getInventory(), material);
            ItemBuilder builder = ItemBuilder.of(material)
                    .name(plugin.getMessages().component("gui.trade.state.item.name",
                            Map.of("item", ServerTradeManager.itemName(material)), player))
                    .lore(plugin.getMessages().component(unit.isPresent() ? "gui.trade.state.item.price" : "gui.trade.state.item.no-price",
                            Map.of("price", unit.isPresent() ? CoinFormat.format(unit.getAsLong()) : ""), player))
                    .lore(plugin.getMessages().component("gui.trade.state.item.have", Map.of("amount", String.valueOf(have)), player))
                    .lore(net.kyori.adventure.text.Component.empty());
            if (order.isFull() || allowance <= 0) {
                builder.lore(plugin.getMessages().component("gui.trade.state.item.closed", player));
            } else {
                builder.lore(plugin.getMessages().component("gui.trade.state.item.hint-stack", player))
                        .lore(plugin.getMessages().component("gui.trade.state.item.hint-all", player));
            }
            slotMap.put(slots[i], new Entry(null, null, material));
            inventory.setItem(slots[i], builder.build());
        }
        return 0;
    }

    // ── Clicks ────────────────────────────────────────────────────────────────

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan, Holder holder) {
        int slot = event.getRawSlot();
        State state = stateByPlayer.getOrDefault(player.getUniqueId(), State.initial());
        boolean forward = !event.isRightClick();

        int[] tabSlots = tabSlots(state.tab());
        for (Tab tab : Tab.values()) {
            if (tabSlots[tab.ordinal()] == slot) {
                if (tab != state.tab()) render(player, clan, state.withTab(tab));
                return;
            }
        }

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_BACK -> plugin.getGuiManager().openMain(player, clan);
            case SLOT_SESSION -> {
                if (plugin.getClanTradeSessionManager().activeSessionForClan(clan.id()).isEmpty()) return;
                if (!plugin.getClanTradeSessionManager().reopenFor(player)) {
                    plugin.getMessages().send(player, "trade.session.reopen-unavailable");
                }
            }
            case SLOT_PREV -> render(player, clan, state.withPage(state.page() - 1));
            case SLOT_NEXT -> render(player, clan, state.withPage(state.page() + 1));
            case SLOT_FILTER -> {
                if (state.tab() == Tab.CLANS) {
                    render(player, clan, new State(state.tab(), CycleButton.step(state.clanFilter(), ClanFilter.values(), forward),
                            state.clanSort(), state.requestFilter(), state.requestSort(), 0));
                } else if (state.tab() == Tab.REQUESTS) {
                    render(player, clan, new State(state.tab(), state.clanFilter(), state.clanSort(),
                            CycleButton.step(state.requestFilter(), RequestFilter.values(), forward), state.requestSort(), 0));
                }
            }
            case SLOT_SORT -> {
                if (state.tab() == Tab.CLANS) {
                    render(player, clan, new State(state.tab(), state.clanFilter(),
                            CycleButton.step(state.clanSort(), ClanSort.values(), forward), state.requestFilter(), state.requestSort(), 0));
                } else if (state.tab() == Tab.REQUESTS) {
                    render(player, clan, new State(state.tab(), state.clanFilter(), state.clanSort(), state.requestFilter(),
                            CycleButton.step(state.requestSort(), RequestSort.values(), forward), 0));
                }
            }
            default -> {
                Entry entry = holder.entryAt(slot);
                if (entry == null) return;
                if (entry.clanId() != null) handleClan(player, clan, entry.clanId());
                else if (entry.tradeId() != null) handleRequest(event, player, clan, entry.tradeId());
                else if (entry.material() != null) handleDelivery(event, player, clan, entry.material(), state);
            }
        }
    }

    private void handleClan(Player player, Clan clan, UUID targetId) {
        Clan target = plugin.getClanManager().getClanById(targetId).orElse(null);
        if (target == null) return;
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.TRADE)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        if (plugin.getClanTradeManager().tradeBlocked(clan.id(), target.id())) {
            plugin.getMessages().send(player, "trade.blocked");
            return;
        }
        player.closeInventory();
        plugin.getClanTradeManager().proposeTradeAsync(clan, player.getUniqueId(), target)
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void handleRequest(InventoryClickEvent event, Player player, Clan clan, UUID tradeId) {
        ClanTrade trade = plugin.getClanTradeManager().pendingTrade(tradeId).orElse(null);
        if (trade == null) {
            open(player, clan);
            return;
        }
        if (trade.fromClanId().equals(clan.id())) {
            plugin.getClanTradeManager().cancelTradeAsync(trade.id(), clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "trade.cancelled-confirm");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (event.isRightClick()) {
            plugin.getClanTradeManager().declineTradeAsync(trade.id(), clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "trade.declined-confirm");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        } else {
            player.closeInventory();
            plugin.getClanTradeManager().acceptTradeAsync(trade.id(), clan, player.getUniqueId())
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        }
    }

    private void handleDelivery(InventoryClickEvent event, Player player, Clan clan, Material material, State state) {
        if (plugin.getServerTradeManager().deliver(player, clan, material, event.isShiftClick())) {
            render(player, clan, state);
        }
    }
}
