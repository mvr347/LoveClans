package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.war.WarResult;
import me.lovelace.loveclans.model.war.WarState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AchievementsListenerTest {

    private static final UUID ATTACKER = UUID.randomUUID();
    private static final UUID DEFENDER = UUID.randomUUID();
    private static final ClanWar WAR = new ClanWar(UUID.randomUUID(), ATTACKER, DEFENDER, null, 0L, 1L,
            WarState.FINISHED, 0, 0);

    @Test
    void winnerIsTheSideThatWon() {
        assertEquals(Optional.of(ATTACKER), AchievementsListener.winnerClan(WAR, WarResult.ATTACKER_WIN));
        assertEquals(Optional.of(DEFENDER), AchievementsListener.winnerClan(WAR, WarResult.DEFENDER_WIN));
    }

    @Test
    void drawAndCancelledWarsHaveNoWinner() {
        assertEquals(Optional.empty(), AchievementsListener.winnerClan(WAR, WarResult.DRAW));
        assertEquals(Optional.empty(), AchievementsListener.winnerClan(WAR, WarResult.CANCELLED));
    }
}
