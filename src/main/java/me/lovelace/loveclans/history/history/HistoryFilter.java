package me.lovelace.loveclans.history;

import java.util.EnumSet;
import java.util.Set;

/** The filter buttons of the chronicle GUI; {@code ALL} means no type restriction. */
public enum HistoryFilter {
    ALL(EnumSet.allOf(HistoryType.class)),
    WARS(EnumSet.of(HistoryType.WAR_DECLARED, HistoryType.WAR_WON, HistoryType.WAR_LOST)),
    RAIDS(EnumSet.of(HistoryType.RAID_STARTED, HistoryType.RAID_WON, HistoryType.RAID_LOST)),
    SIEGES(EnumSet.of(HistoryType.SIEGE_DECLARED, HistoryType.SIEGE_WON, HistoryType.SIEGE_LOST, HistoryType.SIEGE_DEFENDED)),
    TERRITORIES(EnumSet.of(HistoryType.TERRITORY_CAPTURED, HistoryType.TERRITORY_LOST)),
    DIPLOMACY(EnumSet.of(HistoryType.DIPLOMACY_CREATED, HistoryType.DIPLOMACY_ENDED)),
    MEMBERS(EnumSet.of(HistoryType.CLAN_CREATED, HistoryType.CLAN_LEVEL_UP, HistoryType.MEMBER_JOINED,
            HistoryType.MEMBER_LEFT, HistoryType.MEMBER_KICKED, HistoryType.RANK_CHANGED, HistoryType.LEADER_CHANGED));

    private final Set<HistoryType> types;

    HistoryFilter(Set<HistoryType> types) {
        this.types = types;
    }

    public Set<HistoryType> types() {
        return types;
    }

    public boolean isAll() {
        return this == ALL;
    }

    public HistoryFilter next() {
        HistoryFilter[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
