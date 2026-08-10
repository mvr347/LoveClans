package me.lovelace.loveclans.model.trade;

import java.util.UUID;

/**
 * A clan trade's payout to one side, held back for {@code dueAt} (§4.2 delayed delivery - 10
 * minutes after both clans confirm) before it's credited to {@code clanId}'s chest. {@code items}
 * is a serialized ItemStack[] payload (see InventorySerialization); {@code money} is credited in
 * one shot once due, but items may only partially fit if the chest is full - in that case
 * {@code items} is rewritten to whatever is still left over and the delivery stays pending
 * (retried on the next tick) instead of being dropped.
 */
public record ClanTradeDelivery(
        UUID id,
        UUID clanId,
        String fromTag,
        String fromTagColor,
        long money,
        byte[] items,
        long dueAt,
        boolean moneyDelivered
) {
    public ClanTradeDelivery withMoneyDelivered() {
        return new ClanTradeDelivery(id, clanId, fromTag, fromTagColor, money, items, dueAt, true);
    }

    public ClanTradeDelivery withItems(byte[] remainingItems) {
        return new ClanTradeDelivery(id, clanId, fromTag, fromTagColor, money, remainingItems, dueAt, moneyDelivered);
    }
}
