package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.listener.TerritoryEnterText.Kind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryEnterTextTest {
    private final UUID mine = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void ownClanTerritory() {
        assertEquals(Kind.OWN, TerritoryEnterText.choose(mine, mine, false));
        assertEquals(Kind.OWN, TerritoryEnterText.choose(mine, mine, true));
    }

    @Test
    void clanAtWarIsEnemy() {
        assertEquals(Kind.ENEMY, TerritoryEnterText.choose(mine, other, true));
    }

    @Test
    void everyoneElseIsForeign() {
        assertEquals(Kind.FOREIGN, TerritoryEnterText.choose(mine, other, false));
        assertEquals(Kind.FOREIGN, TerritoryEnterText.choose(null, other, false));
        assertEquals(Kind.FOREIGN, TerritoryEnterText.choose(null, other, true));
    }

    @Test
    void langKeys() {
        assertEquals("territory.enter.own", Kind.OWN.langKey());
        assertEquals("territory.enter.enemy", Kind.ENEMY.langKey());
        assertEquals("territory.enter.foreign", Kind.FOREIGN.langKey());
    }
}
