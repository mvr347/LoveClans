package me.lovelace.loveclans.activity;

import java.util.List;
import java.util.UUID;

/**
 * Public read/write API of the activity system. Reads come from memory and are safe on any thread; the database is
 * written asynchronously. Activity is not a currency: it is only ever added.
 */
public interface ActivityService {

    /** Adds {@code amount} to the player's activity inside the clan, for lifetime, the current week and month. */
    void addPlayerActivity(UUID playerId, UUID clanId, ActivityCategory category, int amount);

    PlayerActivity getPlayerActivity(UUID playerId);

    ClanActivity getClanActivity(UUID clanId);

    PlayerActivity getPlayerActivity(UUID playerId, ActivityPeriod period);

    ClanActivity getClanActivity(UUID clanId, ActivityPeriod period);

    /** Members of the clan ordered by activity in {@code period}, highest first. */
    List<PlayerActivity> getClanMembersRanking(UUID clanId, ActivityPeriod period);

    /** Clans ordered by activity in {@code period}, highest first (for leaderboards). */
    List<ClanActivity> getClanRanking(ActivityPeriod period, int limit);
}
