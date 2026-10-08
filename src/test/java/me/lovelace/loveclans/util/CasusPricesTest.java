package me.lovelace.loveclans.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CasusPricesTest {
    @Test
    void siegeUsesExplicitOverrideThenMultiplier() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("siege-cost-multiplier", 2.5);
        yaml.set("just-reasons.revenge_war.cost", 50);
        yaml.set("siege.just-reasons.revenge_war.cost", 150);
        yaml.set("frivolous-reasons.insult.cost", 2000);

        assertEquals(50, CasusPrices.cost(yaml, true, "revenge_war", true));
        assertEquals(150, CasusPrices.cost(yaml, true, "revenge_war", false));
        assertEquals(5000, CasusPrices.cost(yaml, false, "insult", false)); // 2000 x 2.5, no override
    }

    @Test
    void missingConfigFallsBackToDefaults() {
        assertEquals(25, CasusPrices.cost(null, true, "unpaid_tribute", true));
        assertEquals(21, CasusPrices.ttlDays(null, "unpaid_tribute"));
        assertEquals(14, CasusPrices.ttlDays(null, "revenge_war"));
    }
}
