package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Список кланов" (§10) - the /clans main menu. Keeps the existing 28-slot browsing grid (rows
 * 1-4) rather than shrinking to §10.1's 6-slots-per-page mockup, which would noticeably hurt
 * usability for servers with more than a handful of clans; the requested "my clan" shortcut,
 * sort/filter cycling and always-visible "my applications" button are added into the header/footer
 * rows that were previously just border glass, the same adaptation already used for the
 * Diplomacy &amp; Trade browser (§6.1/{@link ClanDiplomacySelectMenu}).
 */
public final class ClanListMenu implements InventoryHolder {
    // gui_gen 54-slot working zone is 18-44 only (three rows) — row 1 (9-17) is always frame.
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_MY_CLAN = 0;
    private static final int SLOT_FILTER = 3;
    private static final int SLOT_SORT = 5;
    private static final int SLOT_PREVIOUS = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_MY_APPLICATIONS = 52;
    // Back exists only for the Guildmaster flow; that player is in a clan, so it never shares 52 with applications
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;

    private enum SortMode {
        NEWEST(Comparator.comparingLong(Clan::createdAt).reversed()),
        OLDEST(Comparator.comparingLong(Clan::createdAt)),
        INFLUENCE(Comparator.comparingLong(Clan::influence).reversed()),
        MEMBERS(Comparator.comparingInt((Clan c) -> c.members().size()).reversed());

        final Comparator<Clan> comparator;

        SortMode(Comparator<Clan> comparator) {
            this.comparator = comparator;
        }

    }

    private enum FilterMode {
        ALL,
        AT_WAR,
        PEACEFUL,
        CLOSED,
        OPEN;

    }

    /** Filter/sort/page remembered per player between openings of the list (cleared on quit). */
    private record State(FilterMode filter, SortMode sort, int page) {}

    private static final Map<java.util.UUID, State> STATE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearPlayer(java.util.UUID playerId) {
        STATE.remove(playerId);
    }

    private final LoveClansPlugin plugin;
    private final Player player;
    private int currentPage;
    private SortMode sortMode = SortMode.NEWEST;
    private FilterMode filterMode = FilterMode.ALL;
    private List<Clan> visibleClans = List.of();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public ClanListMenu(LoveClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        State remembered = STATE.get(player.getUniqueId());
        if (remembered != null) {
            this.filterMode = remembered.filter();
            this.sortMode = remembered.sort();
            this.currentPage = remembered.page();
        } else {
            this.currentPage = 0;
        }
    }

    private boolean matchesFilter(Clan clan) {
        return switch (filterMode) {
            case ALL -> true;
            case AT_WAR -> plugin.getClanManager().inAnyConflict(clan.id());
            case PEACEFUL -> !plugin.getClanManager().inAnyConflict(clan.id());
            case CLOSED -> !clan.isOpen();
            case OPEN -> clan.isOpen();
        };
    }

    private void recomputeVisibleClans() {
        visibleClans = plugin.getClanManager().getAllClans().stream()
                .filter(this::matchesFilter)
                .sorted(sortMode.comparator)
                .toList();
    }

    @Override
    public Inventory getInventory() {
        recomputeVisibleClans();
        int maxPage = Math.max(0, (visibleClans.size() - 1) / CONTENT_SLOTS.length);
        currentPage = Math.max(0, Math.min(currentPage, maxPage));
        STATE.put(player.getUniqueId(), new State(filterMode, sortMode, currentPage));

        Inventory inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.clan-list.title", Map.of("page", String.valueOf(currentPage + 1), "max", String.valueOf(maxPage + 1)), player));
        fillGlass(inventory);

        Optional<Clan> myClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (myClan.isPresent()) {
            Clan clan = myClan.get();
            Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
            inventory.setItem(SLOT_MY_CLAN, ItemBuilder.of(emblemMaterial)
                    .name(plugin.getMessages().component("gui.clan-list.my-clan.name",
                            Map.of("tag", clan.tag(), "color", clan.tagColor()), player))
                    .lore(plugin.getMessages().components("gui.clan-list.my-clan.lore", Map.of(
                            "level", String.valueOf(clan.level()),
                            "influence", String.valueOf(clan.influence()),
                            "members", String.valueOf(clan.members().size())
                    ), player))
                    .build());
        } else {
            // Клана нет — слот 0 просто информирует об этом (некликабельно, см.
            // handleInventoryClick: SLOT_MY_CLAN там уже no-op без клана).
            inventory.setItem(SLOT_MY_CLAN, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.clan-list.no-clan.name", player))
                    .lore(plugin.getMessages().component("gui.clan-list.no-clan.lore", player))
                    .build());
        }

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, visibleClans.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(CONTENT_SLOTS[index - start], createClanItem(visibleClans.get(index)));
        }

