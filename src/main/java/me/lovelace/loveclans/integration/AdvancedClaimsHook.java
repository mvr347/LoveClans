package me.lovelace.loveclans.integration;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.TerritoryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class AdvancedClaimsHook {
    private final LoveClansPlugin plugin;
    private Object api;
    private Class<?> trustLevelClass;
    private boolean enabled;
    private Method showBorderMethod;
    private Method hideBorderMethod;

    public AdvancedClaimsHook(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        enabled = plugin.getConfig().getBoolean("integration.advanced-claims.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("LoveClaims");
        if (!enabled) {
            plugin.getLogger().info("LoveClaims integration is disabled or plugin is not installed.");
            return;
        }

        try {
            String apiClassName = plugin.getConfig().getString("integration.advanced-claims.api-class", "me.lovelace.loveclaims.api.LoveClaimsAPI");
            String trustLevelClassName = plugin.getConfig().getString("integration.advanced-claims.trust-level-class", "me.lovelace.loveclaims.model.TrustLevel");
            Class<?> apiClass = Class.forName(apiClassName);
            Method getInstance = apiClass.getMethod(plugin.getConfig().getString("integration.advanced-claims.methods.get-instance", "getInstance"));
            api = getInstance.invoke(null);
            trustLevelClass = Class.forName(trustLevelClassName);

            // Get showBorder and hideBorder methods
            showBorderMethod = apiClass.getMethod("showBorder", Player.class, BoundingBox.class, long.class);
            hideBorderMethod = apiClass.getMethod("hideBorder", Player.class);

            plugin.getLogger().info("AdvancedClaimsAPI integration enabled.");
        } catch (ReflectiveOperationException exception) {
            enabled = false;
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims found, but API reflection failed.", exception);
        }
    }

    public boolean enabled() {
        return enabled && api != null;
    }

    public Optional<UUID> createOrAttachClaim(Clan clan, ClanTerritory territory) {
        if (!enabled() || api == null || !plugin.getConfig().getBoolean("integration.advanced-claims.auto-claim-chunk", true)) {
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (clan == null || territory == null) {
            plugin.getLogger().warning("createOrAttachClaim called with null clan or territory.");
            return Optional.empty();
        }

        TerritoryKey key = territory.key();
        World world = Bukkit.getWorld(key.world());
        if (world == null) {
            plugin.getLogger().warning("createOrAttachClaim called for a non-existent world: " + key.world());
            return Optional.empty();
        }

        BoundingBox box;
        Location centerLoc;

        if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
            // New 25x25x25 logic around banner
            int radius = 12; // 25 total (12 + 1 + 12)
            box = new BoundingBox(
                    territory.bannerX() - radius, world.getMinHeight(), territory.bannerZ() - radius,
                    territory.bannerX() + radius, world.getMaxHeight(), territory.bannerZ() + radius
            );
            centerLoc = new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());
        } else {
            // Fallback to old chunk logic if for some reason no banner coords
            int minX = key.chunkX() << 4;
            int minZ = key.chunkZ() << 4;
            box = new BoundingBox(minX, world.getMinHeight(), minZ, minX + 15, world.getMaxHeight(), minZ + 15);
            centerLoc = world.getHighestBlockAt(minX + 8, minZ + 8).getLocation();
        }

        // Use createClanClaim instead of createClaim for clan territories.
        // The owner is the clan's UUID, not the leader's.
        Object claim = invokeBest(
                methodName("create-clan-claim", "createClanClaim"),
                new Object[]{world, box, clan.id(), centerLoc}
        ).orElse(null);

        Optional<UUID> claimId = extractClaimId(claim);
        claimId.ifPresent(id -> syncClanTrust(clan, territory.withAdvancedClaimId(id)));
        return claimId;
    }

    public void deleteClaim(UUID claimId) {
        if (!enabled() || api == null) {
            return;
        }
        // Защита от дурачков: Проверка на null для claimId
        if (claimId == null) {
            plugin.getLogger().warning("deleteClaim called with null claimId.");
            return;
        }
        invokeBest(methodName("delete-claim", "deleteClaim"), new Object[]{claimId}, new Object[]{claimId.toString()});
    }

    public boolean isClaimed(Location location) {
        if (!enabled() || api == null) {
            return false;
        }
        // Защита от дурачков: Проверка на null для location
        if (location == null) {
            plugin.getLogger().warning("isClaimed called with null location.");
            return false;
        }
        // AdvancedClaims API may return Optional<?> itself, so use extractClaimId
        // to properly unwrap nested Optionals and avoid false positives.
        Optional<Object> result = getClaimAt(location);
        if (result.isEmpty()) return false;
        Object value = result.get();
        if (value instanceof Optional<?> inner) {
            return inner.isPresent();
        }
        return true;
    }

    public Optional<Object> getClaimAt(Location location) {
        if (!enabled() || api == null) {
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для location
        if (location == null) {
            plugin.getLogger().warning("getClaimAt called with null location.");
            return Optional.empty();
        }
        return invokeBest(methodName("get-claim-at", "getClaimAt"), new Object[]{location});
    }

    public Optional<UUID> getClaimOwner(Location location) {
        if (!enabled() || api == null) {
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для location
        if (location == null) {
            plugin.getLogger().warning("getClaimOwner called with null location.");
            return Optional.empty();
        }
        return getClaimAt(location).flatMap(this::extractClaimId);
    }

    /**
     * Синхронизирует права всех членов клана для указанной территории AdvancedClaims.
     * Вызывается при создании территории или при загрузке клана.
     *
     * @param clan Клан
     * @param territory Территория клана
     */
    public void syncClanTrust(Clan clan, ClanTerritory territory) {
        if (!enabled() || api == null) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (clan == null || territory == null || territory.advancedClaimId() == null) {
            plugin.getLogger().warning("syncClanTrust called with null clan, territory, or advancedClaimId.");
            return;
        }
        // AdvancedClaimsAPI.addPlayerToClaim принимает Claim, а не UUID.
        // Поэтому сначала нужно получить объект Claim.
        findClaim(territory.advancedClaimId()).ifPresent(claimObject -> {
            for (ClanMember member : clan.members().values()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(member.playerId());
                // Защита от дурачков: Проверка на null для OfflinePlayer
                if (player == null) {
                    plugin.getLogger().warning("syncClanTrust: OfflinePlayer is null for member " + member.playerId());
                    continue;
                }
                updatePlayerTrust(claimObject, player, member.rank());
            }
        });
    }

    /**
     * Обновляет права конкретного игрока в привате AdvancedClaims на основе его ранга в клане.
     *
     * @param claimObject Объект привата AdvancedClaims (не UUID)
     * @param player Игрок
     * @param rank Ранг игрока в клане
     */
    public void updatePlayerTrust(Object claimObject, OfflinePlayer player, ClanRank rank) {
        if (!enabled() || api == null) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (claimObject == null || player == null || rank == null) {
            plugin.getLogger().warning("updatePlayerTrust called with null claimObject, player, or rank.");
            return;
        }

        Object trustLevel = trustLevel(rank);
        invokeBest(methodName("add-player", "addPlayerToClaim"),
                new Object[]{claimObject, player, trustLevel});
    }

    /**
     * Удаляет игрока из привата AdvancedClaims.
     *
     * @param claimObject Объект привата AdvancedClaims (не UUID)
     * @param player Игрок
     */
    public void removePlayerTrust(Object claimObject, OfflinePlayer player) {
        if (!enabled() || api == null) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (claimObject == null || player == null) {
            plugin.getLogger().warning("removePlayerTrust called with null claimObject or player.");
            return;
        }
        invokeBest(methodName("remove-player", "removePlayerFromClaim"),
                new Object[]{claimObject, player});
    }

    public Optional<Object> findClaim(UUID claimId) {
        if (!enabled() || api == null) { // Добавлена проверка
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для claimId
        if (claimId == null) {
            plugin.getLogger().warning("findClaim called with null claimId.");
            return Optional.empty();
        }
        Object value = invokeBest(methodName("get-claim-by-id", "getClaimById"), new Object[]{claimId}, new Object[]{claimId.toString()}).orElse(null);
        if (value instanceof Optional<?> optional) {
            return optional.map(object -> object);
        }
        return Optional.ofNullable(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object trustLevel(ClanRank rank) {
        String defaultTrustLevel;
        // Определяем дефолтный TrustLevel в зависимости от ClanRank
        if (rank == null) {
            plugin.getLogger().warning("trustLevel called with null ClanRank. Defaulting to CONTAINER.");
            defaultTrustLevel = "CONTAINER";
        } else {
            switch (rank) {
                case LEADER:
                case GUARDIAN:
                    defaultTrustLevel = "BUILD";
                    break;
                case MEMBER:
                case RECRUIT:
                default: // Should not happen if all ranks are covered
                    defaultTrustLevel = "CONTAINER";
                    break;
            }
        }

        String configured = plugin.getConfig().getString("integration.advanced-claims.trust-mapping." + rank.name(), defaultTrustLevel);

        if (trustLevelClass != null && trustLevelClass.isEnum()) {
            try {
                return Enum.valueOf((Class<? extends Enum>) trustLevelClass.asSubclass(Enum.class), configured.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid TrustLevel mapping for ClanRank " + rank.name() + ": " + configured + ". Defaulting to " + defaultTrustLevel + ".", e);
                return Enum.valueOf((Class<? extends Enum>) trustLevelClass.asSubclass(Enum.class), defaultTrustLevel);
            }
        }
        return configured; // Fallback if TrustLevel is not an enum or class not found
    }

    private Optional<UUID> extractClaimId(Object claim) {
        if (claim == null) {
            return Optional.empty();
        }
        if (claim instanceof Optional<?> optional) {
            return optional.flatMap(this::extractClaimId);
        }
        if (claim instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (claim instanceof String string) {
            try {
                return Optional.of(UUID.fromString(string));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        for (String method : new String[]{"getId", "id", "getUniqueId", "uniqueId", "getOwnerId", "ownerId"}) {
            try {
                Object value = claim.getClass().getMethod(method).invoke(claim);
                Optional<UUID> uuid = extractClaimId(value);
                if (uuid.isPresent()) {
                    return uuid;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<Object> invokeBest(String methodName, Object[]... candidates) {
        if (!enabled() || api == null) {
            plugin.getLogger().fine("AdvancedClaimsHook is not enabled or API is null. Cannot invoke method " + methodName);
            return Optional.empty();
        }
        for (Object[] args : candidates) {
            Optional<Method> method = findCompatibleMethod(methodName, args);
            if (method.isEmpty()) {
                plugin.getLogger().fine("No compatible method found for " + methodName + " with arguments: " + java.util.Arrays.toString(args));
                continue;
            }
            try {
                return Optional.ofNullable(method.get().invoke(api, args));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for " + methodName + " with arguments " + java.util.Arrays.toString(args) + ": " + exception.getMessage(), exception);
            }
        }
        plugin.getLogger().warning("Failed to invoke method " + methodName + " after trying all candidates.");
        return Optional.empty();
    }

    private Optional<Method> findCompatibleMethod(String methodName, Object[] args) {
        if (api == null) {
            return Optional.empty();
        }
        for (Method method : api.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                // Handle primitive types and null arguments
                if (args[index] == null) {
                    // If the argument is null, it can be assigned to any non-primitive type.
                    // If the parameter type is primitive, null is not compatible.
                    if (parameterTypes[index].isPrimitive()) {
                        compatible = false;
                        break;
                    }
                } else if (!wrap(parameterTypes[index]).isAssignableFrom(args[index].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                method.setAccessible(true);
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }


    private String methodName(String pathName, String fallback) {
        return plugin.getConfig().getString("integration.advanced-claims.methods." + pathName, fallback);
    }

    public void showClaimBorder(Player player, BoundingBox box, long durationTicks) {
        if (!enabled() || api == null || showBorderMethod == null) return;
        // Защита от дурачков: Проверка на null для входных параметров
        if (player == null || box == null) {
            plugin.getLogger().warning("showClaimBorder called with null player or bounding box.");
            return;
        }
        try {
            showBorderMethod.invoke(api, player, box, durationTicks);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to invoke showBorder method from AdvancedClaimsAPI", e);
        }
    }

    public void hideClaimBorder(Player player) {
        if (!enabled() || api == null || hideBorderMethod == null) return;
        // Защита от дурачков: Проверка на null для player
        if (player == null) {
            plugin.getLogger().warning("hideBorder called with null player.");
            return;
        }
        try {
            hideBorderMethod.invoke(api, player);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to invoke hideBorder method from AdvancedClaimsAPI", e);
        }
    }
}