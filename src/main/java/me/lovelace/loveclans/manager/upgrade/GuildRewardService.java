package me.lovelace.loveclans.manager.upgrade;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.history.HistoryType;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.upgrade.GuildReward;
import me.lovelace.loveclans.storage.UpgradeStore;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the unlocked guild rewards. State is touched on the main thread only; unlocking is
 * insert-if-absent, so every reward is announced and paid exactly once.
 */
public final class GuildRewardService {

    private final LoveClansPlugin plugin;
    private final UpgradeStore store;
    private final Map<UUID, Set<GuildReward>> unlocked = new HashMap<>();
    private final Map<UUID, Map<GuildReward, Long>> unlockedAt = new HashMap<>();

    public GuildRewardService(LoveClansPlugin plugin, UpgradeStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load(Map<UUID, Map<String, Long>> rows) {
        unlocked.clear();
        unlockedAt.clear();
        rows.forEach((clanId, ids) -> {
            Set<GuildReward> set = EnumSet.noneOf(GuildReward.class);
            for (Map.Entry<String, Long> id : ids.entrySet()) {
                try {
                    GuildReward reward = GuildReward.valueOf(id.getKey());
                    set.add(reward);
                    unlockedAt.computeIfAbsent(clanId, k -> new HashMap<>()).put(reward, id.getValue());
                } catch (IllegalArgumentException ignored) {
                    // A reward that no longer exists (e.g. the retired "Экспансионист") is skipped.
                }
            }
            unlocked.put(clanId, set);
        });
    }

    public boolean has(Clan clan, GuildReward reward) {
        return clan != null && unlocked.getOrDefault(clan.id(), Set.of()).contains(reward);
    }

    public Set<GuildReward> of(Clan clan) {
        return clan == null ? Set.of() : Collections.unmodifiableSet(unlocked.getOrDefault(clan.id(), Set.of()));
    }

    public long unlockedAt(Clan clan, GuildReward reward) {
        return clan == null ? 0L : unlockedAt.getOrDefault(clan.id(), Map.of()).getOrDefault(reward, 0L);
    }

    /** Sum of the percentages of every unlocked reward that improves {@code bonus}. */
    public double percent(Clan clan, GuildReward.Bonus bonus) {
        double total = 0.0D;
        for (GuildReward reward : of(clan)) {
            if (reward.bonus() == bonus) {
                total += reward.percent();
            }
        }
        return total;
    }

    /** Unlocks the reward if it is new. Main thread. @return true when it was newly unlocked. */
    public boolean grant(Clan clan, GuildReward reward) {
        return grant(clan, reward, true);
    }

    public boolean grant(Clan clan, GuildReward reward, boolean announce) {
        if (clan == null || reward == null) {
            return false;
        }
        Set<GuildReward> set = unlocked.computeIfAbsent(clan.id(), k -> EnumSet.noneOf(GuildReward.class));
        if (!set.add(reward)) {
            return false;
        }
        long now = System.currentTimeMillis();
        unlockedAt.computeIfAbsent(clan.id(), k -> new HashMap<>()).put(reward, now);
        store.insertReward(clan.id(), reward.name(), now);
        if (announce) {
            plugin.getHistoryManager().add(clan.id(), HistoryType.GUILD_REWARD, null,
                    Map.of("reward", reward.name()));
            plugin.getSoundService().playClan(clan, "guild-reward");
            String name = plugin.getMessages().raw("gui.rewards.entry." + reward.langKey() + ".name");
            plugin.getClanManager().onlineMembers(clan).forEach(p ->
                    plugin.getMessages().send(p, "gui.rewards.unlocked", Map.of("reward", name)));
        }
        if (reward.bonus() == GuildReward.Bonus.INFLUENCE) {
            plugin.getClanManager().recalculateInfluenceAsync(clan);
        }
        return true;
    }

    // --- Hooks: each is cheap and idempotent, so callers may invoke them on every relevant event. ---

    public void checkMilestones(Clan clan) {
        checkMilestones(clan, true);
    }

    private void checkMilestones(Clan clan, boolean announce) {
        if (clan == null) {
            return;
        }
        var cfg = plugin.getConfig();
        if (clan.chestMoney() >= cfg.getLong("guild-rewards.rich-treasury", 100_000L)) grant(clan, GuildReward.RICH, announce);
        if (clan.members().size() >= cfg.getInt("guild-rewards.legion-members", 20)) grant(clan, GuildReward.LEGION, announce);
        long days = cfg.getLong("guild-rewards.veteran-days", 30L);
        if (System.currentTimeMillis() - clan.createdAt() >= days * 86_400_000L) grant(clan, GuildReward.VETERAN, announce);
        if (clan.warsWon() >= cfg.getInt("guild-rewards.conqueror-wars", 5)) grant(clan, GuildReward.CONQUEROR, announce);
        if (clan.warsWon() >= 1) grant(clan, GuildReward.FIRST_BLOOD, announce);
        if (clan.allianceCount() >= 1) grant(clan, GuildReward.DIPLOMAT, announce);
    }

    /** Silent catch-up for clans that earned rewards before the system existed. */
    public void backfill(Iterable<Clan> clans) {
        for (Clan clan : clans) {
            grant(clan, GuildReward.PIONEER, false);
            Set<GuildReward> before = new HashSet<>(of(clan));
            checkMilestones(clan, false);
            if (!before.equals(of(clan))) {
                plugin.getClanManager().recalculateInfluenceAsync(clan);
            }
        }
    }
}
