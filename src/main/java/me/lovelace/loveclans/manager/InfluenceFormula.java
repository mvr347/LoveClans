package me.lovelace.loveclans.manager;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Clan power ("Влияние") as a pure function, so the weights can be tested without a server.
 * <p>
 * Size alone must not decide it: a small active clan that keeps winning should outrank a big clan whose members
 * have not logged in for weeks and which keeps losing. Hence three layers: a base made of many parameters (active
 * members count fully, inactive ones almost not at all), a battle record in which both wins and losses fade, and a
 * recent-form multiplier plus a share-of-active-members multiplier on top of both.
 */
public final class InfluenceFormula {
    private InfluenceFormula() {
    }

    /** Everything the formula reads, collected on the main thread. */
    public record Inputs(int members, int activeMembers, int level, int upgradeLevels, int territories,
                         int spiritLevel, long treasury, long monthlyActivity, int alliances,
                         int warsWon, int warsLost, int siegesWon, int siegesLost, int raidsWon, int raidsLost,
                         double winFreshness, double lossFreshness, int recentWins, int recentLosses) {
    }

    /** Result with the parts the UI shows. */
    public record Result(long value, long base, long battle, double form, double activeShare) {
    }

    public static Result compute(Inputs in, ConfigurationSection cfg) {
        double base = 0;
        int inactive = Math.max(0, in.members() - in.activeMembers());
        base += (double) in.activeMembers() * w(cfg, "active-member-weight", 10);
        base += (double) inactive * w(cfg, "inactive-member-weight", 1);
        base += (double) in.level() * w(cfg, "level-weight", 5);
        base += (double) in.upgradeLevels() * w(cfg, "upgrade-point-weight", 20);
        base += (double) in.territories() * w(cfg, "territory-weight", 15);
        base += (double) in.spiritLevel() * w(cfg, "spirit-weight", 8);
        // Logarithmic: money should help, but not buy the top spot.
        base += Math.log10(1.0 + Math.max(0L, in.treasury())) * w(cfg, "wealth-weight", 12);
        base += Math.min(w(cfg, "activity-cap", 150), Math.max(0L, in.monthlyActivity()) * w(cfg, "activity-weight", 0.05));
        base += (double) in.alliances() * w(cfg, "alliance-weight", 10);

        double wins = in.warsWon() * w(cfg, "war-win-weight", 50)
                + in.siegesWon() * w(cfg, "siege-win-weight", 75)
                + in.raidsWon() * w(cfg, "raid-win-weight", 30);
        double losses = in.warsLost() * w(cfg, "war-loss-weight", 25)
                + in.siegesLost() * w(cfg, "siege-loss-weight", 40)
                + in.raidsLost() * w(cfg, "raid-loss-weight", 15);
        double battle = wins * clamp01(in.winFreshness()) - losses * clamp01(in.lossFreshness());

        // Recent form: smoothed win rate over the decay window, so one lucky fight does not swing the whole score.
        int recentTotal = in.recentWins() + in.recentLosses();
        double form = 1.0;
        if (recentTotal >= (int) w(cfg, "form-min-conflicts", 3)) {
            double winRate = (in.recentWins() + 1.0) / (recentTotal + 2.0);
            double lo = w(cfg, "form-min", 0.6);
            double hi = w(cfg, "form-max", 1.8);
            form = lo + (hi - lo) * winRate;
        }

        double activeShare = in.members() <= 0 ? 1.0
                : (1.0 - w(cfg, "inactive-penalty", 0.5)) + w(cfg, "inactive-penalty", 0.5) * in.activeMembers() / in.members();

        long value = Math.max(0L, Math.round((base + battle) * form * activeShare));
        return new Result(value, Math.round(base), Math.round(battle), form, activeShare);
    }

    private static double w(ConfigurationSection cfg, String key, double def) {
        return cfg == null ? def : cfg.getDouble(key, def);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
