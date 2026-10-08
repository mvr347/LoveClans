package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.activity.ActivityCategory;
import me.lovelace.loveclans.activity.ActivityPeriod;
import me.lovelace.loveclans.activity.ClanActivity;
import me.lovelace.loveclans.activity.PlayerActivity;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan activity screen (gui_gen v2.1, 54 slots). Header controls (slots 2, 4, 6): the clan summary tab, the
 * members ranking tab and the period switch. The clan tab shows the total and the six categories; the members tab is
 * a paginated ranking (arrows only where a neighbouring page exists).
 */
public final class ClanActivityMenu {
    private static final int SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    /** Clan tab: three categories in the first work row, three in the second, the total below them. */
    private static final int[] CATEGORY_SLOTS = {20, 22, 24, 29, 31, 33};
    private static final int SLOT_TOTAL = 40;
    private static final int PAGE_SIZE = MembersView.PAGE_SIZE;

    public enum View { CLAN, MEMBERS }

    private record State(View view, ActivityPeriod period, int page) {}

    private final LoveClansPlugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ClanActivityMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        render(player, clan, states.getOrDefault(player.getUniqueId(), new State(View.CLAN, ActivityPeriod.LIFETIME, 0)));
    }

    private void render(Player player, Clan clan, State state) {
        states.put(player.getUniqueId(), state);
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.ACTIVITY, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                plugin.getMessages().component("gui.activity.title", Map.of("clan", clan.name()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame54(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.activity.info.name", Map.of("clan", clan.name()), player))
                .lore(plugin.getMessages().component("gui.activity.info.lore", player))
                .build());

        int[] controls = GuiFrames.controlSlots(3);
        inventory.setItem(controls[0], ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.activity.tab-clan", player))
                .glow(state.view() == View.CLAN).build());
        inventory.setItem(controls[1], ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.activity.tab-members", player))
                .glow(state.view() == View.MEMBERS).build());
        inventory.setItem(controls[2], CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.activity.period.name", "gui.activity.period.", ActivityPeriod.values(), state.period()));

        if (state.view() == View.CLAN) {
            renderClanTab(inventory, player, clan, state.period());
        } else {
            renderMembersTab(inventory, player, clan, state);
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private void renderClanTab(Inventory inventory, Player player, Clan clan, ActivityPeriod period) {
        ClanActivity activity = plugin.getActivityManager().getClanActivity(clan.id(), period);
        long[] values = {activity.war(), activity.raid(), activity.siege(), activity.territory(), activity.economy(), activity.diplomacy()};
        Material[] icons = {Material.IRON_SWORD, Material.CHEST, Material.CAMPFIRE, Material.FILLED_MAP, Material.GOLD_INGOT, Material.WRITABLE_BOOK};
        ActivityCategory[] categories = ActivityCategory.values();
        for (int i = 0; i < categories.length; i++) {
            String key = categories[i].name().toLowerCase(java.util.Locale.ROOT);
            inventory.setItem(CATEGORY_SLOTS[i], ItemBuilder.of(icons[i])
                    .name(plugin.getMessages().component("gui.activity.category." + key, Map.of("points", format(values[i])), player))
                    .lore(plugin.getMessages().component("gui.activity.category-lore", Map.of("points", format(values[i])), player))
                    .build());
        }
        inventory.setItem(SLOT_TOTAL, ItemBuilder.head(ItemBuilder.HEAD_EXPERIENCE)
                .name(plugin.getMessages().component("gui.activity.total", Map.of("points", format(activity.total())), player))
                .lore(plugin.getMessages().component("gui.activity.total-lore", player))
                .build());
    }

    private void renderMembersTab(Inventory inventory, Player player, Clan clan, State state) {
        List<PlayerActivity> ranking = new ArrayList<>(plugin.getActivityManager().getClanMembersRanking(clan.id(), state.period()));
        // Members that have not earned anything yet still belong in the list, at the bottom
        for (ClanMember member : clan.members().values()) {
            if (ranking.stream().noneMatch(a -> a.playerId().equals(member.playerId()))) {
                ranking.add(new PlayerActivity(member.playerId(), 0, 0, 0, 0, 0, 0, 0));
            }
        }
        int pages = Math.max(1, (ranking.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(state.page(), pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= ranking.size()) break;
            PlayerActivity entry = ranking.get(index);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
            String name = offline.getName() == null ? entry.playerId().toString().substring(0, 8) : offline.getName();
            inventory.setItem(CONTENT_SLOTS[i], ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(plugin.getMessages().component("gui.activity.member.name",
                            Map.of("place", String.valueOf(index + 1), "player", name), player))
                    .lore(plugin.getMessages().component("gui.activity.member.total", Map.of("points", format(entry.total())), player))
                    .lore(plugin.getMessages().component("gui.activity.member.details", Map.of(
                            "war", format(entry.war()), "raid", format(entry.raid()), "siege", format(entry.siege()),
                            "economy", format(entry.economy()), "diplomacy", format(entry.diplomacy())), player))
                    .mutate(meta -> {
                        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(offline);
                    })
                    .build());
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }
        states.put(player.getUniqueId(), new State(state.view(), state.period(), page));
    }

    private static String format(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan) {
        int slot = event.getRawSlot();
        State state = states.getOrDefault(player.getUniqueId(), new State(View.CLAN, ActivityPeriod.LIFETIME, 0));
        int[] controls = GuiFrames.controlSlots(3);
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
        } else if (slot == controls[0]) {
            render(player, clan, new State(View.CLAN, state.period(), 0));
        } else if (slot == controls[1]) {
            render(player, clan, new State(View.MEMBERS, state.period(), 0));
        } else if (slot == controls[2]) {
            render(player, clan, new State(state.view(), CycleButton.step(state.period(), ActivityPeriod.values(), !event.isRightClick()), 0));
        } else if (slot == SLOT_PREV && state.view() == View.MEMBERS) {
            render(player, clan, new State(state.view(), state.period(), state.page() - 1));
        } else if (slot == SLOT_NEXT && state.view() == View.MEMBERS) {
            render(player, clan, new State(state.view(), state.period(), state.page() + 1));
        }
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }
}
