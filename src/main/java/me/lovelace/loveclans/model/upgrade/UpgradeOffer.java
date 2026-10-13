package me.lovelace.loveclans.model.upgrade;

import java.util.UUID;

/**
 * One pending "pick one of two" decision. The pair is fixed when the offer is created and never
 * re-rolled. {@code option2} is null when only one option was available at that moment.
 * Offers are mutated only on the main thread, which is what makes a double click harmless.
 */
public final class UpgradeOffer {

    public enum Kind { NORMAL, DIRECTION }

    public enum Status { PENDING, CHOSEN, CANCELLED }

    private final UUID id;
    private final UUID clanId;
    private final Kind kind;
    private final String option1;
    private final String option2;
    private final long createdAt;
    private Status status;
    private String chosen;

    public UpgradeOffer(UUID id, UUID clanId, Kind kind, String option1, String option2,
                        Status status, String chosen, long createdAt) {
        this.id = id;
        this.clanId = clanId;
        this.kind = kind;
        this.option1 = option1;
        this.option2 = option2;
        this.status = status;
        this.chosen = chosen;
        this.createdAt = createdAt;
    }

    public static UpgradeOffer create(UUID clanId, Kind kind, String option1, String option2) {
        return new UpgradeOffer(UUID.randomUUID(), clanId, kind, option1, option2, Status.PENDING, null,
                System.currentTimeMillis());
    }

    public UUID id() { return id; }
    public UUID clanId() { return clanId; }
    public Kind kind() { return kind; }
    public String option1() { return option1; }
    public String option2() { return option2; }
    public long createdAt() { return createdAt; }
    public Status status() { return status; }
    public String chosen() { return chosen; }
    public boolean pending() { return status == Status.PENDING; }

    public boolean offers(String option) {
        return option != null && (option.equals(option1) || option.equals(option2));
    }

    /** @return false when the offer was already handled - the caller must then do nothing. */
    public boolean choose(String option) {
        if (!pending() || !offers(option)) {
            return false;
        }
        this.status = Status.CHOSEN;
        this.chosen = option;
        return true;
    }

    /** Resolves an offer that has no fixed pair (the direction pick); the caller validates the value. */
    public boolean resolve(String value) {
        if (!pending()) {
            return false;
        }
        this.status = Status.CHOSEN;
        this.chosen = value;
        return true;
    }

    public boolean cancel() {
        if (!pending()) {
            return false;
        }
        this.status = Status.CANCELLED;
        return true;
    }
}
