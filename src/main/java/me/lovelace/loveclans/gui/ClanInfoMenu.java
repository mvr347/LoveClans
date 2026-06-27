package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClanInfoMenu implements InventoryHolder {
    private static final int PER_ROW = 7;
    private static final int MAX_CONTENT_ROWS = 3;
    private static final int APPLY_SLOT = 7;

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    private final List<ClanMember> sortedMembers;
    private Inventory inventory;

    private int contentRows;
    private boolean paginated;
    private int currentPage;
    private int totalPages;
    private int backSlot;
    private int prevSlot = -1;
    private int nextSlot = -1;

    public ClanInfoMenu(LoveClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
        this.sortedMembers = clan.members().values().stream()
                .sorted(Comparator.comparingInt((ClanMember member) -> member.rank().weight()).reversed())
                .toList();
    }

    public Clan clan() {
        return clan;
    }

    public void open() {
        int memberCount = sortedMembers.size();
        int noPaginationCapacity = PER_ROW * MAX_CONTENT_ROWS;
        this.paginated = memberCount > noPaginationCapacity;
        this.contentRows = paginated ? MAX_CONTENT_ROWS - 1 : Math.max(1, (int) Math.ceil(memberCount / (double) PER_ROW));
        int perPage = contentRows * PER_ROW;
        this.totalPages = paginated ? Math.max(1, (int) Math.ceil(memberCount / (double) perPage)) : 1;
        this.currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        int totalRows = 2 + contentRows + (paginated ? 1 : 0) + 1;
        int size = totalRows * 9;

        this.inventory = Bukkit.createInventory(this, size,
                plugin.getMessages().component("gui.info.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor(), "name", clan.name()), player));

        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        clan.leaderId().ifPresent(leaderId -> {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            String leaderName = leader.getName() != null ? leader.getName() : leaderId.toString().substring(0, 8);
            ItemBuilder leaderHead = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(plugin.getMessages().component("gui.info.leader", Map.of("player", leaderName), player));
            leaderHead.mutate(meta -> {
                if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(leader);
            });
            inventory.setItem(1, leaderHead.build());
        });

        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
        ItemBuilder info = ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.info.name",
                        Map.of("tag", clan.tag(), "color", clan.tagColor(), "name", clan.name()), player))
                .lore(plugin.getMessages().component("gui.info.level", Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.info.members",
                        Map.of("current", String.valueOf(memberCount),
                                "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .lore(plugin.getMessages().component(clan.isOpen() ? "gui.info.status.open" : "gui.info.status.closed", player));
        inventory.setItem(4, info.build());

        boolean isMember = plugin.getClanManager().getPlayerClan(player.getUniqueId())
                .map(c -> c.id().equals(clan.id())).orElse(false);

        if (!isMember) {
            if (clan.isOpen()) {
                inventory.setItem(APPLY_SLOT, ItemBuilder.head(ItemBuilder.HEAD_INVITE)
                        .name(plugin.getMessages().component("gui.info.apply.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply.lore", player))
                        .build());
            } else {
                inventory.setItem(APPLY_SLOT, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.info.apply-closed.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply-closed.lore", player))
                        .build());
            }
        }

        int contentStartSlot = 2 * 9;
        if (memberCount == 0) {
            inventory.setItem(contentStartSlot + 4, ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("gui.info.no-members.name", player))
                    .lore(plugin.getMessages().component("gui.info.no-members.lore", player))
                    .build());
        } else {
            int start = currentPage * perPage;
            int end = Math.min(start + perPage, memberCount);
            int index = 0;
            for (int i = start; i < end; i++) {
                int row = index / PER_ROW;
                int col = index % PER_ROW;
                int slot = contentStartSlot + row * 9 + 1 + col;
                inventory.setItem(slot, createMemberItem(sortedMembers.get(i)));
                index++;
            }
        }

        int rowCursor = 2 + contentRows;
        if (paginated) {
            int paginationRowStart = rowCursor * 9;
            if (currentPage > 0) {
                prevSlot = paginationRowStart + 2;
                inventory.setItem(prevSlot, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                        .name(plugin.getMessages().component("gui.previous-page", player)).build());
            } else {
                prevSlot = -1;
            }
            inventory.setItem(paginationRowStart + 4, ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("gui.info.page-indicator",
                            Map.of("page", String.valueOf(currentPage + 1), "max", String.valueOf(totalPages)), player))
                    .build());
            if (currentPage < totalPages - 1) {
                nextSlot = paginationRowStart + 6;
                inventory.setItem(nextSlot, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                        .name(plugin.getMessages().component("gui.next-page", player)).build());
            } else {
                nextSlot = -1;
            }
            rowCursor++;
        } else {
            prevSlot = -1;
            nextSlot = -1;
        }

        backSlot = rowCursor * 9 + 4;
        inventory.setItem(backSlot, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack createMemberItem(ClanMember member) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerId());
        String name = offline.getName() != null ? offline.getName() : member.playerId().toString().substring(0, 8);
        String status = offline.isOnline() ? "<green>В сети</green>" : "<red>Оффлайн</red>";

        ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(plugin.getMessages().component("gui.members.item.name", Map.of("player", name), player))
                .lore(plugin.getMessages().component("gui.members.item.rank", Map.of("rank", member.rank().displayName()), player))
                .lore(plugin.getMessages().component("gui.members.item.status", Map.of("status", status), player));

        builder.mutate(meta -> {
            if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(offline);
        });
        return builder.build();
    }

    public void handleInventoryClick(int slot) {
        if (slot == backSlot) {
            new ClanListMenu(plugin, player).open();
            return;
        }
        if (slot == prevSlot && currentPage > 0) {
            currentPage--;
            open();
            return;
        }
        if (slot == nextSlot && currentPage < totalPages - 1) {
            currentPage++;
            open();
            return;
        }
        if (slot == APPLY_SLOT) {
            boolean isMember = plugin.getClanManager().getPlayerClan(player.getUniqueId())
                    .map(c -> c.id().equals(clan.id())).orElse(false);
            if (isMember || !clan.isOpen()) return;

            plugin.getClanManager().applyToClanAsync(clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "clan.applied", Map.of("tag", clan.tag(), "color", clan.tagColor()));
                        // Уведомляем всех онлайн-участников с правом INVITE кликабельным
                        // сообщением принять/отклонить заявку, чтобы не зависеть только от лидера.
                        String applicantName = player.getName() != null ? player.getName() : player.getUniqueId().toString();
                        for (Player recipient : plugin.getClanManager().getOnlineMembersWithPermission(clan, ClanPermission.INVITE)) {
                            plugin.getMessages().sendClickableApplication(recipient, applicantName, clan.tag(), clan.tagColor());
                        }
                        player.closeInventory();
                    }))
                    .exceptionally(t -> {
                        plugin.runSync(() -> plugin.sendOperationError(player, t));
                        return null;
                    });
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
