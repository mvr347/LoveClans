package me.lovelace.loveclans.model.siege;

import me.lovelace.loveclans.model.TerritoryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClanSiege(
        UUID id,
        UUID attackerClanId,
        UUID defenderClanId,
        TerritoryKey contestedTerritory,
        long startedAt,
        long endsAt,
        SiegeState state,
        List<SiegeCamp> camps
) {
    public boolean involves(UUID clanId) {
        return attackerClanId.equals(clanId) || defenderClanId.equals(clanId);
    }

    public boolean between(UUID first, UUID second) {
        return (attackerClanId.equals(first) && defenderClanId.equals(second))
                || (attackerClanId.equals(second) && defenderClanId.equals(first));
    }

    /** Promotes a PREPARING siege to ACTIVE, spawning the camps generated for it. */
    public ClanSiege activate(long newEndsAt, List<SiegeCamp> spawnedCamps) {
        return new ClanSiege(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, newEndsAt, SiegeState.ACTIVE, spawnedCamps);
    }

    public ClanSiege withCamp(SiegeCamp updatedCamp) {
        List<SiegeCamp> updated = new ArrayList<>(camps);
        updated.set(updatedCamp.index(), updatedCamp);
        return new ClanSiege(id, attackerClanId, defenderClanId, contestedTerritory, startedAt, endsAt, state, List.copyOf(updated));
    }
}
