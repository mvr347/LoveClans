package me.lovelace.loveclans.history;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Permanent chronicle of a clan. Every method is asynchronous: the database is never touched on the main thread. */
public interface ClanHistoryService {

    /** Appends an entry. {@code actorId} and {@code metadata} may be null. */
    void add(UUID clanId, HistoryType type, UUID actorId, Map<String, String> metadata);

    /** One page, newest first. {@code page} starts at 0. */
    CompletableFuture<List<ClanHistoryEntry>> get(UUID clanId, int page, int pageSize);

    CompletableFuture<List<ClanHistoryEntry>> get(UUID clanId, HistoryFilter filter, int page, int pageSize);
}
