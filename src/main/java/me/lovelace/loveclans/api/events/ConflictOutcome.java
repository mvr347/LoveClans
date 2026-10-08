package me.lovelace.loveclans.api.events;

/** How a war, raid or siege ended from the attacker's point of view. Cancelled conflicts fire no resolved event. */
public enum ConflictOutcome {
    ATTACKER_WIN,
    DEFENDER_WIN,
    DRAW
}
