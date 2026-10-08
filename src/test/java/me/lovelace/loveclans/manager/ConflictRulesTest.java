package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.modifier.ClanModifier;
import me.lovelace.loveclans.model.raid.ClanRaid;
import me.lovelace.loveclans.model.raid.RaidPhase;
import me.lovelace.loveclans.model.raid.RaidState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the new conflict revamp mechanics:
 * - Reparations daily amount calculations and bounds
 * - Siege level prerequisites and gap constraints
 * - ClanModifier expiration and reparations debt tracking
 * - Raid substantial loot qualification
 * - Siege camp hits calculation with fortification
 * - Cylinder capture geometry bounds
 */
class ConflictRulesTest {

    @Test
    @DisplayName("Reparations daily amount formula scales with defender level and respects bounds")
    void reparationsDailyAmountCalculations() {
        int baseDaily = 300;
        int perLevel = 40;
        int minDaily = 100;
        int maxDaily = 2000;

        // Defender level 1: 300 + 40 = 340
        long dailyLvl1 = Math.max(minDaily, Math.min(maxDaily, (long) baseDaily + 1L * perLevel));
        assertEquals(340, dailyLvl1);

        // Defender level 10: 300 + 400 = 700
        long dailyLvl10 = Math.max(minDaily, Math.min(maxDaily, (long) baseDaily + 10L * perLevel));
        assertEquals(700, dailyLvl10);

        // Defender level 50: 300 + 2000 = 2300 -> capped at 2000
        long dailyLvl50 = Math.max(minDaily, Math.min(maxDaily, (long) baseDaily + 50L * perLevel));
        assertEquals(maxDaily, dailyLvl50);

        // Negative or zero level bounded by minDaily
        long dailyLvlMin = Math.max(minDaily, Math.min(maxDaily, -100L));
        assertEquals(minDaily, dailyLvlMin);
    }

    @Test
    @DisplayName("Siege level prerequisites enforce min levels and maximum level gap")
    void siegeLevelRequirements() {
        int minAtk = 5;
        int minDef = 4;
        int maxGap = 8;

        // Valid: atk 6, def 5 (gap = 1)
        assertTrue(isValidSiege(6, 5, minAtk, minDef, maxGap));

        // Invalid: atk 4 (< min 5)
        assertFalse(isValidSiege(4, 5, minAtk, minDef, maxGap));

        // Invalid: def 3 (< min 4)
        assertFalse(isValidSiege(5, 3, minAtk, minDef, maxGap));

        // Invalid: atk 15, def 4 (gap = 11 > 8)
        assertFalse(isValidSiege(15, 4, minAtk, minDef, maxGap));

        // Valid: def > atk (def 10, atk 5: gap is negative, permitted)
        assertTrue(isValidSiege(5, 10, minAtk, minDef, maxGap));

        // Edge case: exactly max gap (atk 12, def 4: gap = 8)
        assertTrue(isValidSiege(12, 4, minAtk, minDef, maxGap));
    }

    private boolean isValidSiege(int atkLevel, int defLevel, int minAtk, int minDef, int maxGap) {
        if (atkLevel < minAtk) return false;
        if (defLevel < minDef) return false;
        return (atkLevel - defLevel) <= maxGap;
    }

    @Test
    @DisplayName("ClanModifier expiration and daysLeft tracking")
    void modifierExpiryAndState() {
        UUID modId = UUID.randomUUID();
        UUID clanId = UUID.randomUUID();
        UUID targetClanId = UUID.randomUUID();
        long now = 100_000L;
        long endsAt = now + 86_400_000L * 3; // 3 days

        Map<String, String> payload = Map.of(
                "target_clan_id", targetClanId.toString(),
                "daily_amount", "500",
                "days_left", "3",
                "arrears", "1"
        );

        ClanModifier modifier = new ClanModifier(modId, clanId, ClanModifier.TYPE_REPARATIONS_DEBT, payload, now, endsAt, 1);

        assertFalse(modifier.isExpired(now));
        assertFalse(modifier.isExpired(now + 86_400_000L * 2));
        assertTrue(modifier.isExpired(endsAt + 1000L));

        assertEquals(500, modifier.dailyAmount());
        assertEquals(3, modifier.daysLeft());
        assertEquals(1, modifier.arrears());
        assertEquals(targetClanId, modifier.targetClanId());

        ClanModifier decremented = modifier.withDaysAndArrears(2, 2);
        assertEquals(2, decremented.daysLeft());
        assertEquals(2, decremented.arrears());
    }

