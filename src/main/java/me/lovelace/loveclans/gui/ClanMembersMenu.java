package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.MembersView.Entry;
import me.lovelace.loveclans.gui.MembersView.Filter;
import me.lovelace.loveclans.gui.MembersView.Kind;
import me.lovelace.loveclans.gui.MembersView.Sort;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanInvite;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Members, applications and invitations of the clan in one screen (gui_gen v2.1, 54 slots): a filter and a sort
 * button in the header (slots 3 and 5, LMB forward / RMB back), the list in the work zone with pagination at
 * 36/44, "Invite" as the footer extra button. Filter, sort and page are remembered per player. The holder keeps
 * the slot -> entry map of what was actually shown, so a click is judged by what the player saw, and every
 * action is re-checked on the server (the real checks live in {@code ClanManager}).
 */
public final class ClanMembersMenu {
    private static final int SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_FILTER = 3;
    private static final int SLOT_SORT = 5;
    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_INVITE = 51;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private record State(Filter filter, Sort sort, int page) {}

    public static final class Holder extends ClanMenuHolder {
        private final Map<Integer, Entry> entries;

        Holder(UUID clanId, Map<Integer, Entry> entries) {
            super(ClanMenuType.MEMBERS, clanId);
            this.entries = entries;
        }

        Entry entryAt(int slot) {
            return entries.get(slot);
        }
    }

    private final LoveClansPlugin plugin;
    private final Map<UUID, State> stateByPlayer = new ConcurrentHashMap<>();

    public ClanMembersMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearPlayer(UUID playerId) {
        stateByPlayer.remove(playerId);
    }

    /** Opens the screen with the player's remembered filter/sort/page. */
    public void open(Player player, Clan clan) {
        State state = stateByPlayer.getOrDefault(player.getUniqueId(), new State(Filter.ALL, Sort.RANK, 0));
        render(player, clan, state);
    }

    /** Opens the screen on a given filter (e.g. {@code /clan applications}), keeping the remembered sort. */
    public void open(Player player, Clan clan, Filter filter) {
        State previous = stateByPlayer.getOrDefault(player.getUniqueId(), new State(Filter.ALL, Sort.RANK, 0));
        render(player, clan, new State(filter, previous.sort(), 0));
    }

