package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.upgrade.UpgradeOption;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanDirection;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Upgrades hub (gui_gen v2.1, 54 slots): the level head in slot 0; the work zone holds the
 * "available upgrades" button (row 1), the five upgrades with their current effect (row 2) and the
 * development direction (row 3); Back and Close sit in the footer. Nothing here is spent by a click -
 * choosing happens in {@link ClanUpgradeOfferMenu} and {@link ClanDirectionMenu}.
 */
public final class ClanUpgradesMenu {
    private static final int SLOT_AVAILABLE = 22;
    private static final int SLOT_DIRECTION = 40;
    private static final int ROW_UPGRADES = 27;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int INVENTORY_SIZE = 54;

    private final LoveClansPlugin plugin;

    public ClanUpgradesMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    static String headFor(UpgradeOption option) {
        return switch (option) {
            case MEMBERS -> ItemBuilder.HEAD_MEMBERS;
            case CLAIM_SIZE -> ItemBuilder.HEAD_TERRITORIES;
            case EXPERIENCE -> ItemBuilder.HEAD_EXPERIENCE;
            case CHEST -> ItemBuilder.HEAD_EXPAND;
            case TAX -> ItemBuilder.HEAD_CHEST_MONEY;
        };
    }

    static String langKey(UpgradeOption option) {
        return option.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public void open(Player player, Clan clan) {
        // Restore "pending offers == points" quietly: points can also come from contracts or an admin.
        plugin.getUpgradeOffers().topUp(clan);

        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.UPGRADES, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.upgrades-title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame54(inventory);

        long currentExp = clan.experience();
        long expForCurrent = plugin.getClanManager().experienceForLevel(clan.level());
        long expForNext = plugin.getClanManager().experienceForLevel(clan.level() + 1);
        long required = expForNext - expForCurrent;

        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_LEVEL_INFO)
                .name(plugin.getMessages().component("gui.upgrades.level-info.name", player))
                .lore(plugin.getMessages().components("gui.upgrades.level-info.lore", Map.of(
                        "level", String.valueOf(clan.level()),
                        "current_exp", String.valueOf(currentExp),
                        "next_exp", String.valueOf(expForNext),
                        "bar", me.lovelace.loveclans.util.LoreBar.bar(required > 0 ? currentExp - expForCurrent : 1, Math.max(1, required))
                ), player))
                .lore(plugin.getMessages().components("gui.upgrades.points-info.lore",
                        Map.of("points", String.valueOf(clan.upgradePoints())), player))
                .build());

        renderAvailable(inventory, player, clan);
        renderUpgrades(inventory, player, clan);
        renderDirection(inventory, player, clan);

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private void renderAvailable(Inventory inventory, Player player, Clan clan) {
        int pending = plugin.getUpgradeOffers().pendingCount(clan);
        ItemBuilder builder = ItemBuilder.head(pending > 0 ? ItemBuilder.HEAD_QUEST : ItemBuilder.HEAD_INACTIVE)
                .name(pending > 0 ? plugin.getMessages().component("gui.upgrades.available.name", player)
                        : LoreBlocks.disabledName(plugin.getMessages(), player, "gui.upgrades.available.name"));
        if (pending > 0) {
            builder.lore(plugin.getMessages().components("gui.upgrades.available.lore",
                    Map.of("count", String.valueOf(pending)), player)).glow(true);
        } else {
            builder.lore(plugin.getMessages().components("gui.upgrades.available.head", player));
            builder.lore(LoreBlocks.blockedReason(plugin.getMessages(), player, "gui.upgrades.available.none", Map.of(),
                    LoreBlocks.hint(plugin.getMessages(), LoreBlocks.Click.LEFT, "gui.upgrades.available.action")));
        }
        inventory.setItem(SLOT_AVAILABLE, builder.build());
    }

