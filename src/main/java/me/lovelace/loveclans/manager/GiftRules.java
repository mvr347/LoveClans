package me.lovelace.loveclans.manager;

/**
 * Pure gift limits, so the menu can grey out an amount for the same reason the server would refuse it.
 * Returned values are {@code deny.gift.*} lang keys; {@code null} means the gift is allowed.
 */
public final class GiftRules {
    private GiftRules() {
    }

    public static String deny(boolean enabled, long amount, long maxAmount, long cooldownSeconds, long balance) {
        if (!enabled) return "deny.gift.disabled";
        if (cooldownSeconds > 0) return "deny.gift.cooldown";
        if (amount > maxAmount) return "deny.gift.too-large";
        if (amount > balance) return "deny.gift.insufficient-funds";
        return null;
    }
}
