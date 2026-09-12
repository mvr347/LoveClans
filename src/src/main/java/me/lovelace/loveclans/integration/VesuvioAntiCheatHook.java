package me.lovelace.loveclans.integration;

import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Pure-reflection bridge into Vesuvio AntiCheat (no compile-time hard dependency).
 * Protects clan wars, sieges, raids, and rituals against high-risk or suspect players.
 *
 * Author: Lovelace
 */
public final class VesuvioAntiCheatHook {

    private final LoveClansPlugin plugin;

    public VesuvioAntiCheatHook(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Vesuvio");
    }

    /**
     * Checks if a player has a high risk score (>= 75.0) or is flagged as an active suspect.
     */
    public boolean isHighRisk(UUID uuid) {
        if (!isAvailable()) return false;
        try {
            Class<?> providerClass = Class.forName("net.lovelace.vesuvio.api.VesuvioProvider");
            Method isAvailMethod = providerClass.getMethod("isAvailable");
            if (!((boolean) isAvailMethod.invoke(null))) return false;

            Method getMethod = providerClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) return false;

            Class<?> apiClass = Class.forName("net.lovelace.vesuvio.api.VesuvioAPI");
            Method isHighRiskMethod = apiClass.getMethod("isHighRisk", UUID.class);
            boolean highRisk = (boolean) isHighRiskMethod.invoke(api, uuid);
            if (highRisk) return true;

            Method isSuspectMethod = apiClass.getMethod("isSuspect", UUID.class);
            return (boolean) isSuspectMethod.invoke(api, uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Retrieves the player's risk index from Vesuvio (0.0 to 100.0).
     */
    public double getRiskScore(UUID uuid) {
        if (!isAvailable()) return 0.0;
        try {
            Class<?> providerClass = Class.forName("net.lovelace.vesuvio.api.VesuvioProvider");
            Method isAvailMethod = providerClass.getMethod("isAvailable");
            if (!((boolean) isAvailMethod.invoke(null))) return 0.0;

            Method getMethod = providerClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) return 0.0;

            Class<?> apiClass = Class.forName("net.lovelace.vesuvio.api.VesuvioAPI");
            Method getRiskMethod = apiClass.getMethod("getRiskScore", UUID.class);
            return (double) getRiskMethod.invoke(api, uuid);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }
}
