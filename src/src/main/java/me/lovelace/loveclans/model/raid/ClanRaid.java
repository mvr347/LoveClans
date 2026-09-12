package me.lovelace.loveclans.model.raid;

import java.util.UUID;

/**
 * §3.1 Набег. {@code moneyLootCap}/{@code itemSlotLootCap} are snapshotted from the defender's
 * chest the moment the raid goes ACTIVE (50% of what it held at that instant); {@code moneyLooted}
 * /{@code itemSlotsLooted} track cumulative progress against those caps for the rest of the window.
 */
public record ClanRaid(
        UUID id,
        UUID attackerClanId,
        UUID defenderClanId,
        long startedAt,
        long endsAt,
        RaidState state,
        long moneyLootCap,
        long moneyLooted,
        int itemSlotLootCap,
        int itemSlotsLooted
) {
    public ClanRaid(UUID id, UUID attackerClanId, UUID defenderClanId, long startedAt, long endsAt, RaidState state) {
        this(id, attackerClanId, defenderClanId, startedAt, endsAt, state, 0L, 0L, 0, 0);
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

    public long moneyRemaining() {
        return Math.max(0L, moneyLootCap - moneyLooted);
    }

    public int itemSlotsRemaining() {
        return Math.max(0, itemSlotLootCap - itemSlotsLooted);
    }

    public ClanRaid activate(long newEndsAt, long moneyLootCap, int itemSlotLootCap) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, newEndsAt, RaidState.ACTIVE,
                moneyLootCap, 0L, itemSlotLootCap, 0);
    }

    public ClanRaid withMoneyLooted(long additionalMoney) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                moneyLootCap, moneyLooted + additionalMoney, itemSlotLootCap, itemSlotsLooted);
    }

    public ClanRaid withItemSlotsLooted(int additionalSlots) {
        return new ClanRaid(id, attackerClanId, defenderClanId, startedAt, endsAt, state,
                moneyLootCap, moneyLooted, itemSlotLootCap, itemSlotsLooted + additionalSlots);
    }
}
