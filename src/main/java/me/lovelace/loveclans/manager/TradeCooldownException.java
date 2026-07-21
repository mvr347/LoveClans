package me.lovelace.loveclans.manager;

public final class TradeCooldownException extends IllegalStateException {
    private final long remainingSeconds;

    public TradeCooldownException(long remainingSeconds) {
        super("trade.cooldown-active");
        this.remainingSeconds = remainingSeconds;
    }

    public long remainingSeconds() {
        return remainingSeconds;
    }
}
