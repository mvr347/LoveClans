package me.lovelace.loveclans.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.achievements.Achievements;

import java.util.UUID;

/**
 * Bridge to LoveCore's {@link Achievements}. Looked up on every call; an older LoveCore without the contract
 * ({@link LinkageError}) or no service simply means nothing is recorded. Main thread only.
 */
public final class AchievementsHook {
    private AchievementsHook() {
    }

    public static void unlock(UUID player, String id) {
        try {
            LoveCore.service(Achievements.class).ifPresent(service -> service.unlock(player, id));
        } catch (LinkageError missingContract) {
            // an older LoveCore: nothing to record
        }
    }

    public static void progress(UUID player, String key, double delta) {
        try {
            LoveCore.service(Achievements.class).ifPresent(service -> service.progress(player, key, delta));
        } catch (LinkageError missingContract) {
            // an older LoveCore: nothing to record
        }
    }
}
