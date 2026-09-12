package me.lovelace.loveclans.model.trade;

import java.util.UUID;

/**
 * A clan-to-clan trade offer (§4.2). {@code items} is a serialized ItemStack[] payload (see
 * InventorySerialization) escrowed from the proposing clan's chest at offer time; {@code money}
 * is a chest-money balance amount, also already escrowed (removed from the proposer's chest
 * balance) by the time this record exists.
 */
public record ClanTrade(
        UUID id,
        UUID fromClanId,
        UUID toClanId,
        long money,
        byte[] items,
        TradeStatus status,
        long createdAt,
        long resolvedAt
) {
    public ClanTrade resolved(TradeStatus status) {
        return new ClanTrade(id, fromClanId, toClanId, money, items, status, createdAt, System.currentTimeMillis());
    }
}
