package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
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

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClanInfoMenu implements InventoryHolder {
    private static final int PER_ROW = 7;
    private static final int MAX_CONTENT_ROWS = 3;
    private static final int SLOT_ICON = 0;
    private static final int SLOT_LEADER = 4;

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    // Everyone except the owner - the owner has its own guaranteed slot (SLOT_LEADER) and must
    // not also take a slot in this grid, otherwise they'd be duplicated on-screen.
    private final List<ClanMember> otherMembers;
    private final int totalMemberCount;
    private Inventory inventory;

    private int headerRows;
    private int contentRows;
    private boolean paginated;
    private int currentPage;
    private int totalPages;
    private int backSlot;
    private int closeSlot;
    private int applySlot = -1;
    private int prevSlot = -1;
    private int nextSlot = -1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");

    public ClanInfoMenu(LoveClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
        this.totalMemberCount = clan.members().size();
        this.otherMembers = clan.members().values().stream()
                .filter(member -> member.rank() != ClanRank.LEADER)
                .sorted(Comparator.comparingInt((ClanMember member) -> member.rank().weight()).reversed())
                .toList();
    }

    public Clan clan() {
        return clan;
    }

    public void open() {
        // Grid capacity/pagination is based on OTHER members only - the owner never occupies a
        // grid slot, so an owner-only clan legitimately has an empty grid, not a bug.
        int otherCount = otherMembers.size();
        int noPaginationCapacity = PER_ROW * MAX_CONTENT_ROWS;
        this.paginated = otherCount > noPaginationCapacity;
        this.contentRows = paginated ? MAX_CONTENT_ROWS - 1 : Math.max(1, (int) Math.ceil(otherCount / (double) PER_ROW));
        int perPage = contentRows * PER_ROW;
        this.totalPages = paginated ? Math.max(1, (int) Math.ceil(otherCount / (double) perPage)) : 1;
        this.currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        // gui-gen v2.1: Header's second row (slots 9-17) is only part of the frame at 45+ slots.
        // Below that it must not exist at all - reserving it unconditionally left a dead, fully
        // empty row (neither header, content nor footer) for every clan under 15 other members.
        int footerRows = 1;
        int paginationRows = paginated ? 1 : 0;
        int rowsWithSingleHeader = 1 + contentRows + paginationRows + footerRows;
        this.headerRows = rowsWithSingleHeader * 9 >= 45 ? 2 : 1;
        int totalRows = headerRows + contentRows + paginationRows + footerRows;
        int size = totalRows * 9;

        this.inventory = Bukkit.createInventory(this, size,
                plugin.getMessages().component("gui.info.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor(), "name", clan.name()), player));

        // Rule 8: only the header row(s) (0-8, plus 9-17 once the menu is 45+ slots - holds leader
        // head/info/apply as control-row content) and the footer row (last 9 slots) are pure frame
        // — the member grid and pagination row in between are working zone and must stay glass-free
        // where unused.
        for (int slot = 0; slot < headerRows * 9; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
        for (int slot = size - 9; slot < size; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Слот 0 — иконка/баннер клана; та же информация (уровень/влияние/участники/статус),
        // теперь ещё и с датой основания клана и описанием клана, которые раньше нигде в этой
        // панели не показывались. Раньше эта карточка занимала слот 4 — его теперь отдали голове
        // лидера.
        String description = clan.description();
        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
        ItemBuilder info = ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.info.name",
                        Map.of("tag", clan.tag(), "color", clan.tagColor(), "name", clan.name()), player))
                .lore(plugin.getMessages().component("gui.info.level", Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.info.influence", Map.of("influence", String.valueOf(clan.influence())), player))
                .lore(plugin.getMessages().component("gui.info.members",
                        Map.of("current", String.valueOf(totalMemberCount),
                                "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .lore(plugin.getMessages().component(clan.isOpen() ? "gui.info.status.open" : "gui.info.status.closed", player))
                .lore(plugin.getMessages().component("gui.info.created-at",
                        Map.of("date", dateFormat.format(new java.util.Date(clan.createdAt()))), player))
                .lore(description == null || description.isBlank()
                        ? plugin.getMessages().component("gui.info.description-empty", player)
                        : plugin.getMessages().component("gui.info.description", Map.of("description", description), player));
        inventory.setItem(SLOT_ICON, info.build());

        // Слот 4 — голова лидера, ВСЕГДА присутствует независимо от числа остальных участников
        // (в т.ч. для клана из одного главы). Раньше лидер также попадал в общий список ниже
        // (sortedMembers строился из clan.members(), который его уже включает) — теперь
        // otherMembers явно его исключает, так что эта голова единственное место, где он
        // показан, без дублирования.
        clan.leaderId().ifPresent(leaderId -> {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            String leaderName = leader.getName() != null ? leader.getName() : leaderId.toString().substring(0, 8);
            boolean leaderOnline = leader.isOnline();
            String leaderStatus = leaderOnline
                    ? plugin.getMessages().raw("gui.members.item.status-online")
                    : plugin.getMessages().raw("gui.members.item.status-offline");
            ItemBuilder leaderHead = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(plugin.getMessages().component("gui.info.leader", Map.of("player", leaderName), player))
                    .lore(plugin.getMessages().component("gui.members.item.status", Map.of("status", leaderStatus), player));

            // "Немного больше информации о главе": дата вступления в клан и вклад (оба поля уже
            // персистентны на ClanMember, никакой новой трекинг-системы не заводим) плюс дата
            // последнего визита, когда глава сейчас оффлайн - "Оффлайн" одним словом не говорит,
            // насколько давно. НЕ подписываем дату вступления как "лидер с" - лидерство передаётся
            // вручную (transferLeadershipAsync) и по наследованию (SuccessionManager#finishVote),
            // а оба пути меняют только ранг и не трогают joinedAt, так что для унаследовавшего
            // лидера эта дата была бы враньём.
            clan.member(leaderId).ifPresent(leaderMember -> {
                leaderHead.lore(plugin.getMessages().component("gui.info.leader-member-since",
                        Map.of("date", dateFormat.format(new java.util.Date(leaderMember.joinedAt()))), player));
                leaderHead.lore(plugin.getMessages().component("gui.members.item.contribution",
                        Map.of("amount", String.valueOf(leaderMember.contribution())), player));
                if (!leaderOnline) {
                    // Тот же максимум (Bukkit-логин vs. последний раз замеченный кланом), что
                    // SuccessionManager#leaderAbsent уже использует для решения "пора ли голосовать
                    // за нового главу" - одно и то же представление "как давно" в UI и в механике.
                    long lastSeen = Math.max(leader.getLastPlayed(), leaderMember.lastSeen());
                    if (lastSeen > 0L) {
                        leaderHead.lore(plugin.getMessages().component("gui.info.leader-last-seen",
                                Map.of("date", dateFormat.format(new java.util.Date(lastSeen))), player));
                    }
                }
            });

            leaderHead.mutate(meta -> {
                if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(leader);
            });
            inventory.setItem(SLOT_LEADER, leaderHead.build());
        });

        // "Подать заявку" виден только если смотрящий ещё не в клане (ни в этом, ни в другом)
        // и не является лидером просматриваемого клана — второе тут избыточно (лидер клана уже
        // состоит в клане), но проверяем явно для ясности и на случай рассинхронизации данных.
        boolean alreadyInClan = plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent();
        boolean isLeaderOfViewedClan = clan.member(player.getUniqueId())
                .map(member -> member.rank() == ClanRank.LEADER)
                .orElse(false);
        boolean canApply = !alreadyInClan && !isLeaderOfViewedClan;

        int contentStartSlot = headerRows * 9;
        if (otherCount == 0) {
            // Owner-only clan: the grid legitimately has no one else to show - the owner is not
            // "missing", they're the head at SLOT_LEADER above. Text reflects that explicitly so
            // it doesn't read as "this clan has nobody in it".
            inventory.setItem(contentStartSlot + 4, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.info.no-members.name", player))
                    .lore(plugin.getMessages().component("gui.info.no-members.lore", player))
                    .build());
        } else {
            int start = currentPage * perPage;
            int end = Math.min(start + perPage, otherCount);
            int index = 0;
            for (int i = start; i < end; i++) {
                int row = index / PER_ROW;
                int col = index % PER_ROW;
                int slot = contentStartSlot + row * 9 + 1 + col;
                inventory.setItem(slot, createMemberItem(otherMembers.get(i)));
                index++;
            }
        }

        int rowCursor = headerRows + contentRows;
        if (paginated) {
            // Whenever paginated, contentRows is fixed at MAX_CONTENT_ROWS-1, so this row always
            // lands on slots 36-44 — the standard Prev/Next pagination slots for a full 54-slot menu.
            int paginationRowStart = rowCursor * 9;
            if (currentPage > 0) {
                prevSlot = paginationRowStart;
                inventory.setItem(prevSlot, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                        .name(plugin.getMessages().component("gui.previous-page", player)).build());
            } else {
                prevSlot = -1;
            }
            if (currentPage < totalPages - 1) {
                nextSlot = paginationRowStart + 8;
                inventory.setItem(nextSlot, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                        .name(plugin.getMessages().component("gui.next-page", player)).build());
            } else {
                nextSlot = -1;
            }
        } else {
            prevSlot = -1;
            nextSlot = -1;
        }

        backSlot = size - 2;
        closeSlot = size - 1;

        // "Подать заявку" сидит сразу перед "Назад" в footer'е (было — в header'е, слот 7).
        applySlot = size - 3;
        if (canApply) {
            if (clan.isOpen()) {
                inventory.setItem(applySlot, ItemBuilder.head(ItemBuilder.HEAD_INVITE)
                        .name(plugin.getMessages().component("gui.info.apply.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply.lore", player))
                        .build());
            } else {
                inventory.setItem(applySlot, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                        .name(plugin.getMessages().component("gui.info.apply-closed.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply-closed.lore", player))
                        .build());
            }
        } else {
            applySlot = -1;
        }

        inventory.setItem(backSlot, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(closeSlot, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack createMemberItem(ClanMember member) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerId());
        String name = offline.getName() != null ? offline.getName() : member.playerId().toString().substring(0, 8);
        String status = offline.isOnline()
                ? plugin.getMessages().raw("gui.members.item.status-online")
                : plugin.getMessages().raw("gui.members.item.status-offline");

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
        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }
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
        if (slot == applySlot && applySlot >= 0) {
            boolean inAnyClan = plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent();
            boolean isLeaderOfViewedClan = clan.member(player.getUniqueId())
                    .map(member -> member.rank() == ClanRank.LEADER)
                    .orElse(false);
            if (inAnyClan || isLeaderOfViewedClan || !clan.isOpen()) return;

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
