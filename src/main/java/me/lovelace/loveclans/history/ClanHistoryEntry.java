package me.lovelace.loveclans.history;

import java.util.Map;
import java.util.UUID;

/**
 * One chronicle line. The text is NOT stored: only the type and structured metadata, so the wording can change
 * (or be translated) without touching the stored history.
 */
public record ClanHistoryEntry(UUID id, UUID clanId, HistoryType type, UUID actorId, long timestamp,
                               Map<String, String> metadata) {
}
