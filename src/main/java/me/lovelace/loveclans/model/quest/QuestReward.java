package me.lovelace.loveclans.model.quest;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.entity.Player;

public interface QuestReward {
    /**
     * Applies the reward to the given player and/or clan.
     * @param plugin The LoveClansPlugin instance.
     * @param player The player who is claiming the reward (can be null if reward is purely for clan).
     * @param clan The clan receiving the reward.
     */
    void giveReward(LoveClansPlugin plugin, Player player, Clan clan);

    /**
     * Gets a display string for the reward.
     * @return The display string.
     */
    String getDisplayString();
}
