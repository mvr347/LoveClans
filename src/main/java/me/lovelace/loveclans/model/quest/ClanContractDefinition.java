package me.lovelace.loveclans.model.quest;

/**
 * One catalog entry from the weekly (20) or daily (40) contract pool (§1.1). {@code baseRewardXp}
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