    private void renderUpgrades(Inventory inventory, Player player, Clan clan) {
        UpgradeOption[] options = UpgradeOption.values();
        int[] slots = GuiFrames.contentRowSlots(ROW_UPGRADES, options.length);
        for (int i = 0; i < options.length; i++) {
            UpgradeOption option = options[i];
            String key = langKey(option);
            Map<String, String> values = plugin.getUpgradeOffers().describe(clan, option);
            boolean maxed = clan.upgradeLevel(option.upgrade()) >= plugin.getUpgradeOffers().maxLevel(option.upgrade());
            var messages = plugin.getMessages();
            String nameKey = "gui.upgrades.option." + key + ".name";
            ItemBuilder builder = ItemBuilder.head(maxed ? ItemBuilder.HEAD_INACTIVE : headFor(option))
                    .name(maxed ? messages.component("gui.upgrades.item.maxed-name", Map.of("name", messages.raw(nameKey)), player)
                            : messages.component(nameKey, player))
                    .lore(messages.component("gui.upgrades.option.subtitle", player))
                    .lore(Component.empty())
                    .lore(messages.component("gui.upgrades.option." + key + ".desc", player))
                    .lore(Component.empty())
                    .lore(messages.component("gui.upgrades.option.now-title", player))
                    .lore(messages.component("gui.upgrades.option." + key + ".now", values, player))
                    .lore(messages.components("gui.upgrades.option.level", values, player));
            if (maxed) {
                builder.lore(Component.empty()).lore(messages.component("gui.style.status.max", player));
            }
            inventory.setItem(slots[i], builder.build());
        }
    }

    private void renderDirection(Inventory inventory, Player player, Clan clan) {
        var messages = plugin.getMessages();
        int first = plugin.getConfig().getInt("direction.tiers.first", 5);
        boolean chosen = clan.direction().isPresent();
        boolean offered = plugin.getUpgradeOffers().pendingDirection(clan).isPresent();
        ItemBuilder builder = ItemBuilder.head(chosen || offered ? ItemBuilder.HEAD_SPIRIT_ABILITIES : ItemBuilder.HEAD_INACTIVE)
                .name(chosen || offered ? messages.component("gui.upgrades.direction-button.name", player)
                        : LoreBlocks.disabledName(messages, player, "gui.upgrades.direction-button.name"));
        if (chosen) {
            int tier = plugin.getClanBonuses().tier(clan);
            Map<String, String> values = new HashMap<>();
            values.put("direction", clan.direction().map(ClanDirection::displayName).orElse("?"));
            values.put("tier", String.valueOf(tier));
            values.put("bar", me.lovelace.loveclans.util.LoreBar.bar(tier, 3));
            builder.lore(messages.components("gui.upgrades.direction-button.chosen", values, player)).glow(true);
            if (tier < 3) {
                int next = plugin.getConfig().getInt(tier == 1 ? "direction.tiers.second" : "direction.tiers.third", tier == 1 ? 10 : 15);
                builder.lore(messages.component("gui.upgrades.direction-button.next-tier",
                        Map.of("level", String.valueOf(next)), player));
            } else {
                builder.lore(Component.empty()).lore(messages.component("gui.style.status.max", player));
            }
        } else if (offered) {
            builder.lore(messages.components("gui.upgrades.direction-button.pending", player)).glow(true);
        } else {
            builder.lore(messages.components("gui.upgrades.direction-button.head", player));
            builder.lore(LoreBlocks.blockedReason(messages, player, "gui.upgrades.direction-button.locked",
                    Map.of("level", String.valueOf(first)),
                    LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.upgrades.direction-button.action")));
        }
        inventory.setItem(SLOT_DIRECTION, builder.build());
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        Clan clan = plugin.getClanManager().getPlayerClan(player.getUniqueId()).orElse(null);
        if (clan == null) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (slot == SLOT_AVAILABLE) {
            if (plugin.getUpgradeOffers().pendingCount(clan) <= 0) {
                return;
            }
            if (!clan.hasPermission(player.getUniqueId(), ClanPermission.UPGRADE)) {
                plugin.getMessages().sendDeny(player, "gui.upgrades.error.no-permission", Map.of());
                return;
            }
            new ClanUpgradeOfferMenu(plugin).open(player, clan);
            return;
        }
        if (slot == SLOT_DIRECTION && plugin.getUpgradeOffers().pendingDirection(clan).isPresent()) {
            if (!clan.hasPermission(player.getUniqueId(), ClanPermission.UPGRADE)) {
                plugin.getMessages().sendDeny(player, "gui.upgrades.error.no-permission", Map.of());
                return;
            }
            new ClanDirectionMenu(plugin).open(player, clan);
        }
    }
}
