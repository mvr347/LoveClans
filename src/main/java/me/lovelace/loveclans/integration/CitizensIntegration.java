package me.lovelace.loveclans.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.lang.reflect.Method;

/**
 * Pure-reflection bridge into Citizens (no compile-time dependency), mirroring LoveHunt's and
 * LoveTweaks' CitizensIntegration, so the plugin loads fine whether or not Citizens is present.
 * Used to bind the "Marshal" NPC that clan leaders interact with to pick a weekly contract.
 */
public final class CitizensIntegration {
    private final Plugin citizensPlugin;

    public CitizensIntegration() {
        this.citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
    }

    public boolean isAvailable() {
        return citizensPlugin != null && citizensPlugin.isEnabled();
    }

    public boolean isNpc(Entity entity) {
        return npcOf(entity) != null;
    }

    public Integer npcId(Entity entity) {
        Object npc = npcOf(entity);
        if (npc == null) {
            return null;
        }
        Object id = invoke(npc, "getId");
        return id instanceof Integer value ? value : null;
    }

    public String npcName(Entity entity) {
        Object npc = npcOf(entity);
        if (npc == null) {
            return null;
        }
        Object name = invoke(npc, "getName");
        return name == null ? null : name.toString();
    }

    /**
     * Resolves the NPC the player is currently looking at. Used for admin NPC binding instead of
     * Citizens' own selector API, so we don't have to reflect into that API surface too.
     */
    public Entity lookedAtNpc(Player player, double distance) {
        if (!isAvailable() || player == null) {
            return null;
        }
        RayTraceResult trace = player.rayTraceEntities(distance);
        if (trace == null) {
            return null;
        }
        Entity hit = trace.getHitEntity();
        return hit != null && isNpc(hit) ? hit : null;
    }

    private Object npcOf(Entity entity) {
        if (!isAvailable() || entity == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = apiClass.getMethod("getNPCRegistry").invoke(null);
            Object isNpc = registry.getClass().getMethod("isNPC", Entity.class).invoke(registry, entity);
            if (!(isNpc instanceof Boolean bool) || !bool) {
                return null;
            }
            return registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
