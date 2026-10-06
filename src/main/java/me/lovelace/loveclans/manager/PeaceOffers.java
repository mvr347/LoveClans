package me.lovelace.loveclans.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending peace proposals in a war, siege or raid. A proposal from A to B is accepted when B proposes peace
 * to A back (the same {@code /clan peace} command or button) before it expires; nothing ends without both
 * sides. Pure state with an injectable clock so it is unit-tested without a server.
 */
public final class PeaceOffers {
    private record Key(UUID from, UUID to) {}

    private final Map<Key, Long> offers = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public PeaceOffers(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Records an offer from {@code from} to {@code to}. Returns true when {@code to} had already offered peace
     * to {@code from} (and it is still valid): the offers cancel out and peace should be made now.
     */
    public boolean offerOrAccept(UUID from, UUID to, long now) {
        Long counter = offers.remove(new Key(to, from));
        if (counter != null && counter > now) {
            offers.remove(new Key(from, to));
            return true;
        }
        offers.put(new Key(from, to), now + ttlMillis);
        return false;
    }

    /** True while {@code from} has a valid offer waiting for {@code to}. */
    public boolean hasOffer(UUID from, UUID to, long now) {
        Long until = offers.get(new Key(from, to));
        return until != null && until > now;
    }

    /** Drops every offer that involves the clan (conflict ended, clan disbanded). */
    public void clear(UUID clanId) {
        offers.keySet().removeIf(key -> key.from().equals(clanId) || key.to().equals(clanId));
    }

    public void clearPair(UUID a, UUID b) {
        offers.remove(new Key(a, b));
        offers.remove(new Key(b, a));
    }
}
