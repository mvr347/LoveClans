package me.lovelace.loveclans.manager;

public final class SpiritAbilityCooldownException extends IllegalStateException {
    private final long remainingMillis;

    public SpiritAbilityCooldownException(long remainingMillis) {
        super("gui.spirit.ability.cooldown");
        this.remainingMillis = remainingMillis;
    }

    public long remainingMillis() {
        return remainingMillis;
    }
}
