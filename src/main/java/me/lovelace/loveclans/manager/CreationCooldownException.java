package me.lovelace.loveclans.manager;

public final class CreationCooldownException extends IllegalStateException {
    private final long remainingMillis;

    public CreationCooldownException(long remainingMillis) {
        super("clan.creation-cooldown");
        this.remainingMillis = remainingMillis;
    }

    public long remainingMillis() {
        return remainingMillis;
    }
}
