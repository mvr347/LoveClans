package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.upgrade.GuildReward;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Guild rewards (gui_gen v2.1, 45 slots: header, Row1 glass, two content rows, one footer row). Earned
 * rewards are lit and show their date, the others are grey with a single red line naming the condition.
 */
public final class ClanGuildRewardsMenu {
    private static final int SIZE = 45;
    private static final int SLOT_INFO = 0;
    private static final int ROW_ONE = 18;
    private static final int ROW_TWO = 27;
    private static final int SLOT_BACK = 43;
    private static final int SLOT_CLOSE = 44;

    private final LoveClansPlugin plugin;

    public ClanGuildRewardsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private static String headFor(GuildReward reward) {
        return switch (reward.bonus()) {
            case EXPERIENCE -> ItemBuilder.HEAD_EXPERIENCE;
            case DAMAGE_OWN_LAND, DAMAGE_OFFENSE -> ItemBuilder.HEAD_PERK_WARRIOR;
            case RESISTANCE_OWN_LAND, RESISTANCE -> ItemBuilder.HEAD_ABILITY_SANCTUARY;
            case TAX_DISCOUNT, INCOME -> ItemBuilder.HEAD_CHEST_MONEY;
            case INFLUENCE -> ItemBuilder.HEAD_DIPLOMACY;
        };
    }

    private Map<String, String> conditionValues(GuildReward reward) {
        var cfg = plugin.getConfig();
        Map<String, String> values = new HashMap<>();
        switch (reward) {
            case RICH -> values.put("amount", CoinFormat.format(cfg.getLong("guild-rewards.rich-treasury", 100_000L)));
            case LEGION -> values.put("amount", String.valueOf(cfg.getInt("guild-rewards.legion-members", 20)));
            case VETERAN -> values.put("amount", String.valueOf(cfg.getLong("guild-rewards.veteran-days", 30L)));
            case CONQUEROR -> values.put("amount", String.valueOf(cfg.getInt("guild-rewards.conqueror-wars", 5)));
            default -> { }
        }
        return values;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.REWARDS, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().component(
                "gui.rewards.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);
        GuiFrames.fillFrame45(inventory);

        var service = plugin.getGuildRewards();
        GuildReward[] rewards = GuildReward.values();
        int done = service.of(clan).size();
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(plugin.getMessages().component("gui.rewards.info.name", player))
                .lore(plugin.getMessages().component("gui.rewards.info.lore",
                        Map.of("done", String.valueOf(done), "total", String.valueOf(rewards.length)), player))
                .build());

        int firstRow = (rewards.length + 1) / 2;
        int[] top = GuiFrames.contentRowSlots(ROW_ONE, firstRow);
        int[] bottom = GuiFrames.contentRowSlots(ROW_TWO, rewards.length - firstRow);
        for (int i = 0; i < rewards.length; i++) {
            GuildReward reward = rewards[i];
            int slot = i < firstRow ? top[i] : bottom[i - firstRow];
            inventory.setItem(slot, button(player, clan, reward));
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack button(Player player, Clan clan, GuildReward reward) {
        var messages = plugin.getMessages();
        String base = "gui.rewards.entry." + reward.langKey();
        boolean has = plugin.getGuildRewards().has(clan, reward);
        ItemBuilder builder = ItemBuilder.head(has ? headFor(reward) : ItemBuilder.HEAD_INACTIVE)
                .name(messages.component(base + ".name", player))
                .lore(messages.component(base + ".bonus", player));
        if (has) {
            long at = plugin.getGuildRewards().unlockedAt(clan, reward);
            builder.lore(messages.component("gui.rewards.state.done",
                    Map.of("date", at > 0 ? messages.formatDate(at) : "-"), player)).glow(true);
        } else {
            builder.lore(messages.reason(base + ".condition", conditionValues(reward), player));
        }
        return builder.build();
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_BACK) {
            plugin.getGuiManager().openModifiers(player, clan);
        }
    }
}
