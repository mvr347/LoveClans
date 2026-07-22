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
 * click, §4.2/§6.3 - see ClanTradeManager#proposeTradeAsync). Sort/filter are spec'd as mouse-wheel cycles,
 * which vanilla container GUIs can't actually capture outside the hotbar - left-click cycles
 * forward here instead, the same adaptation already used elsewhere in this plugin for stateful
 * toggle buttons.
 */
public final class ClanDiplomacySelectMenu implements InventoryHolder {
    // Framed content grid — columns 0 and 8 of each row stay reserved for the border/pagination.
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_INFO = 0;
    private static final int SLOT_SORT = 2;
    private static final int SLOT_FILTER = 3;
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

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private enum FilterMode {
        ALL,
        AT_WAR,
        PEACEFUL,
        CLOSED,
        OPEN;

        FilterMode next() {
            FilterMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
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

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        Material sourceEmblem = sourceClan.emblem().name().endsWith("_BANNER") ? sourceClan.emblem() : Material.WHITE_BANNER;
        inventory.setItem(SLOT_INFO, ItemBuilder.of(sourceEmblem)
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
                            Map.of("relation", sourceClan.relationTo(target.id()).name()), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.hint", player));
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

        inventory.setItem(SLOT_SORT, ItemBuilder.head(ItemBuilder.HEAD_SORT)
                .name(plugin.getMessages().component("gui.diplomacy-select.sort.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy-select.sort." + sortMode.name().toLowerCase(java.util.Locale.ROOT), player))
                .build());
        inventory.setItem(SLOT_FILTER, ItemBuilder.head(ItemBuilder.HEAD_FILTER)
                .name(plugin.getMessages().component("gui.diplomacy-select.filter.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy-select.filter." + filterMode.name().toLowerCase(java.util.Locale.ROOT), player))
                .build());

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
            sortMode = sortMode.next();
            open();
            return;
        }
        if (slot == SLOT_FILTER) {
            filterMode = filterMode.next();
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
