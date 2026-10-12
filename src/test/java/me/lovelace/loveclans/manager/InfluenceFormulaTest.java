package me.lovelace.loveclans.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceFormulaTest {
    private static org.bukkit.configuration.ConfigurationSection shippedConfig() {
        var in = InfluenceFormulaTest.class.getResourceAsStream("/config.yml");
        assertNotNull(in);
        var section = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getConfigurationSection("influence");
        assertNotNull(section, "influence section must ship in config.yml");
        return section;
    }

    /** members, active, level, upgrades, territories, spirit, treasury, activity, alliances, wars/sieges/raids W-L, freshness, recent W-L */
    private static InfluenceFormula.Inputs clan(int members, int active, int level, int upgrades, int territories,
                                                int spirit, long treasury, long activity, int alliances,
                                                int won, int lost, int recentWins, int recentLosses) {
        return new InfluenceFormula.Inputs(members, active, level, upgrades, territories, spirit, treasury, activity,
                alliances, won, lost, 0, 0, 0, 0, 1.0, 1.0, recentWins, recentLosses);
    }

    @Test
    void smallActiveWinningClanBeatsBigInactiveLosingOne() {
        var cfg = shippedConfig();
        var small = InfluenceFormula.compute(clan(4, 4, 3, 3, 1, 2, 20_000, 800, 1, 6, 2, 6, 2), cfg);
        var big = InfluenceFormula.compute(clan(20, 5, 3, 3, 1, 2, 20_000, 200, 0, 2, 6, 2, 6), cfg);
        System.out.println("small=" + small + " big=" + big);
        assertTrue(small.value() > big.value() * 2, "small " + small.value() + " vs big " + big.value());
    }

    @Test
    void inactiveMembersAddAlmostNothing() {
        var cfg = shippedConfig();
        var withDormant = InfluenceFormula.compute(clan(30, 4, 3, 3, 1, 2, 0, 0, 0, 0, 0, 0, 0), cfg);
        var withoutDormant = InfluenceFormula.compute(clan(4, 4, 3, 3, 1, 2, 0, 0, 0, 0, 0, 0, 0), cfg);
        assertTrue(withDormant.value() < withoutDormant.value(),
                "dormant members must not raise power: " + withDormant.value() + " vs " + withoutDormant.value());
    }

    @Test
    void fewFightsDoNotChangeForm() {
        var result = InfluenceFormula.compute(clan(4, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0), shippedConfig());
        assertEquals(1.0, result.form(), 1e-9, "below form-min-conflicts the form stays neutral");
    }

    @Test
    void formIsBoundedAndNeverNegativeTotal() {
        var cfg = shippedConfig();
        var winning = InfluenceFormula.compute(clan(4, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 50, 0), cfg);
        var losing = InfluenceFormula.compute(clan(4, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50), cfg);
        assertTrue(winning.form() <= 1.8 && winning.form() > 1.5);
        assertTrue(losing.form() >= 0.6 && losing.form() < 0.7);
        var crushed = InfluenceFormula.compute(clan(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 100, 0, 50), cfg);
        assertEquals(0L, crushed.value(), "influence is never negative");
    }

    @Test
    void fadedLossesCostLessThanFreshOnes() {
        var cfg = shippedConfig();
        var fresh = new InfluenceFormula.Inputs(4, 4, 3, 3, 1, 2, 0, 0, 0, 0, 4, 0, 0, 0, 0, 1.0, 1.0, 0, 0);
        var faded = new InfluenceFormula.Inputs(4, 4, 3, 3, 1, 2, 0, 0, 0, 0, 4, 0, 0, 0, 0, 1.0, 0.25, 0, 0);
        assertTrue(InfluenceFormula.compute(faded, cfg).value() > InfluenceFormula.compute(fresh, cfg).value());
    }

    @Test
    void defaultsWorkWithoutConfig() {
        assertTrue(InfluenceFormula.compute(clan(4, 4, 3, 3, 1, 2, 0, 0, 0, 0, 0, 0, 0), null).value() > 0);
    }
}