        if (visibleClans.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.clan-list.empty.name", player))
                    .lore(plugin.getMessages().component("gui.clan-list.empty.lore", player))
                    .build());
        }

        if (currentPage > 0) inventory.setItem(SLOT_PREVIOUS, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                .name(plugin.getMessages().component("gui.previous-page", player)).build());
        if (currentPage < maxPage) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }

        inventory.setItem(SLOT_FILTER, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.clan-list.filter.name", "gui.clan-list.filter.", FilterMode.values(), filterMode));
        inventory.setItem(SLOT_SORT, CycleButton.build(plugin, player, ItemBuilder.HEAD_SORT,
                "gui.clan-list.sort.name", "gui.clan-list.sort.", SortMode.values(), sortMode));

        boolean notInClan = myClan.isEmpty();
        if (notInClan) {
            int appCount = countPlayerApplicationsAndInvites();
            inventory.setItem(SLOT_MY_APPLICATIONS, ItemBuilder.head(ItemBuilder.HEAD_MSG)
                    .name(plugin.getMessages().component("gui.clan-list.my-applications.name", player))
                    .lore(plugin.getMessages().component("gui.clan-list.my-applications.lore",
                            Map.of("count", String.valueOf(appCount)), player))
                    .build());
        }

        if (plugin.getGuiManager().inGuildmasterSession(player)) {
            inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                    .name(plugin.getMessages().component("gui.back", player)).build());
        }

        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());

        return inventory;
    }

    private int contentSlotIndex(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private ItemStack createClanItem(Clan clan) {
        boolean closed = !clan.isOpen();
        boolean isMember = plugin.getClanManager().getPlayerClan(player.getUniqueId())
                .map(c -> c.id().equals(clan.id())).orElse(false);

        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;

        ItemBuilder builder = ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.clan-list.clan-item.name",
                        Map.of("tag", clan.tag(), "color", clan.tagColor(), "name", clan.name()), player));

        // Признанность клана не зависит от наличия главы, поэтому строка ставится
        // отдельно от блока leaderId.ifPresent ниже - иначе безглавый клан (edge case)
        // остался бы вовсе без этого статуса.
        builder.lore(plugin.getMessages().component(clan.isRecognized()
                ? "gui.clan-list.clan-item.status-recognized"
                : "gui.clan-list.clan-item.status-unrecognized", player));

        // Leader information
        clan.leaderId().ifPresent(leaderId -> {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            String leaderName = leader.getName() != null ? leader.getName() : leaderId.toString().substring(0, 8);
            builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.leader",
                    Map.of("player", leaderName), player));

            builder.lore(plugin.getMessages().component(closed
                    ? "gui.clan-list.clan-item.status.closed"
                    : "gui.clan-list.clan-item.status.open", player));

            if (leader.isOnline()) {
                builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.leader-status.online", player));
            } else {
                builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.leader-status.offline", player));
                if (leader.getLastPlayed() > 0) {
                    builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.leader-last-seen",
                            Map.of("date", dateTimeFormat.format(new Date(leader.getLastPlayed()))), player));
                }
            }
        });

        builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.level",
                        Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.clan-list.clan-item.influence",
                        Map.of("influence", String.valueOf(clan.influence())), player))
                .lore(plugin.getMessages().component("gui.clan-list.clan-item.members",
                        Map.of("current", String.valueOf(clan.members().size()),
                                "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .lore(plugin.getMessages().component("gui.clan-list.clan-item.created-at",
                        Map.of("date", dateFormat.format(clan.createdAt())), player));

        // Apply/Status button logic
        if (isMember) {
            builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.already-member", player));
        } else if (closed) {
            builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.apply-closed", player));
        } else { // Open clan
            builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.apply-open", player));
        }

        builder.lore(plugin.getMessages().component("gui.clan-list.clan-item.click-info", player));

        return builder.build();
    }

    private int countPlayerApplicationsAndInvites() {
        int apps = (int) plugin.getClanManager().getAllClans().stream()
                .flatMap(c -> plugin.getClanManager().getClanApplications(c.id()).stream())
                .filter(a -> a.applicantId().equals(player.getUniqueId()))
                .count();
        int invites = plugin.getClanManager().getPlayerInvites(player.getUniqueId()).size();
        return apps + invites;
    }

    public void open() { player.openInventory(getInventory()); }

    public void handleInventoryClick(int slot, boolean rightClick) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK && plugin.getGuiManager().inGuildmasterSession(player)) {
            plugin.getClanManager().getPlayerClan(player.getUniqueId())
                    .ifPresentOrElse(clan -> plugin.getGuiManager().openGuildmaster(player, clan), player::closeInventory);
            return;
        }
        if (slot == SLOT_MY_CLAN) {
            plugin.getClanManager().getPlayerClan(player.getUniqueId())
                    .ifPresent(clan -> plugin.getGuiManager().openMain(player, clan));
            return;
        }
        if (slot == SLOT_FILTER) {
            filterMode = CycleButton.step(filterMode, FilterMode.values(), !rightClick);
            currentPage = 0;
            open();
            return;
        }
        if (slot == SLOT_SORT) {
            sortMode = CycleButton.step(sortMode, SortMode.values(), !rightClick);
            currentPage = 0;
            open();
            return;
        }
        if (slot == SLOT_PREVIOUS && currentPage > 0) { currentPage--; open(); return; }

        int maxPage = Math.max(0, (visibleClans.size() - 1) / CONTENT_SLOTS.length);
        if (slot == SLOT_NEXT && currentPage < maxPage) { currentPage++; open(); return; }
        if (slot == SLOT_MY_APPLICATIONS && plugin.getClanManager().getPlayerClan(player.getUniqueId()).isEmpty()) {
            new PlayerApplicationsMenu(plugin, player).open();
            return;
        }

        int relativeIndex = contentSlotIndex(slot);
        if (relativeIndex < 0) return;
        int clanIndex = currentPage * CONTENT_SLOTS.length + relativeIndex;
        if (clanIndex < 0 || clanIndex >= visibleClans.size()) return;

        Clan clickedClan = visibleClans.get(clanIndex);

        // Click → open clan info
        new ClanInfoMenu(plugin, player, clickedClan).open();
    }

    /** Rule 8/4.1: header (0-8), row 1 (9-17, always frame in 54-slot menus) and footer (45-53)
     *  are the only pure-frame slots — the working zone (18-44) hosts the clan grid. */
    private void fillGlass(Inventory inventory) {
        GuiFrames.fillFrame54(inventory);
    }
}
