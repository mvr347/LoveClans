package me.lovelace.loveclans.manager;

public final class WarCooldownException extends IllegalStateException {
    private final long remainingSeconds;

    public WarCooldownException(long remainingSeconds) {
        super("war.cooldown-active");
        this.remainingSeconds = remainingSeconds;
    }

    public long remainingSeconds() {
        return remainingSeconds;
    }
}
