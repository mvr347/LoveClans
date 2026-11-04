package me.lovelace.loveclans.model.quest;

import java.util.UUID;

/**
 * A clan's progress on one active contract slot (§1.1) - a clan can have one active WEEKLY and one
 * active MONTHLY contract at the same time, tracked as separate rows/instances. {@code scaledTarget}
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
        long expiresAt,
        int failurePenaltyPercent
) {
    /** Existing API callers and saved vows keep the original 80% failure terms. */
    public ClanQuestProgress(UUID clanId, ContractType type, String questId, int scaledTarget,
            long scaledRewardXp, int progress, boolean completed, boolean claimed, long startedAt, long expiresAt) {
        this(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt, 80);
    }

    public long failurePenaltyXp() {
        return Math.max(0L, Math.round(scaledRewardXp * (Math.max(0, failurePenaltyPercent) / 100.0)));
    }

    public ClanQuestProgress withProgress(int progress) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt, failurePenaltyPercent);
    }

    public ClanQuestProgress withCompleted(boolean completed) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt, failurePenaltyPercent);
    }

    public ClanQuestProgress withClaimed(boolean claimed) {
        return new ClanQuestProgress(clanId, type, questId, scaledTarget, scaledRewardXp, progress, completed, claimed, startedAt, expiresAt, failurePenaltyPercent);
    }
}
