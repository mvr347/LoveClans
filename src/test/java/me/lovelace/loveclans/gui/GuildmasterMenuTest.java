package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static me.lovelace.loveclans.gui.GuildmasterMenu.Action.CASUS_BELLI;
import static me.lovelace.loveclans.gui.GuildmasterMenu.Action.CLAN_LIST;
import static me.lovelace.loveclans.gui.GuildmasterMenu.Action.RECOGNITION;
import static me.lovelace.loveclans.gui.GuildmasterMenu.Action.VOWS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who sees which Guildmaster buttons: the clan list always, the rest by right (owner request 2026-11-05). */
class GuildmasterMenuTest {
    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID recruit = UUID.randomUUID();
    private final Clan clan = Clan.create(UUID.randomUUID(), "Test", "TST", "<white>", Material.WHITE_BANNER, leader, 1, true);

    {
        clan.addMember(member, ClanRank.MEMBER);
        clan.addMember(recruit, ClanRank.RECRUIT);
    }

    @Test
    void aPlayerWithoutRightsGetsThePlainClanList() {
        assertFalse(GuildmasterMenu.hasMenu(clan, recruit));
        assertFalse(GuildmasterMenu.hasMenu(clan, UUID.randomUUID()), "no clan at all");
    }

    @Test
    void vowsRightAddsVowsNextToTheClanList() {
        assertTrue(GuildmasterMenu.hasMenu(clan, member));
        assertEquals(List.of(VOWS, CLAN_LIST), GuildmasterMenu.buttonsFor(clan, member));
    }

    @Test
    void warRightAddsTheCasusBelli() {
        clan.setPermission(ClanRank.MEMBER, ClanPermission.DIPLOMACY, true);
        assertEquals(List.of(VOWS, CASUS_BELLI, CLAN_LIST), GuildmasterMenu.buttonsFor(clan, member));
    }

    @Test
    void theOwnerOfAnUnrecognizedClanAlsoSeesRecognition() {
        assertEquals(List.of(VOWS, RECOGNITION, CASUS_BELLI, CLAN_LIST), GuildmasterMenu.buttonsFor(clan, leader));
        clan.setRecognized(true);
        assertEquals(List.of(VOWS, CASUS_BELLI, CLAN_LIST), GuildmasterMenu.buttonsFor(clan, leader));
    }
}
