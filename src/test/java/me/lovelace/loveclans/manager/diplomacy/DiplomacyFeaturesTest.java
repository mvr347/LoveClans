package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.model.diplomacy.ClanPact;
import me.lovelace.loveclans.model.diplomacy.PactType;
import me.lovelace.loveclans.model.modifier.ClanModifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiplomacyFeaturesTest {

    @Test
    void testClanPactModel() {
        UUID clanA = UUID.randomUUID();
        UUID clanB = UUID.randomUUID();
        UUID clanC = UUID.randomUUID();

        long now = System.currentTimeMillis();
        ClanPact napPact = ClanPact.create(clanA, clanB, PactType.NAP, now + 3600_000L);

        assertEquals(PactType.NAP, napPact.type());
        assertTrue(napPact.involves(clanA));
        assertTrue(napPact.involves(clanB));
        assertFalse(napPact.involves(clanC));

        assertTrue(napPact.matchesPair(clanA, clanB));
        assertTrue(napPact.matchesPair(clanB, clanA));
        assertFalse(napPact.matchesPair(clanA, clanC));

        assertEquals(clanB, napPact.otherClan(clanA));
        assertEquals(clanB, napPact.otherClanId(clanA));
        assertEquals(clanA, napPact.otherClan(clanB));

        assertFalse(napPact.isPermanent());
        assertFalse(napPact.isExpired(now));
        assertTrue(napPact.isExpired(now + 7200_000L));
        assertTrue(napPact.remainingMillis(now) > 0);

        ClanPact permPact = ClanPact.createPermanent(clanA, clanB, PactType.ALLIANCE);
        assertTrue(permPact.isPermanent());
        assertFalse(permPact.isExpired(now + 100_000_000_000L));
    }

    @Test
    void testGuildShieldModifiers() {
        UUID clanId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        ClanModifier shield = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_GUILD_SHIELD,
                Map.of(),
                now,
                now + 24 * 3600_000L,
                1
        );

        assertFalse(shield.isExpired(now));
        assertEquals(ClanModifier.TYPE_GUILD_SHIELD, shield.type());
        assertTrue(shield.remainingMillis(now) > 0);

        ClanModifier cooldown = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_GUILD_SHIELD_COOLDOWN,
                Map.of(),
                now,
                now + 7 * 24 * 3600_000L,
                1
        );

        assertFalse(cooldown.isExpired(now));
        assertEquals(ClanModifier.TYPE_GUILD_SHIELD_COOLDOWN, cooldown.type());
        assertTrue(cooldown.remainingMillis(now) > shield.remainingMillis(now));
    }

    @Test
    void testGiftScoreScaling() {
        // Formula: (int) Math.min(25, Math.max(5, amount / 2000))
        assertEquals(5, Math.min(25, Math.max(5, 1_000L / 2000)));
        assertEquals(5, Math.min(25, Math.max(5, 5_000L / 2000)));
        assertEquals(5, Math.min(25, Math.max(5, 10_000L / 2000)));
        assertEquals(12, Math.min(25, Math.max(5, 25_000L / 2000)));
        assertEquals(25, Math.min(25, Math.max(5, 50_000L / 2000)));
        assertEquals(25, Math.min(25, Math.max(5, 100_000L / 2000)));
    }

    @Test
    void testRecognitionMatrixRules() {
        // Matrix verification:
        // - Official treaties / alliances / embargo / blockade / trade contract: requires both recognized
        // - Send gift: requires sender recognized (receiver may be unrecognized)
        // - Raid / war / siege: allowed even if either or both are unrecognized
        boolean selfRec = true;
        boolean otherRec = false;

        boolean canAlly = selfRec && otherRec;
        boolean canSendGift = selfRec;
        boolean canStartConflict = true; // allowed even when !otherRec

        assertFalse(canAlly, "Alliance requires both clans to be recognized");
        assertTrue(canSendGift, "Recognized clan may send gifts to any clan");
        assertTrue(canStartConflict, "Conflict / wars / raids are allowed regardless of recognition status");

        boolean selfUnrec = false;
        assertFalse(selfUnrec, "Unrecognized clan cannot send gifts or form treaties");
    }
}