    private boolean canHandleRequests(Clan clan, UUID playerId) {
        return clan.member(playerId).map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN).orElse(false);
    }

    private static String nameOf(OfflinePlayer offline, UUID id) {
        return offline.getName() != null ? offline.getName() : id.toString().substring(0, 8);
    }

    private List<Entry> collect(Clan clan, boolean manager) {
        List<Entry> entries = new ArrayList<>();
        for (ClanMember member : clan.members().values()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerId());
            entries.add(new Entry(Kind.MEMBER, member.playerId(), nameOf(offline, member.playerId()),
                    member.rank().weight(), member.contribution(), member.joinedAt(), offline.isOnline()));
        }
        if (manager) {
            for (ClanApplication application : plugin.getClanManager().getClanApplications(clan.id())) {
                OfflinePlayer applicant = Bukkit.getOfflinePlayer(application.applicantId());
                entries.add(new Entry(Kind.APPLICATION, application.applicantId(), nameOf(applicant, application.applicantId()),
                        0, 0, application.appliedAt(), false));
            }
            for (ClanInvite invite : plugin.getClanManager().getClanInvites(clan.id())) {
                OfflinePlayer invited = Bukkit.getOfflinePlayer(invite.invitedPlayer());
                entries.add(new Entry(Kind.INVITE, invite.invitedPlayer(), nameOf(invited, invite.invitedPlayer()),
                        0, 0, invite.expiresAt(), false));
            }
        }
        return entries;
    }

    private void render(Player player, Clan clan, State requested) {
        boolean manager = canHandleRequests(clan, player.getUniqueId());
        // A filter the viewer may not use (rights lost since it was remembered) falls back to "all".
        Filter filter = requested.filter().needsManager() && !manager ? Filter.ALL : requested.filter();
        List<Entry> shown = MembersView.apply(collect(clan, manager), filter, requested.sort(), manager);
        int page = MembersView.clampPage(requested.page(), shown.size());
        int pages = MembersView.pageCount(shown.size());
        stateByPlayer.put(player.getUniqueId(), new State(filter, requested.sort(), page));

        List<Entry> pageEntries = MembersView.page(shown, page);
        Map<Integer, Entry> slotMap = new HashMap<>();

        Holder holder = new Holder(clan.id(), slotMap);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                plugin.getMessages().component("gui.members-hub.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        // Header and Row1 are glass; the footer is glass up to the buttons. Work zone and its side walls stay free.
        for (int slot = 1; slot <= 17; slot++) inventory.setItem(slot, GuiFrames.glassPane());
        for (int slot = 45; slot <= 52; slot++) inventory.setItem(slot, GuiFrames.glassPane());

        String leaderName = clan.leaderId().map(id -> nameOf(Bukkit.getOfflinePlayer(id), id)).orElse("—");
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.members.info.name",
                        Map.of("name", clan.name(), "tag", clan.tag(), "color", clan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.members.info.lore", Map.of(
                        "level", String.valueOf(clan.level()),
                        "current", String.valueOf(clan.members().size()),
                        "max", String.valueOf(plugin.getClanManager().maxMembers(clan)),
                        "leader", leaderName), player))
                .build());

        inventory.setItem(SLOT_FILTER, cycleButton("filter", Filter.values(), filter, manager, player));
        inventory.setItem(SLOT_SORT, cycleButton("sort", Sort.values(), requested.sort(), true, player));

        if (shown.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.members-hub.empty.name", player))
                    .lore(plugin.getMessages().component("gui.members-hub.empty.lore", player))
                    .build());
        }
        for (int i = 0; i < pageEntries.size(); i++) {
            Entry entry = pageEntries.get(i);
            slotMap.put(CONTENT_SLOTS[i], entry);
            inventory.setItem(CONTENT_SLOTS[i], buildEntry(entry, clan, player));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }

        if (clan.hasPermission(player.getUniqueId(), ClanPermission.INVITE)) {
            boolean full = plugin.getClanManager().isClanFull(clan);
            inventory.setItem(SLOT_INVITE, (full ? ItemBuilder.head(ItemBuilder.HEAD_INACTIVE) : ItemBuilder.head(ItemBuilder.HEAD_INVITE))
                    .name(plugin.getMessages().component("gui.members.invite.name", player))
                    .lore(plugin.getMessages().component(full ? "gui.members.invite.lore-full" : "gui.members.invite.lore", player))
                    .build());
        }
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());

        player.openInventory(inventory);
    }

    /** A control button listing every option with the current one marked; LMB steps forward, RMB back. */
    private <E extends Enum<E>> ItemStack cycleButton(String group, E[] options, E current, boolean manager, Player player) {
        List<Component> lore = new ArrayList<>();
        for (E option : options) {
            if (option instanceof Filter f && f.needsManager() && !manager) continue;
            String key = "gui.members-hub." + group + "." + option.name().toLowerCase();
            lore.add(plugin.getMessages().component(
                    option == current ? "gui.members-hub.option-current" : "gui.members-hub.option-other",
                    Map.of("name", plugin.getMessages().raw(key)), player));
        }
        lore.add(Component.empty());
        lore.add(plugin.getMessages().component("gui.members-hub.cycle-hint", player));
        return ItemBuilder.head(group.equals("filter") ? ItemBuilder.HEAD_FILTER : ItemBuilder.HEAD_SORT)
                .name(plugin.getMessages().component("gui.members-hub." + group + "-title", player))
                .lore(lore)
                .build();
    }

    private ItemStack buildEntry(Entry entry, Clan clan, Player viewer) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.id());
        return switch (entry.kind()) {
            case MEMBER -> {
                ClanMember member = clan.member(entry.id()).orElse(null);
                String status = plugin.getMessages().raw(entry.online() ? "gui.members.item.status-online" : "gui.members.item.status-offline");
                ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(plugin.getMessages().component("gui.members.item.name", Map.of("player", entry.name()), viewer))
                        .lore(plugin.getMessages().component("gui.members.item.rank",
                                Map.of("rank", member != null ? member.rank().displayName() : "—"), viewer))
                        .lore(plugin.getMessages().component("gui.members.item.status", Map.of("status", status), viewer))
                        .lore(plugin.getMessages().component("gui.members.item.contribution",
                                Map.of("amount", String.valueOf(entry.contribution())), viewer));
                if (clan.hasPermission(viewer.getUniqueId(), ClanPermission.KICK) && !entry.id().equals(viewer.getUniqueId())) {
                    builder.lore(plugin.getMessages().component("gui.members.item.hint", viewer));
                }
                builder.mutate(meta -> {
                    if (meta instanceof SkullMeta skull) skull.setOwningPlayer(offline);
                });
                yield builder.build();
            }
            case APPLICATION -> ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(plugin.getMessages().component("gui.applications.applicant-item.name", Map.of("player", entry.name()), viewer))
                    .lore(plugin.getMessages().component("gui.applications.applicant-item.applied-at",
                            Map.of("date", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(entry.date()))), viewer))
                    .lore(plugin.getMessages().component("gui.applications.applicant-item.left-click-accept", viewer))
                    .lore(plugin.getMessages().component("gui.applications.applicant-item.right-click-reject", viewer))
                    .mutate(meta -> {
                        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(offline);
                    })
                    .build();
            case INVITE -> ItemBuilder.head(ItemBuilder.HEAD_INVITE_ITEM)
                    .name(plugin.getMessages().component("gui.applications.invite-item.name", Map.of("player", entry.name()), viewer))
                    .lore(plugin.getMessages().component("gui.applications.invite-item.status", viewer))
                    .build();
        };
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan, Holder holder) {
        int slot = event.getRawSlot();
        State state = stateByPlayer.getOrDefault(player.getUniqueId(), new State(Filter.ALL, Sort.RANK, 0));
        boolean forward = !event.isRightClick();
        boolean manager = canHandleRequests(clan, player.getUniqueId());

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_BACK -> plugin.getGuiManager().openMain(player, clan);
            case SLOT_FILTER -> render(player, clan, new State(state.filter().step(forward, manager), state.sort(), 0));
            case SLOT_SORT -> render(player, clan, new State(state.filter(), state.sort().step(forward), 0));
            case SLOT_PREV -> render(player, clan, new State(state.filter(), state.sort(), state.page() - 1));
            case SLOT_NEXT -> render(player, clan, new State(state.filter(), state.sort(), state.page() + 1));
            case SLOT_INVITE -> handleInvite(player, clan);
            default -> {
                Entry entry = holder.entryAt(slot);
                if (entry != null) handleEntry(event, player, clan, entry);
            }
        }
    }

    private void handleInvite(Player player, Clan clan) {
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.INVITE)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        if (plugin.getClanManager().isClanFull(clan)) {
            plugin.getMessages().send(player, "clan.member-limit-reached");
            return;
        }
        player.closeInventory();
        plugin.getMessages().send(player, "gui.members.invite.prompt");
        plugin.expectChatInput(player.getUniqueId(), (inputName, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(() -> open(player, clan));
                return;
            }
            plugin.getServer().dispatchCommand(player, "clan invite " + inputName);
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    plugin.getClanManager().getPlayerClan(player.getUniqueId()).ifPresent(refreshed -> open(player, refreshed)), 10L);
        });
    }

    private void handleEntry(InventoryClickEvent event, Player player, Clan clan, Entry entry) {
        switch (entry.kind()) {
            case MEMBER -> {
                if (entry.id().equals(player.getUniqueId())) return;
                // The detail screen is the kick/promote/demote entry point, so it needs the KICK right.
                if (!clan.hasPermission(player.getUniqueId(), ClanPermission.KICK)) {
                    plugin.getMessages().send(player, "general.no-permission");
                    return;
                }
                plugin.getGuiManager().openMemberDetail(player, clan, entry.id());
            }
            case APPLICATION -> {
                if (event.isLeftClick()) {
                    plugin.getClanManager().acceptApplicationAsync(clan, player.getUniqueId(), entry.id())
                            .thenAccept(updatedClan -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, "gui.applications.accepted", Map.of("player", entry.name()));
                                Player target = Bukkit.getPlayer(entry.id());
                                if (target != null) {
                                    plugin.getMessages().send(target, "clan.joined",
                                            Map.of("tag", updatedClan.tag(), "color", updatedClan.tagColor()));
                                }
                                open(player, updatedClan);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                } else if (event.isRightClick()) {
                    plugin.getClanManager().rejectApplicationAsync(clan, player.getUniqueId(), entry.id())
                            .thenRun(() -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, "gui.applications.rejected", Map.of("player", entry.name()));
                                open(player, clan);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                }
            }
            case INVITE -> { /* informational: an invitation is answered by the invited player */ }
        }
    }
}
