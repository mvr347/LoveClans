package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.model.Clan;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.UUID;

public record PendingClaim(
        UUID playerId,
        Clan clan,
        Location location,
        String bannerType, // e.g., "CAPITAL" or "TERRITORY"
        BoundingBox visualizationBox
) {}