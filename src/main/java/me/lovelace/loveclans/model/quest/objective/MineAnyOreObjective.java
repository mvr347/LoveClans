package me.lovelace.loveclans.model.quest.objective;

import me.lovelace.loveclans.model.quest.QuestObjective;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** Matches any ore-like block (vanilla "_ORE" blocks plus ancient debris), not one specific material. */
public record MineAnyOreObjective(int targetAmount, String displayNameFormat) implements QuestObjective {

    public static boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    @Override
    public String getType() {
        return "MINE_ANY_ORE";
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
        if (eventData.get("block_type") instanceof Material minedType && isOre(minedType)) {
            return 1;
        }
        return 0;
    }
}
