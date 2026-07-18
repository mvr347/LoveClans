package me.lovelace.loveclans.integration;

import me.lovelace.loveclaims.api.LoveClaimsAPI;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
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

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Прямая (не reflection-based) интеграция с LoveClaims. LoveClaims подключён как
 * provided-зависимость в pom.xml — на этапе компиляции его классы доступны, но в
 * собранный jar LoveClans не зашиваются (см. scope=provided), поэтому в рантайме
 * AdvancedClaimsHook резолвит реальный класс LoveClaimsAPI из работающего плагина
 * LoveClaims, а не свою отдельную копию.
 */
public final class AdvancedClaimsHook {
    private final LoveClansPlugin plugin;
    private LoveClaimsAPI api;
    private boolean enabled;

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

        // Защита от дурачков: LoveClaims может быть в стадии onEnable и ещё не успеть
        // вызвать LoveClaimsAPI.init(...) к моменту нашей инициализации (порядок softdepend
        // гарантирует только то, что плагин включён, а не то, что его API готов).
        if (!LoveClaimsAPI.isInitialized()) {
            enabled = false;
            api = null;
            plugin.getLogger().warning("LoveClaimsAPI ещё не инициализирован (плагин включён, но API не готов). Интеграция отключена для этого запуска.");
            return;
        }

