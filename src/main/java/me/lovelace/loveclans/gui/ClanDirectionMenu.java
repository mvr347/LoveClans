package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.upgrade.UpgradeOfferManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanDirection;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Choice of the clan's development direction (gui_gen v2.1, 27 slots): three big buttons centred in
 * the work row. The choice is permanent, so a click only opens the confirmation screen.
 */
public final class ClanDirectionMenu {
    private static final int SIZE = 27;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    private static final ClanDirection[] ORDER = ClanDirection.values();

    private final LoveClansPlugin plugin;

    public ClanDirectionMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private static String headFor(ClanDirection direction) {
        return switch (direction) {
            case FARMING -> ItemBuilder.HEAD_PERK_HARVESTER;
            case MINING -> ItemBuilder.HEAD_PERK_MINER;
            case MILITARY -> ItemBuilder.HEAD_PERK_WARRIOR;
        };
    }

    private static String langKey(ClanDirection direction) {
        return direction.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Tier levels and per-tier numbers for the lore templates (t1..t3, a1..c3). */
    private Map<String, String> placeholders(ClanDirection direction) {
        var cfg = plugin.getConfig();
        Map<String, String> values = new HashMap<>();
        values.put("t1", String.valueOf(cfg.getInt("direction.tiers.first", 5)));
        values.put("t2", String.valueOf(cfg.getInt("direction.tiers.second", 10)));
        values.put("t3", String.valueOf(cfg.getInt("direction.tiers.third", 15)));
        String[] paths = switch (direction) {
            case FARMING -> new String[]{"crop-growth-percent", "harvest-yield-percent"};
            case MINING -> new String[]{"break-speed-percent", "extra-drop-percent"};
            case MILITARY -> new String[]{"damage-own-land-percent", "resistance-own-land-percent", "cooldown-reduction-percent"};
        };
        String[] letters = {"a", "b", "c"};
        for (int p = 0; p < paths.length; p++) {
            for (int tier = 1; tier <= 3; tier++) {
                double value = plugin.getClanBonuses().directionValueAt(direction, paths[p], tier);
                values.put(letters[p] + tier, value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value));
            }
        }
        return values;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.DIRECTION, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component(
                "gui.direction.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame27(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT_ABILITIES)
                .name(plugin.getMessages().component("gui.direction.info.name", player))
                .lore(plugin.getMessages().components("gui.direction.info.lore", placeholders(ClanDirection.FARMING), player))
                .build());

        int[] slots = GuiFrames.workRowSlots(ORDER.length);
        for (int i = 0; i < ORDER.length; i++) {
            ClanDirection direction = ORDER[i];
            String key = langKey(direction);
            inventory.setItem(slots[i], ItemBuilder.head(headFor(direction))
                    .name(plugin.getMessages().component("gui.direction." + key + ".name", player))
                    .lore(plugin.getMessages().components("gui.direction." + key + ".lore", placeholders(direction), player))
                    .lore(plugin.getMessages().components("gui.direction.choose", player))
                    .build());
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openUpgrades(player, clan);
            return;
        }
        int[] slots = GuiFrames.workRowSlots(ORDER.length);
        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            ClanDirection direction = ORDER[i];
            Map<String, String> values = Map.of("direction", direction.displayName());
            plugin.getGuiManager().openConfirm(player, clan,
                    plugin.getMessages().component("gui.direction.confirm.title", values, player),
                    plugin.getMessages().components("gui.direction.confirm.effects", values, player),
                    () -> confirm(player, clan, direction),
                    () -> open(player, clan));
            return;
        }
    }

    private void confirm(Player player, Clan clan, ClanDirection direction) {
        UpgradeOfferManager.Result result = plugin.getUpgradeOffers().chooseDirection(clan, player, direction);
        var messages = plugin.getMessages();
        switch (result) {
            case OK -> {
                plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                messages.send(player, "gui.direction.chosen", Map.of("direction", direction.displayName()));
                plugin.getGuiManager().openUpgrades(player, clan);
            }
            case NO_PERMISSION -> messages.sendDeny(player, "gui.upgrades.error.no-permission", Map.of());
            case LOCKED -> messages.sendDeny(player, "gui.direction.locked", Map.of("level",
                    String.valueOf(plugin.getConfig().getInt("direction.tiers.first", 5))));
            default -> {
                messages.send(player, "gui.direction.already");
                plugin.getGuiManager().openUpgrades(player, clan);
            }
        }
    }
}
