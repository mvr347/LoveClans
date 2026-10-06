package me.lovelace.loveclans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BannerReplacementPenaltyTest {

    @Test
    void spiritLevelNeverDropsBelowOne() {
        assertEquals(4, BannerReplacementPenalty.spiritLevel(5, 1));
        assertEquals(1, BannerReplacementPenalty.spiritLevel(1, 1));
        assertEquals(1, BannerReplacementPenalty.spiritLevel(2, 5));
        assertEquals(5, BannerReplacementPenalty.spiritLevel(5, -3), "a negative config value is no bonus");
    }

    @Test
    void spiritEnergyStaysBelowTheNextLevelThreshold() {
        assertEquals(2999L, BannerReplacementPenalty.spiritEnergy(5000, 1, 3000));
        assertEquals(1200L, BannerReplacementPenalty.spiritEnergy(1200, 2, 6000));
        assertEquals(0L, BannerReplacementPenalty.spiritEnergy(0, 1, 3000));
    }

    @Test
    void experienceLossIsAShareOfTheCurrentLevelOnly() {
        assertEquals(50L, BannerReplacementPenalty.experienceLoss(1500, 1000, 10));
        assertEquals(0L, BannerReplacementPenalty.experienceLoss(900, 1000, 10), "below the level start nothing is lost");
        assertEquals(500L, BannerReplacementPenalty.experienceLoss(1500, 1000, 400), "percent is capped at 100");
        assertEquals(0L, BannerReplacementPenalty.experienceLoss(1500, 1000, -5));
    }

    @Test
    void replacementCostIsHalfOfTheBannerRoundedUp() {
        assertEquals(5_000L, BannerReplacementPenalty.replacementCost(10_000, 50));
        assertEquals(6L, BannerReplacementPenalty.replacementCost(11, 50));
        assertEquals(0L, BannerReplacementPenalty.replacementCost(10_000, 0));
        assertEquals(10_000L, BannerReplacementPenalty.replacementCost(10_000, 150));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shippedConfigHasTheReplacementKeys() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/config.yml")) {
            java.util.Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(in);
            java.util.Map<String, Object> banner = (java.util.Map<String, Object>)
                    ((java.util.Map<String, Object>) ((java.util.Map<String, Object>) root.get("clans")).get("banner"));
            assertEquals(50, banner.get("replacement-cost-percent"));
            assertEquals(1, banner.get("replacement-spirit-levels"));
            assertEquals(10, banner.get("replacement-exp-percent"));
        }
    }
}
