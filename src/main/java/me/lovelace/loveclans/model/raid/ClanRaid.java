package me.lovelace.loveclans.model.raid;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Новый дизайн рейдов (newraids.md): случайная точка в столице защитника,
 * рейдовый сундук, зона захвата (радиус 5), прогресс 0..100%, snapshot казны,
 * substantial loot и extract в течение 60 секунд.
 */
public record ClanRaid(
        UUID id,
        UUID attackerClanId,
        UUID defenderClanId,
        long startedAt,
        long endsAt,
        RaidState state,
        RaidPhase phase,
        Location chestLocation,
        UUID chestHologramId,
        double captureProgress,
        long moneyLootCap,
        long moneyLooted,
        int itemSlotLootCap,
        int itemSlotsLooted,
        long firstLootAt,
        boolean extracted
) {
    public ClanRaid(UUID id, UUID attackerClanId, UUID defenderClanId, long startedAt, long endsAt, RaidState state) {
        this(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                RaidPhase.PREPARING, null, null, 0.0, 0L, 0L, 0, 0, 0L, false);
    }

    public boolean involves(UUID clanId) {
        return attackerClanId.equals(clanId) || defenderClanId.equals(clanId);
    }

    public boolean between(UUID first, UUID second) {
        return (attackerClanId.equals(first) && defenderClanId.equals(second))
                || (attackerClanId.equals(second) && defenderClanId.equals(first));
    }

    public boolean anyLooted() {
        return moneyLooted > 0 || itemSlotsLooted > 0;
    }

    public boolean hasSubstantialLoot(double minMoneyPercent, int minItemSlots) {
        if (itemSlotsLooted >= minItemSlots) {
            return true;
        }
        if (moneyLootCap > 0 && ((double) moneyLooted / moneyLootCap * 100.0) >= minMoneyPercent) {
            return true;
        }
        return false;
    }

    public long moneyRemaining() {
        return Math.max(0L, moneyLootCap - moneyLooted);
    }

    public int itemSlotsRemaining() {
        return Math.max(0, itemSlotLootCap - itemSlotsLooted);
    }

    public ClanRaid activate(long newEndsAt, Location chestLoc, UUID hologramId, long moneyCap, int itemCap) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, newEndsAt, RaidState.ACTIVE,
                RaidPhase.CAPTURE, chestLoc, hologramId, 0.0, moneyCap, 0L, itemCap, 0, 0L, false);
    }

    public ClanRaid withProgress(double newProgress) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                phase, chestLocation, chestHologramId, Math.max(0.0, Math.min(100.0, newProgress)),
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted, firstLootAt, extracted);
    }

    public ClanRaid withPhase(RaidPhase newPhase) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                newPhase, chestLocation, chestHologramId, captureProgress,
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted, firstLootAt, extracted);
    }

    public ClanRaid withMoneyLooted(long additionalMoney) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                phase, chestLocation, chestHologramId, captureProgress,
                moneyLootCap, moneyLooted + additionalMoney, itemSlotLootCap, itemSlotsLooted,
                firstLootAt, extracted);
    }

    public ClanRaid withItemSlotsLooted(int additionalSlots) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                phase, chestLocation, chestHologramId, captureProgress,
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted + additionalSlots,
                firstLootAt, extracted);
    }

    public ClanRaid withFirstLootAt(long timestamp) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                phase, chestLocation, chestHologramId, captureProgress,
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted,
                timestamp, extracted);
    }

    public ClanRaid withExtracted(boolean isExtracted) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                phase, chestLocation, chestHologramId, captureProgress,
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted,
                firstLootAt, isExtracted);
    }
}
