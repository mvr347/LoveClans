package me.lovelace.loveclans.history;

/** What happened to the clan. The stored value is the enum name: renaming one needs a migration. */
public enum HistoryType {
    CLAN_CREATED,
    CLAN_LEVEL_UP,

    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_KICKED,
    RANK_CHANGED,
    LEADER_CHANGED,

    WAR_DECLARED,
    WAR_WON,
    WAR_LOST,

    RAID_STARTED,
    RAID_WON,
    RAID_LOST,

    SIEGE_DECLARED,
    SIEGE_WON,
    SIEGE_LOST,
    SIEGE_DEFENDED,

    TERRITORY_CAPTURED,
    TERRITORY_LOST,

    DIPLOMACY_CREATED,
    DIPLOMACY_ENDED
}
