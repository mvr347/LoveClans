package me.lovelace.loveclans.model.quest.reward;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.QuestReward; // Added import
import org.bukkit.entity.Player;

import java.util.Map;

public record PlayerXPReward(int amount) implements QuestReward {

    @Override
    public void giveReward(LoveClansPlugin plugin, Player player, Clan clan) {
        if (player != null) {
            player.giveExp(amount);
            plugin.getMessages().send(player, "quest.reward.player_xp", Map.of("amount", String.valueOf(amount)));
        }
    }

    @Override
    public String getDisplayString() {
        return amount + " опыта игрока";
    }
}
