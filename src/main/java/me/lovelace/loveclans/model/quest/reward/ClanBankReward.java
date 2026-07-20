package me.lovelace.loveclans.model.quest.reward;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.QuestReward;
import org.bukkit.entity.Player;

import java.util.Map;

/** Deposits ItemsAdder currency directly into the clan bank ledger (no physical item transfer). */
public record ClanBankReward(String itemId, long amount) implements QuestReward {

    @Override
    public void giveReward(LoveClansPlugin plugin, Player player, Clan clan) {
        plugin.getClanManager().depositRewardToBankAsync(clan, itemId, amount);
        if (player != null) {
            plugin.getMessages().send(player, "quest.reward.clan-bank",
                    Map.of("amount", String.valueOf(amount), "item", itemId));
        }
    }

    @Override
    public String getDisplayString() {
        return amount + "x " + itemId + " в банк клана";
    }
}