        try {
            api = LoveClaimsAPI.getInstance();
            plugin.getLogger().info("AdvancedClaimsAPI integration enabled.");
        } catch (IllegalStateException exception) {
            enabled = false;
            api = null;
            plugin.getLogger().warning("LoveClaimsAPI не инициализирован: " + exception.getMessage() + ". Интеграция с LoveClaims отключена.");
        }
    }

    public boolean enabled() {
        return enabled && api != null;
    }

    public Optional<UUID> createOrAttachClaim(Clan clan, ClanTerritory territory) {
        if (!enabled() || !plugin.getConfig().getBoolean("integration.advanced-claims.auto-claim-chunk", true)) {
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
            int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 12);
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
        Claim claim;
        try {
            claim = api.createClanClaim(world, box, clan.id(), centerLoc, clanOwnerDisplayName(clan));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for createClanClaim: " + exception.getMessage(), exception);
            return Optional.empty();
        }
        if (claim == null) {
            return Optional.empty();
        }

        UUID claimId = claim.getId();
        syncClanTrust(clan, territory.withAdvancedClaimId(claimId));
        return Optional.of(claimId);
    }

    /**
     * Отображаемое имя владельца территории для LoveClaims (надпись "владелец: ..." при входе
     * на территорию) - название клана плюс его тег в цвете, в том же формате "<name> [<tag>]",
     * что используется во всех остальных местах плагина (см. Clan#coloredTag()).
     */
    private String clanOwnerDisplayName(Clan clan) {
        return clan.name() + " [" + clan.coloredTag() + "]";
    }

    /**
     * Обновляет отображаемое имя клана-владельца во всех его приватах LoveClaims. Нужно вызывать
     * при переименовании клана или смене тега, иначе надпись при входе на территорию останется
     * старой до следующего пересоздания привата.
     */
    public void updateClanOwnerDisplayName(Clan clan) {
        if (!enabled()) {
            return;
        }
        try {
            api.updateClanClaimOwnerName(clan.id(), clanOwnerDisplayName(clan));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for updateClanClaimOwnerName: " + exception.getMessage(), exception);
        }
    }

    public void deleteClaim(UUID claimId) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для claimId
        if (claimId == null) {
            plugin.getLogger().warning("deleteClaim called with null claimId.");
            return;
        }
        try {
            api.deleteClaim(claimId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for deleteClaim: " + exception.getMessage(), exception);
        }
    }

    public boolean isClaimed(Location location) {
        return getClaimAt(location).isPresent();
    }

    public Optional<Claim> getClaimAt(Location location) {
        if (!enabled()) {
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для location
        if (location == null) {
            plugin.getLogger().warning("getClaimAt called with null location.");
            return Optional.empty();
        }
        try {
            return api.getClaimAt(location);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for getClaimAt: " + exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    public Optional<UUID> getClaimOwner(Location location) {
        // Возвращаем владельца привата (clan.id() для клановых территорий), а не ID самого привата.
        return getClaimAt(location).map(Claim::getOwnerUuid);
    }

    /**
     * Синхронизирует права всех членов клана для указанной территории AdvancedClaims.
     * Вызывается при создании территории или при загрузке клана.
     *
     * @param clan Клан
     * @param territory Территория клана
     */
    public void syncClanTrust(Clan clan, ClanTerritory territory) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (clan == null || territory == null || territory.advancedClaimId() == null) {
            plugin.getLogger().warning("syncClanTrust called with null clan, territory, or advancedClaimId.");
            return;
        }
        findClaim(territory.advancedClaimId()).ifPresent(claim -> {
            for (ClanMember member : clan.members().values()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(member.playerId());
                updatePlayerTrust(claim, player, member.rank());
            }
        });
    }

    /**
     * Обновляет права конкретного игрока в привате AdvancedClaims на основе его ранга в клане.
     *
     * @param claim Приват AdvancedClaims
     * @param player Игрок
     * @param rank Ранг игрока в клане
     */
    public void updatePlayerTrust(Claim claim, OfflinePlayer player, ClanRank rank) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (claim == null || player == null || rank == null) {
            plugin.getLogger().warning("updatePlayerTrust called with null claim, player, or rank.");
            return;
        }
        try {
            api.addPlayerToClaim(claim, player, trustLevel(rank));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for addPlayerToClaim: " + exception.getMessage(), exception);
        }
    }

    /**
     * Удаляет игрока из привата AdvancedClaims.
     *
     * @param claim Приват AdvancedClaims
     * @param player Игрок
     */
    public void removePlayerTrust(Claim claim, OfflinePlayer player) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (claim == null || player == null) {
            plugin.getLogger().warning("removePlayerTrust called with null claim or player.");
            return;
        }
        try {
            api.removePlayerFromClaim(claim, player);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for removePlayerFromClaim: " + exception.getMessage(), exception);
        }
    }

    public Optional<Claim> findClaim(UUID claimId) {
        if (!enabled()) {
            return Optional.empty();
        }
        // Защита от дурачков: Проверка на null для claimId
        if (claimId == null) {
            plugin.getLogger().warning("findClaim called with null claimId.");
            return Optional.empty();
        }
        try {
            return api.getClaimById(claimId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for getClaimById: " + exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    private TrustLevel trustLevel(ClanRank rank) {
        TrustLevel defaultTrustLevel;
        // Определяем дефолтный TrustLevel в зависимости от ClanRank
        if (rank == null) {
            plugin.getLogger().warning("trustLevel called with null ClanRank. Defaulting to CONTAINER.");
            return TrustLevel.CONTAINER;
        }
        switch (rank) {
            case LEADER:
            case GUARDIAN:
                defaultTrustLevel = TrustLevel.BUILD;
                break;
            case MEMBER:
            case RECRUIT:
            default: // Should not happen if all ranks are covered
                defaultTrustLevel = TrustLevel.CONTAINER;
                break;
        }

        String configured = plugin.getConfig().getString("integration.advanced-claims.trust-mapping." + rank.name(), defaultTrustLevel.name());
        try {
            return TrustLevel.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid TrustLevel mapping for ClanRank " + rank.name() + ": " + configured + ". Defaulting to " + defaultTrustLevel + ".", e);
            return defaultTrustLevel;
        }
    }

    /**
     * Включает/выключает режим осады у привата LoveClaims, привязанного к территории клана.
     * Пока приват в осаде, LoveClaims сам запрещает вражескому клану строить/телепортироваться/
     * взаимодействовать на этой территории и разрешает ломать только знамя (см. ProtectionListener
     * и AnchorListener в LoveClaims). Вызывается при начале и окончании войны за территорию.
     */
    public void setSiegeMode(UUID claimId, boolean active) {
        if (!enabled() || claimId == null) {
            return;
        }
        try {
            api.setSiegeMode(claimId, active);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for setSiegeMode: " + exception.getMessage(), exception);
        }
    }

    public void showClaimBorder(Player player, BoundingBox box, long durationTicks) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (player == null || box == null) {
            plugin.getLogger().warning("showClaimBorder called with null player or bounding box.");
            return;
        }
        try {
            api.showBorder(player, box, durationTicks);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to invoke showBorder method from AdvancedClaimsAPI", exception);
        }
    }

    public void hideClaimBorder(Player player) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для player
        if (player == null) {
            plugin.getLogger().warning("hideBorder called with null player.");
            return;
        }
        try {
            api.hideBorder(player);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to invoke hideBorder method from AdvancedClaimsAPI", exception);
        }
    }
}
