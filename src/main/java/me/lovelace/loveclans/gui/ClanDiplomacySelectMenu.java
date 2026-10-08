package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Дипломатия и Торговля" (§6.1) - the clan browser that fans out into the per-clan relations
 * menu (left click, §6.2 - {@link ClanDiplomacyMenu}) or straight into a trade invite (right
 * click, §4.2/§6.3 - see ClanTradeManager#proposeTradeAsync). Sort/filter are {@link CycleButton}s:
 * LMB steps forward, RMB back.
 */
public final class ClanDiplomacySelectMenu implements InventoryHolder {
    // gui_gen 54-slot working zone is 18-44 only (three rows) — row 1 (9-17) is always frame.
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_INFO = 0;
    private static final int SLOT_SORT = 5;
    private static final int SLOT_FILTER = 3; // same pair as ClanListMenu / ClanMembersMenu: GuiFrames.controlSlots(2)
    private static final int SLOT_PREVIOUS = 36;
    private static final int SLOT_NEXT = 44;
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

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan sourceClan;
    private int currentPage;
    private SortMode sortMode = SortMode.NEWEST;
    private FilterMode filterMode = FilterMode.ALL;
    private List<Clan> visibleClans = List.of();
    private Inventory inventory;

    public ClanDiplomacySelectMenu(LoveClansPlugin plugin, Player player, Clan sourceClan) {
        this.plugin = plugin;
        this.player = player;
        this.sourceClan = sourceClan;
        this.currentPage = 0;
    }

    public boolean hasClans() {
        return plugin.getClanManager().getAllClans().stream().anyMatch(clan -> !clan.id().equals(sourceClan.id()));
    }

    private boolean matchesFilter(Clan clan) {
        return switch (filterMode) {
            case ALL -> true;
            case AT_WAR -> plugin.getClanManager().inConflictWith(sourceClan.id(), clan.id());
            case PEACEFUL -> !plugin.getClanManager().inConflictWith(sourceClan.id(), clan.id());
            case CLOSED -> !clan.isOpen();
            case OPEN -> clan.isOpen();
        };
    }

    private void recomputeVisibleClans() {
        visibleClans = plugin.getClanManager().getAllClans().stream()
                .filter(clan -> !clan.id().equals(sourceClan.id()))
                .filter(this::matchesFilter)
                .sorted(sortMode.comparator)
                .toList();
    }

    public void open() {
        recomputeVisibleClans();
        int maxPage = Math.max(0, (visibleClans.size() - 1) / CONTENT_SLOTS.length);
        currentPage = Math.max(0, Math.min(currentPage, maxPage));

        this.inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.diplomacy-select.title", Map.of("tag", sourceClan.tag(), "color", sourceClan.tagColor()), player));

        // Rule 8/4.1: header (0-8), row 1 (9-17, always frame) and footer (45-53) are frame —
        // the working zone (18-44) hosts the clan grid.
        GuiFrames.fillFrame54(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.diplomacy-select.title", Map.of("tag", sourceClan.tag(), "color", sourceClan.tagColor()), player))
                .build());

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, visibleClans.size());
        for (int index = start; index < end; index++) {
            Clan target = visibleClans.get(index);
            int targetSlot = CONTENT_SLOTS[index - start];

            Material emblemMaterial = target.emblem().name().endsWith("_BANNER") ? target.emblem() : Material.WHITE_BANNER;
            ItemBuilder builder = ItemBuilder.of(emblemMaterial)
                    .name(plugin.getMessages().component("gui.diplomacy-select.clan-item.name",
                            Map.of("tag", target.tag(), "color", target.tagColor(), "name", target.name()), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.relation",
                            Map.of("relation", plugin.getMessages().relationName(sourceClan.relationTo(target.id()))), player));

            if (plugin.getWarManager().areAtWar(sourceClan.id(), target.id())) {
                builder.lore(Component.text("§c⚔ Идёт война"));
            } else if (plugin.getSiegeManager().areInSiege(sourceClan.id(), target.id())) {
                builder.lore(Component.text("§6🏕 Идёт осада"));
            } else if (plugin.getRaidManager().areInRaid(sourceClan.id(), target.id())) {
                builder.lore(Component.text("§e🏹 Идёт набег"));
            }

            builder.lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.hint", player));
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, target.id().toString()));
            inventory.setItem(targetSlot, builder.build());
        }

        if (visibleClans.isEmpty()) {
            inventory.setItem(31, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.diplomacy-select.empty", player))
                    .build());
        }

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREVIOUS, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (currentPage < maxPage) {
            inventory.setItem(SLOT_NEXT, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }

        inventory.setItem(SLOT_SORT, CycleButton.build(plugin, player, ItemBuilder.HEAD_SORT,
                "gui.diplomacy-select.sort.name", "gui.diplomacy-select.sort.", SortMode.values(), sortMode));
        inventory.setItem(SLOT_FILTER, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.diplomacy-select.filter.name", "gui.diplomacy-select.filter.", FilterMode.values(), filterMode));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(int slot, boolean rightClick) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, sourceClan);
            return;
        }
        if (slot == SLOT_SORT) {
            sortMode = CycleButton.step(sortMode, SortMode.values(), !rightClick);
            currentPage = 0;
            open();
            return;
        }
        if (slot == SLOT_FILTER) {
            filterMode = CycleButton.step(filterMode, FilterMode.values(), !rightClick);
            currentPage = 0;
            open();
            return;
        }
        int maxPage = Math.max(0, (visibleClans.size() - 1) / CONTENT_SLOTS.length);
        if (slot == SLOT_PREVIOUS && currentPage > 0) { currentPage--; open(); return; }
        if (slot == SLOT_NEXT && currentPage < maxPage) { currentPage++; open(); return; }

        org.bukkit.inventory.ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        String rawId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rawId == null) return;
        try {
            UUID targetId = UUID.fromString(rawId);
            plugin.getClanManager().getClanById(targetId).ifPresent(targetClan -> {
                if (rightClick) {
                    if (!sourceClan.hasPermission(player.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.TRADE)) {
                        plugin.getMessages().send(player, "general.no-permission");
                        return;
                    }
                    if (plugin.getClanTradeManager().tradeBlocked(sourceClan.id(), targetClan.id())) {
                        plugin.getMessages().send(player, "trade.blocked");
                        return;
                    }
                    player.closeInventory();
                    plugin.getClanTradeManager().proposeTradeAsync(sourceClan, player.getUniqueId(), targetClan)
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                } else {
                    plugin.getGuiManager().openDiplomacy(player, sourceClan, targetClan);
                }
            });
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
