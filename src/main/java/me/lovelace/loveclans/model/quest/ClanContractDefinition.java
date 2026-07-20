package me.lovelace.loveclans.model.quest;

/** One selectable weekly clan contract ("обет"): a single objective plus its completion reward. */
public record ClanContractDefinition(
        String id,
        String displayName,
        String description,
        QuestObjective objective,
        QuestReward reward
) {
}
