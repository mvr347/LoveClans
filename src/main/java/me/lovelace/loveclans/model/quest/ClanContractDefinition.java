package me.lovelace.loveclans.model.quest;

/**
 * One catalog entry of the weekly pool, or of the monthly pool derived from it (§1.1). {@code baseRewardXp}
 * and the objective's base target are unscaled - {@link me.lovelace.loveclans.manager.ContractManager}
 * applies the clan-size difficulty multiplier (§1.2) at selection time.
 */
public record ClanContractDefinition(
        String id,
        ContractType type,
        String displayName,
        String description,
        QuestObjective objective,
        long baseRewardXp
) {
}
