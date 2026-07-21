package me.lovelace.loveclans.model.quest.reward;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.QuestReward;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Deposits ItemsAdder currency directly into the clan chest's money slot (§2) - no physical item
 * transfer, and no bank ledger anymore (see ClanManager#depositRewardToChestAsync).
 */
public record ClanBankReward(String itemId, long amount) implements QuestReward {

    @Override
    public void giveReward(LoveClansPlugin plugin, Player player, Clan clan) {
        String chestCurrency = plugin.getClanManager().chestCurrencyItem();
        if (!chestCurrency.equals(itemId)) {
            plugin.getLogger().warning("Contract reward item '" + itemId + "' does not match the configured "
                    + "chest currency '" + chestCurrency + "' - depositing into the chest anyway, but check config.yml.");
        }
        plugin.getClanManager().depositRewardToChestAsync(clan, amount);
        if (player != null) {
            plugin.getMessages().send(player, "quest.reward.clan-bank",
                    Map.of("amount", String.valueOf(amount), "item", itemId));
        }
    }

    @Override
    public String getDisplayString() {
        return amount + "x " + itemId + " в сундук клана";
    }
}
