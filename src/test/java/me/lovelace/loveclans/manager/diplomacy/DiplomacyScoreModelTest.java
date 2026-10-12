package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.diplomacy.ClanPact;
import me.lovelace.loveclans.model.diplomacy.ClanRelationRecord;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import me.lovelace.loveclans.model.diplomacy.PactType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiplomacyScoreModelTest {

    @Test
    void testTierThresholds() {
        // -100..-60: HOSTILE
        assertEquals(DiplomacyTier.HOSTILE, DiplomacyTier.fromScore(-100, false));
        assertEquals(DiplomacyTier.HOSTILE, DiplomacyTier.fromScore(-60, false));

        // -59..-20: UNFRIENDLY
        assertEquals(DiplomacyTier.UNFRIENDLY, DiplomacyTier.fromScore(-59, false));
        assertEquals(DiplomacyTier.UNFRIENDLY, DiplomacyTier.fromScore(-20, false));

        // -19..+19: NEUTRAL
        assertEquals(DiplomacyTier.NEUTRAL, DiplomacyTier.fromScore(-19, false));
        assertEquals(DiplomacyTier.NEUTRAL, DiplomacyTier.fromScore(0, false));
        assertEquals(DiplomacyTier.NEUTRAL, DiplomacyTier.fromScore(19, false));

        // +20..+59: CORDIAL
        assertEquals(DiplomacyTier.CORDIAL, DiplomacyTier.fromScore(20, false));
        assertEquals(DiplomacyTier.CORDIAL, DiplomacyTier.fromScore(59, false));

        // +60..+89: FRIENDLY
        assertEquals(DiplomacyTier.FRIENDLY, DiplomacyTier.fromScore(60, false));
        assertEquals(DiplomacyTier.FRIENDLY, DiplomacyTier.fromScore(89, false));

        // +90..+100 without pact is FRIENDLY
        assertEquals(DiplomacyTier.FRIENDLY, DiplomacyTier.fromScore(90, false));
        assertEquals(DiplomacyTier.FRIENDLY, DiplomacyTier.fromScore(100, false));

        // +90..+100 WITH pact is ALLIED
        assertEquals(DiplomacyTier.ALLIED, DiplomacyTier.fromScore(90, true));
        assertEquals(DiplomacyTier.ALLIED, DiplomacyTier.fromScore(100, true));
    }

    @Test
    void testLegacyRelationMapping() {
        assertEquals(DiplomacyRelation.ALLY, DiplomacyTier.ALLIED.toLegacyRelation());
        assertEquals(DiplomacyRelation.ENEMY, DiplomacyTier.HOSTILE.toLegacyRelation());
        assertEquals(DiplomacyRelation.NEUTRAL, DiplomacyTier.NEUTRAL.toLegacyRelation());
        assertEquals(DiplomacyRelation.NEUTRAL, DiplomacyTier.CORDIAL.toLegacyRelation());
        assertEquals(DiplomacyRelation.NEUTRAL, DiplomacyTier.FRIENDLY.toLegacyRelation());
        assertEquals(DiplomacyRelation.NEUTRAL, DiplomacyTier.UNFRIENDLY.toLegacyRelation());
    }

    @Test
    void testScoreClampingAndTrend() {
        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();

        ClanRelationRecord record = new ClanRelationRecord(src, tgt, 150, -150, 1000L, 1000L);
        assertEquals(100, record.score(), "Score should clamp to 100");
        assertEquals(-100, record.score24hAgo(), "Baseline should clamp to -100");

        // Trend calculation
        ClanRelationRecord trendingUp = new ClanRelationRecord(src, tgt, 25, 10, 1000L, 1000L);
        assertEquals(15, trendingUp.trend());
        assertEquals("▲▲▲", trendingUp.trendUnicode());

        ClanRelationRecord moderateUp = new ClanRelationRecord(src, tgt, 18, 10, 1000L, 1000L);
        assertEquals("▲▲", moderateUp.trendUnicode());

        ClanRelationRecord slightUp = new ClanRelationRecord(src, tgt, 12, 10, 1000L, 1000L);
        assertEquals("▲", slightUp.trendUnicode());

        ClanRelationRecord steady = new ClanRelationRecord(src, tgt, 10, 10, 1000L, 1000L);
        assertEquals("—", steady.trendUnicode());

        ClanRelationRecord trendingDown = new ClanRelationRecord(src, tgt, -10, 10, 1000L, 1000L);
        assertEquals(-20, trendingDown.trend());
        assertEquals("▼▼▼", trendingDown.trendUnicode());
    }

    @Test
    void testClanRelationIntegration() {
        Clan clan = new Clan(UUID.randomUUID(), "Test", "TEST", "<white>", "Desc",
                Material.WHITE_BANNER, 1, 0, 0, 1, ClanSpirit.fresh(), System.currentTimeMillis(), true, null);

        UUID targetClanId = UUID.randomUUID();

        // Default: score 0, tier NEUTRAL, relation NEUTRAL
        assertEquals(0, clan.relationScore(targetClanId));
        assertEquals(DiplomacyTier.NEUTRAL, clan.tierTo(targetClanId));
        assertEquals(DiplomacyRelation.NEUTRAL, clan.relationTo(targetClanId));

        // Set score to -70 -> tier HOSTILE, relation ENEMY
        clan.setRelation(ClanRelationRecord.of(clan.id(), targetClanId, -70));
        assertEquals(-70, clan.relationScore(targetClanId));
        assertEquals(DiplomacyTier.HOSTILE, clan.tierTo(targetClanId));
        assertEquals(DiplomacyRelation.ENEMY, clan.relationTo(targetClanId));

        // Add alliance pact and score 95 -> tier ALLIED, relation ALLY
        clan.addPact(targetClanId, PactType.ALLIANCE);
        clan.setRelation(ClanRelationRecord.of(clan.id(), targetClanId, 95));
        assertTrue(clan.hasPactWith(targetClanId, PactType.ALLIANCE));
        assertEquals(DiplomacyTier.ALLIED, clan.tierTo(targetClanId));
        assertEquals(DiplomacyRelation.ALLY, clan.relationTo(targetClanId));

        // Breaking pact leaves score high but tier drops to FRIENDLY, relation to NEUTRAL
        clan.removePact(targetClanId, PactType.ALLIANCE);
        assertFalse(clan.hasPactWith(targetClanId, PactType.ALLIANCE));
        assertEquals(DiplomacyTier.FRIENDLY, clan.tierTo(targetClanId));
        assertEquals(DiplomacyRelation.NEUTRAL, clan.relationTo(targetClanId));
    }
}
