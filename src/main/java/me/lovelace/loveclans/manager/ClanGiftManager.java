package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;

import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanGiftManager {
    private final LoveClansPlugin plugin;
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> lastGiftAt = new ConcurrentHashMap<>();

    public ClanGiftManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(a, b)
                : new AbstractMap.SimpleImmutableEntry<>(b, a);
    }

    public long getCooldownRemaining(UUID fromClanId, UUID toClanId) {
        long cooldownMillis = plugin.getConfig().getLong("clans.gifts.cooldown-seconds", 3600L) * 1000L;
        Long last = lastGiftAt.get(pairKey(fromClanId, toClanId));
        if (last == null) return 0L;
        long elapsed = System.currentTimeMillis() - last;
        return elapsed < cooldownMillis ? (cooldownMillis - elapsed) / 1000L : 0L;
    }

    public CompletableFuture<Void> sendGiftAsync(Clan from, Clan to, long amount, UUID actorId) {
        return plugin.supplySync(() -> {
            if (amount <= 0) {
                throw new IllegalStateException("gift.invalid-amount");
            }
            if (!plugin.getConfig().getBoolean("clans.gifts.enabled", true)) {
                throw new IllegalStateException("gift.disabled");
            }
            if (!from.isRecognized()) {
                throw new IllegalStateException("gift.sender-not-recognized");
            }
            if (!from.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (!from.hasCapital() || !to.hasCapital()) {
                throw new IllegalStateException("clan.no-territory");
            }
            if (from.id().equals(to.id())) {
                throw new IllegalStateException("general.error");
            }
            if (plugin.getWarManager().areAtWar(from.id(), to.id())) {
                throw new IllegalStateException("gift.blocked-by-war");
            }
            if (plugin.getDiplomacyManager() != null) {
                plugin.getDiplomacyManager().requireAllowed(DiplomacyActions.Action.GIFT, from, to);
            }
            long max = plugin.getConfig().getLong("clans.gifts.max-amount", 100_000L);
            if (amount > max) {
                throw new IllegalStateException("gift.too-large");
            }
            long cooldownSec = getCooldownRemaining(from.id(), to.id());
            if (cooldownSec > 0) {
                throw new IllegalStateException("gift.cooldown");
            }
            if (from.chestMoney() < amount) {
                throw new IllegalStateException("gift.insufficient-funds");
            }
            // Everything that touches clan state happens here on the main thread, before the database write:
            // the cooldown is set first so a double click cannot pay twice while the write is still in flight.
            long newFromBal = from.addChestMoney(-amount);
            long newToBal = to.addChestMoney(amount);
            lastGiftAt.put(pairKey(from.id(), to.id()), System.currentTimeMillis());
            return new long[]{newFromBal, newToBal};
        }).thenCompose(balances -> plugin.getStorage()
                .transferChestMoneyAsync(from.id(), balances[0], to.id(), balances[1])
                .whenComplete((ok, error) -> {
                    if (error != null) {
                        plugin.runSync(() -> {
                            from.addChestMoney(amount);
                            to.addChestMoney(-amount);
                            lastGiftAt.remove(pairKey(from.id(), to.id()));
                        });
                    }
                }))
        .thenRun(() -> plugin.runSync(() -> {
            if (plugin.getDiplomacyManager() != null) {
                int scoreGain = (int) Math.min(25, Math.max(5, amount / 2000));
                plugin.getDiplomacyManager().adjustPairScore(from.id(), to.id(), scoreGain);
            }
            plugin.getClanManager().getOnlineMembersWithPermission(from, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "clan.gift.sent",
                            Map.of("tag", to.tag(), "color", to.tagColor(), "amount", String.valueOf(amount))));
            plugin.getClanManager().getOnlineMembersWithPermission(to, ClanPermission.DIPLOMACY)
                    .forEach(p -> plugin.getMessages().send(p, "clan.gift.received",
                            Map.of("tag", from.tag(), "color", from.tagColor(), "amount", String.valueOf(amount))));
            plugin.getSoundService().playClan(from, "gift-sent");
            plugin.getSoundService().playClan(to, "gift-received");
        }));
    }
}
