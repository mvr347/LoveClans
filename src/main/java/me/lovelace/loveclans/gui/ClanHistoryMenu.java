package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.history.ClanHistoryEntry;
import me.lovelace.loveclans.history.HistoryFilter;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The clan chronicle (gui_gen v2.1, 54 slots): one page of the stored history at a time, newest first. The page is read
 * from the database off the main thread (one extra row tells whether a next page exists) and the screen opens when it
 * arrives. The only header control is the filter switch (slot 4).
 */
public final class ClanHistoryMenu {
    private static final int SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_FILTER = 4;
    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PAGE_SIZE = CONTENT_SLOTS.length;

    private record State(HistoryFilter filter, int page) {}

    private final LoveClansPlugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ClanHistoryMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        load(player, clan, states.getOrDefault(player.getUniqueId(), new State(HistoryFilter.ALL, 0)));
    }

    private void load(Player player, Clan clan, State state) {
        states.put(player.getUniqueId(), state);
        // PAGE_SIZE + 1 rows: the extra one only says that a next page exists
        plugin.getHistoryManager().get(clan.id(), state.filter(), state.page(), PAGE_SIZE + 1)
                .whenComplete((entries, failure) -> plugin.runSync(() -> {
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        plugin.sendOperationError(player, failure);
                        return;
                    }
                    render(player, clan, state, entries);
                }));
    }

    private void render(Player player, Clan clan, State state, List<ClanHistoryEntry> entries) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.HISTORY, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component("gui.history.title",
                Map.of("clan", clan.name(), "page", String.valueOf(state.page() + 1)), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame54(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.history.info.name", Map.of("clan", clan.name()), player))
                .lore(plugin.getMessages().component("gui.history.info.lore", player))
                .build());
        inventory.setItem(SLOT_FILTER, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.history.filter.name", "gui.history.filter.", HistoryFilter.values(), state.filter()));

        if (entries.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.history.empty", player)).build());
        }
        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (int i = 0; i < Math.min(entries.size(), PAGE_SIZE); i++) {
            ClanHistoryEntry entry = entries.get(i);
            inventory.setItem(CONTENT_SLOTS[i], ItemBuilder.of(iconOf(entry))
                    .name(plugin.getMessages().component("gui.history.entry." + entry.type().name(), placeholders(entry), player))
                    .lore(plugin.getMessages().component("gui.history.entry-date", Map.of(
                            "date", format.format(new Date(entry.timestamp())),
                            "ago", TimeUtil.formatDuration(Math.max(0L, now - entry.timestamp()))), player))
                    .build());
        }
        if (state.page() > 0) {
            inventory.setItem(SLOT_PREV, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (entries.size() > PAGE_SIZE) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    /** Names are resolved when the entry is shown, so a renamed clan or player appears under the current name. */
    private Map<String, String> placeholders(ClanHistoryEntry entry) {
        Map<String, String> map = new HashMap<>();
        Map<String, String> meta = entry.metadata();
        String opponent = meta.get("opponentClanId");
        String tag = "?";
        String color = "<white>";
        if (opponent != null) {
            try {
                Clan other = plugin.getClanManager().getClanById(UUID.fromString(opponent)).orElse(null);
                if (other != null) {
                    tag = other.tag();
                    color = other.tagColor();
                }
            } catch (IllegalArgumentException ignored) {
                // malformed id in the metadata
            }
        }
        map.put("tag", tag);
        map.put("color", color);
        if (entry.actorId() != null) {
            OfflinePlayer actor = Bukkit.getOfflinePlayer(entry.actorId());
            map.put("player", actor.getName() == null ? "?" : actor.getName());
        } else {
            map.put("player", "?");
        }
        map.put("level", meta.getOrDefault("level", "?"));
        map.put("old", meta.getOrDefault("old", "?"));
        map.put("new", meta.getOrDefault("new", "?"));
        map.put("name", meta.getOrDefault("name", "?"));
        return map;
    }

    private static Material iconOf(ClanHistoryEntry entry) {
        return switch (entry.type()) {
            case CLAN_CREATED -> Material.WHITE_BANNER;
            case CLAN_LEVEL_UP -> Material.EXPERIENCE_BOTTLE;
            case MEMBER_JOINED -> Material.LIME_DYE;
            case MEMBER_LEFT -> Material.GRAY_DYE;
            case MEMBER_KICKED -> Material.RED_DYE;
            case RANK_CHANGED -> Material.NAME_TAG;
            case LEADER_CHANGED -> Material.GOLDEN_HELMET;
            case WAR_DECLARED, RAID_STARTED, SIEGE_DECLARED -> Material.PAPER;
            case WAR_WON, RAID_WON, SIEGE_WON, SIEGE_DEFENDED -> Material.LIME_BANNER;
            case WAR_LOST, RAID_LOST, SIEGE_LOST -> Material.RED_BANNER;
            case TERRITORY_CAPTURED -> Material.FILLED_MAP;
            case TERRITORY_LOST -> Material.MAP;
            case DIPLOMACY_CREATED -> Material.EMERALD;
            case DIPLOMACY_ENDED -> Material.REDSTONE;
        };
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan) {
        int slot = event.getRawSlot();
        State state = states.getOrDefault(player.getUniqueId(), new State(HistoryFilter.ALL, 0));
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
        } else if (slot == SLOT_FILTER) {
            HistoryFilter[] all = HistoryFilter.values();
            HistoryFilter next = CycleButton.step(state.filter(), all, !event.isRightClick());
            load(player, clan, new State(next, 0));
        } else if (slot == SLOT_PREV && state.page() > 0) {
            load(player, clan, new State(state.filter(), state.page() - 1));
        } else if (slot == SLOT_NEXT) {
            load(player, clan, new State(state.filter(), state.page() + 1));
        }
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }
}
