package me.lovelace.loveclans.model.quest;

import java.util.UUID;

/**
 * A clan's progress on one active contract slot (§1.1) - a clan can have one active WEEKLY and one
 * active DAILY contract at the same time, tracked as separate rows/instances. {@code scaledTarget}
 * and {@code scaledRewardXp} are snapshots taken at selection time (§1.2's difficulty multiplier
 * applied against the clan's member count then), so they stay stable even if members join/leave
 * mid-contract.
 */
public record ClanQuestProgress(
        UUID clanId,
        ContractType type,
        String questId,
        int scaledTarget,
        long scaledRewardXp,
        int progress,
        boolean completed,
        boolean claimed,
        long startedAt,
        long expiresAt
) {
    public ClanQuestProgress withProgress(int progress) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt);
    }

    public ClanQuestProgress withCompleted(boolean completed) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt);
    }

    public ClanQuestProgress withClaimed(boolean claimed) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt);
    }
}
