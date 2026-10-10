package me.lovelace.loveclans.manager;

import org.bukkit.Location;

import java.util.UUID;

public record FoundationSession(
        UUID playerId,
        String name,
        String tag,
        boolean open,
        Location previewLocation,
        FoundationPhase phase,
        long createdAtMs
) {
    public FoundationSession withPreview(Location loc) {
        return new FoundationSession(playerId, name, tag, open, loc, phase, createdAtMs);
    }

    public FoundationSession withPhase(FoundationPhase newPhase) {
        return new FoundationSession(playerId, name, tag, open, previewLocation, newPhase, createdAtMs);
    }

    public boolean isExpired(long maxAgeMillis) {
        return System.currentTimeMillis() - createdAtMs > maxAgeMillis;
    }
}