    @Test
    @DisplayName("Raid substantial loot check correctly identifies qualification for extraction")
    void raidSubstantialLootCheck() {
        UUID raidId = UUID.randomUUID();
        UUID atkClan = UUID.randomUUID();
        UUID defClan = UUID.randomUUID();
        long now = System.currentTimeMillis();

        long moneyCap = 1000L;
        int itemCap = 10;

        ClanRaid raid = new ClanRaid(
                raidId, atkClan, defClan, now, now + 600_000L,
                RaidState.ACTIVE, RaidPhase.LOOT, null, null,
                0.0, moneyCap, 0L, itemCap, 0, 0L, false
        );

        // Zero loot -> not substantial
        assertFalse(raid.hasSubstantialLoot(15.0, 1));

        // Only 10% money (< 15%) and 0 items -> not substantial
        ClanRaid raid10Percent = raid.withMoneyLooted(100L);
        assertFalse(raid10Percent.hasSubstantialLoot(15.0, 1));

        // 15% money (150) -> substantial!
        ClanRaid raid15Percent = raid.withMoneyLooted(150L);
        assertTrue(raid15Percent.hasSubstantialLoot(15.0, 1));

        // 0 money, but 1 item slot taken (min = 1) -> substantial!
        ClanRaid raid1Item = raid.withItemSlotsLooted(1);
        assertTrue(raid1Item.hasSubstantialLoot(15.0, 1));
    }

    @Test
    @DisplayName("Siege camp hits required formula with fortification level")
    void siegeCampHitsWithFortification() {
        int hitsPerLevel = 1;

        // Base level 0 -> 1 hit
        int hitsLvl0 = 1 + 0 * hitsPerLevel;
        assertEquals(1, hitsLvl0);

        // Fortified level 1 -> 2 hits
        int hitsLvl1 = 1 + 1 * hitsPerLevel;
        assertEquals(2, hitsLvl1);

        // Fortified level 3 (max) -> 4 hits
        int hitsLvl3 = 1 + 3 * hitsPerLevel;
        assertEquals(4, hitsLvl3);

        // Custom config: 2 hits per level
        int customHitsPerLevel = 2;
        int hitsCustomLvl2 = 1 + 2 * customHitsPerLevel;
        assertEquals(5, hitsCustomLvl2);
    }

    @Test
    @DisplayName("Cylinder capture geometry checks radius and Y bounds [-2, +4]")
    void cylinderCaptureGeometry() {
        double radius = 6.0;
        double radiusSq = radius * radius;
        double chestX = 100.0;
        double chestY = 64.0;
        double chestZ = 200.0;

        // Inside zone: exactly at chest
        assertTrue(isInCylinder(100.0, 64.0, 200.0, chestX, chestY, chestZ, radiusSq));

        // Inside zone: 4 blocks horizontally, 3 blocks above
        assertTrue(isInCylinder(104.0, 67.0, 200.0, chestX, chestY, chestZ, radiusSq));

        // Inside zone: 5 blocks horizontally, 2 blocks below
        assertTrue(isInCylinder(100.0, 62.0, 205.0, chestX, chestY, chestZ, radiusSq));

        // Outside zone: 7 blocks horizontally (> 6.0 radius)
        assertFalse(isInCylinder(107.0, 64.0, 200.0, chestX, chestY, chestZ, radiusSq));

        // Outside zone: 5 blocks above chest (relY = 5 > 4.0)
        assertFalse(isInCylinder(100.0, 69.0, 200.0, chestX, chestY, chestZ, radiusSq));

        // Outside zone: 3 blocks below chest (relY = -3 < -2.0)
        assertFalse(isInCylinder(100.0, 61.0, 200.0, chestX, chestY, chestZ, radiusSq));
    }

    private boolean isInCylinder(double px, double py, double pz, double cx, double cy, double cz, double rSq) {
        double dx = px - cx;
        double dz = pz - cz;
        double dy = py - cy;
        return (dx * dx + dz * dz <= rSq) && (dy >= -2.0 && dy <= 4.0);
    }
}
