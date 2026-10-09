package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.activity.ActivityCategory;
import me.lovelace.loveclans.activity.ActivityPeriod;
import me.lovelace.loveclans.activity.ClanActivity;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan-level activity (gui_gen v2.1, 54 slots): the total and the six categories for the chosen period. Members'
 * activity is not here: it lives in the members hub ("Участники и заявки"), next to the people it belongs to.
 */
public final class ClanActivityMenu {
    private static final int SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_PERIOD = 4;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    /** Six categories in two rows of three, the total below them. */
    private static final int[] CATEGORY_SLOTS = {20, 22, 24, 29, 31, 33};
    private static final int SLOT_TOTAL = 40;

    private final LoveClansPlugin plugin;
    private final Map<UUID, ActivityPeriod> periods = new ConcurrentHashMap<>();

    public ClanActivityMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        render(player, clan, periods.getOrDefault(player.getUniqueId(), ActivityPeriod.LIFETIME));
    }

    private void render(Player player, Clan clan, ActivityPeriod period) {
        periods.put(player.getUniqueId(), period);
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.ACTIVITY, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                plugin.getMessages().component("gui.activity.title", Map.of("clan", clan.name()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame54(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.activity.info.name", Map.of("clan", clan.name()), player))
                .lore(plugin.getMessages().component("gui.activity.info.lore", player))
                .build());
        inventory.setItem(SLOT_PERIOD, CycleButton.build(plugin, player, ItemBuilder.HEAD_FILTER,
                "gui.activity.period.name", "gui.activity.period.", ActivityPeriod.values(), period));

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

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private static String format(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan) {
        int slot = event.getRawSlot();
        ActivityPeriod current = periods.getOrDefault(player.getUniqueId(), ActivityPeriod.LIFETIME);
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
        } else if (slot == SLOT_PERIOD) {
            render(player, clan, CycleButton.step(current, ActivityPeriod.values(), !event.isRightClick()));
        }
    }

    public void clear(UUID playerId) {
        periods.remove(playerId);
    }
}
