package me.lovelace.loveclans.model.spirit;

/**
 * Pure bookkeeping for the spirit's Mastery axis, -100 (defence) .. +100 (offence). It only moves through actions and is
 * capped per side per day, so no amount of grinding one action swings the axis in an afternoon.
 */
public final class MasteryLedger {
    public static final int LIMIT = 100;

    private MasteryLedger() {
    }

    /** {@code day} is an epoch day; the two counters are what was already gained on that day. */
    public record State(int mastery, long day, int offenseToday, int defenseToday) {
        public static State neutral() {
            return new State(0, 0L, 0, 0);
        }
    }

    public record Caps(int offense, int defense) {
    }

    /** Positive {@code delta} is an offensive gain, negative a defensive one. Returns the new state, caps applied. */
    public static State apply(State state, int delta, long today, Caps caps) {
        State s = state.day() == today ? state : new State(state.mastery(), today, 0, 0);
        if (delta > 0) {
            int allowed = Math.max(0, Math.min(delta, caps.offense() - s.offenseToday()));
            return new State(Math.min(LIMIT, s.mastery() + allowed), today, s.offenseToday() + allowed, s.defenseToday());
        }
        if (delta < 0) {
            int allowed = Math.max(0, Math.min(-delta, caps.defense() - s.defenseToday()));
            return new State(Math.max(-LIMIT, s.mastery() - allowed), today, s.offenseToday(), s.defenseToday() + allowed);
        }
        return s;
    }

    /** Moves the axis {@code amount} points toward 0 without overshooting. */
    public static int driftToNeutral(int mastery, int amount) {
        if (amount <= 0) return mastery;
        if (mastery > 0) return Math.max(0, mastery - amount);
        if (mastery < 0) return Math.min(0, mastery + amount);
        return 0;
    }
}
