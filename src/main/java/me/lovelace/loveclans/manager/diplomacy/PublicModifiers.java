package me.lovelace.loveclans.manager.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Effects of another clan that anybody may see before acting on it: running conflicts, shields, sanctions and the
 * recognition status. Pure so the clan list, the diplomacy header and tests all derive the same set.
 */
public final class PublicModifiers {
    private PublicModifiers() {
    }

    public enum Kind {
        WAR, SIEGE, RAID, GUILD_SHIELD, POST_RAID_SHIELD, EMBARGO_BY_US, EMBARGO_ON_US, BLOCKADE_BY_US, BLOCKADE_ON_US, UNRECOGNIZED;

        /** Lang key under {@code gui.diplomacy.public}. */
        public String langKey() {
            return "gui.diplomacy.public." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** {@code seconds} is the time left for timed effects, 0 otherwise. */
    public record Entry(Kind kind, long seconds) {
    }

    public record Snapshot(boolean war, boolean siege, boolean raid, long guildShieldSeconds, long postRaidShieldSeconds,
                           boolean embargoByUs, boolean embargoOnUs, boolean blockadeByUs, boolean blockadeOnUs,
                           boolean recognized) {
    }

    /** Most urgent first: conflicts, then shields, then sanctions, then recognition. */
    public static List<Entry> of(Snapshot s) {
        List<Entry> result = new ArrayList<>();
        if (s.war()) result.add(new Entry(Kind.WAR, 0));
        if (s.siege()) result.add(new Entry(Kind.SIEGE, 0));
        if (s.raid()) result.add(new Entry(Kind.RAID, 0));
        if (s.guildShieldSeconds() > 0) result.add(new Entry(Kind.GUILD_SHIELD, s.guildShieldSeconds()));
        if (s.postRaidShieldSeconds() > 0) result.add(new Entry(Kind.POST_RAID_SHIELD, s.postRaidShieldSeconds()));
        if (s.blockadeByUs()) result.add(new Entry(Kind.BLOCKADE_BY_US, 0));
        if (s.blockadeOnUs()) result.add(new Entry(Kind.BLOCKADE_ON_US, 0));
        if (s.embargoByUs()) result.add(new Entry(Kind.EMBARGO_BY_US, 0));
        if (s.embargoOnUs()) result.add(new Entry(Kind.EMBARGO_ON_US, 0));
        if (!s.recognized()) result.add(new Entry(Kind.UNRECOGNIZED, 0));
        return List.copyOf(result);
    }
}
