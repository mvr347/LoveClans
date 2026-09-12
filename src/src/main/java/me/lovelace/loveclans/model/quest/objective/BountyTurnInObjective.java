package me.lovelace.loveclans.model.quest.objective;

import me.lovelace.loveclans.model.quest.QuestObjective;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Progress only moves via an explicit "bounty_turn_in" marker in eventData — populated by the
 * reflective LoveHunt {@code BountyClaimEvent} bridge when a CLAN-type bounty on a hostile clan's
 * member is claimed by a hunter belonging to the questing clan.
 */
public record BountyTurnInObjective(int targetAmount, String displayNameFormat) implements QuestObjective {

    @Override
    public String getType() {
        return "BOUNTY_TURN_IN";
    }

    @Override
    public Component getDisplayName(Player player, int currentProgress) {
        String formatted = displayNameFormat
                .replace("{current_progress}", String.valueOf(currentProgress))
                .replace("{target_amount}", String.valueOf(targetAmount));
        return MiniMessage.miniMessage().deserialize(formatted);
    }

    @Override
    public int getTargetAmount() {
        return targetAmount;
    }

    @Override
    public boolean isCompleted(int currentProgress) {
        return currentProgress >= targetAmount;
    }

    @Override
    public int updateProgress(UUID clanId, UUID playerId, int currentProgress, Map<String, Object> eventData) {
        return Boolean.TRUE.equals(eventData.get("bounty_turn_in")) ? 1 : 0;
    }

    @Override
    public QuestObjective scaled(double multiplier) {
        return new BountyTurnInObjective(Math.max(1, (int) Math.round(targetAmount * multiplier)), displayNameFormat);
    }
}
