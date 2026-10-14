package me.lovelace.loveclans.manager.upgrade;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanDirection;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.upgrade.GuildReward;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * The one place that turns upgrades, the development direction and guild rewards into percentages.
 * Everything that applies a clan bonus reads it from here, so two sources can never be added up
 * (or forgotten) in two different ways.
 */
public final class ClanBonusService {

    private final LoveClansPlugin plugin;

    public ClanBonusService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // --- Direction ---

    /** 0 when no direction was chosen, otherwise 1..3. */
    public int tier(Clan clan) {
        if (clan == null || clan.direction().isEmpty()) {
            return 0;
        }
        return ClanDirection.tier(clan.level(),
                cfg().getInt("direction.tiers.first", 5),
                cfg().getInt("direction.tiers.second", 10),
                cfg().getInt("direction.tiers.third", 15));
    }

    public boolean is(Clan clan, ClanDirection direction) {
        return clan != null && clan.direction().map(d -> d == direction).orElse(false);
    }

    /** Value of a per-tier list from config for the clan's current tier; 0 below tier I. */
    public double directionValue(Clan clan, ClanDirection direction, String path) {
        return directionValueAt(direction, path, is(clan, direction) ? tier(clan) : 0);
    }

    public double directionValueAt(ClanDirection direction, String path, int tier) {
        if (tier <= 0) {
            return 0.0D;
        }
        List<Double> values = cfg().getDoubleList("direction." + direction.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "." + path);
        if (values.isEmpty()) {
            return 0.0D;
        }
        return values.get(Math.min(tier, values.size()) - 1);
    }

    // --- Percentages ---

    public double experiencePercent(Clan clan) {
        if (clan == null) return 0.0D;
        return clan.upgradeLevel(ClanUpgrade.EXPERIENCE) * cfg().getDouble("limits.experience-percent-per-upgrade", 5.0D)
                + plugin.getGuildRewards().percent(clan, GuildReward.Bonus.EXPERIENCE);
    }

    public double taxDiscountPercent(Clan clan) {
        if (clan == null) return 0.0D;
        double percent = clan.upgradeLevel(ClanUpgrade.TAX) * cfg().getDouble("limits.tax-discount-percent-per-level", 5.0D)
                + plugin.getGuildRewards().percent(clan, GuildReward.Bonus.TAX_DISCOUNT);
        return Math.min(90.0D, percent);
    }

    public double incomePercent(Clan clan) {
        return clan == null ? 0.0D : plugin.getGuildRewards().percent(clan, GuildReward.Bonus.INCOME);
    }

    public double influencePercent(Clan clan) {
        return clan == null ? 0.0D : plugin.getGuildRewards().percent(clan, GuildReward.Bonus.INFLUENCE);
    }

    /** Extra damage dealt to an enemy clan's player, depending on where the attacker stands. */
    public double damagePercent(Clan clan, boolean onOwnLand) {
        if (clan == null) return 0.0D;
        var rewards = plugin.getGuildRewards();
        if (onOwnLand) {
            return directionValue(clan, ClanDirection.MILITARY, "damage-own-land-percent")
                    + rewards.percent(clan, GuildReward.Bonus.DAMAGE_OWN_LAND);
        }
        return rewards.percent(clan, GuildReward.Bonus.DAMAGE_OFFENSE);
    }

    /** Share of incoming damage from an enemy clan that is absorbed. */
    public double resistancePercent(Clan clan, boolean onOwnLand) {
        if (clan == null) return 0.0D;
        var rewards = plugin.getGuildRewards();
        double percent = rewards.percent(clan, GuildReward.Bonus.RESISTANCE);
        if (onOwnLand) {
            percent += directionValue(clan, ClanDirection.MILITARY, "resistance-own-land-percent")
                    + rewards.percent(clan, GuildReward.Bonus.RESISTANCE_OWN_LAND);
        }
        return percent;
    }

    /** Shortens the cooldown of raids and sieges this clan starts. */
    public double cooldownReductionPercent(Clan clan) {
        return directionValue(clan, ClanDirection.MILITARY, "cooldown-reduction-percent");
    }

    // --- Hardening of conflicts against a tier III military clan ---

    private boolean hardened(Clan defender) {
        return is(defender, ClanDirection.MILITARY) && tier(defender) >= 3;
    }

    /** Multiplier for how long attackers must hold the camps of {@code defender}. */
    public double siegeHoldMultiplier(Clan defender) {
        return hardened(defender) ? 1.0D + cfg().getDouble("direction.military.hardening.siege-hold-extra-percent", 25.0D) / 100.0D : 1.0D;
    }

    /** Multiplier for how fast a raid captures a base of {@code defender}. */
    public double raidCaptureMultiplier(Clan defender) {
        return hardened(defender) ? Math.max(0.1D, 1.0D - cfg().getDouble("direction.military.hardening.raid-capture-slowdown-percent", 20.0D) / 100.0D) : 1.0D;
    }

    /** How many more attackers must be online to start a conflict against {@code defender}. */
    public int extraAttackersOnline(Clan defender) {
        return hardened(defender) ? cfg().getInt("direction.military.hardening.extra-attackers-online", 1) : 0;
    }
}
