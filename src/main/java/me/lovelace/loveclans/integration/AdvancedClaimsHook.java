package me.lovelace.loveclans.integration;

import me.lovelace.loveclaims.api.LoveClaimsAPI;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
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

    /** Чем закончилась попытка завести приват под клановую территорию. */
    public enum AttachResult {
        /** Приват создан. */
        CREATED,
        /** Интеграция выключена — территория берётся без привата, как было до неё. */
        SKIPPED,
        /** LoveClaims отказал: земля занята чужим приватом либо вызов не удался. */
        REFUSED
    }

    /**
     * Отличать отказ от выключенной интеграции обязательно: раньше и то, и другое возвращалось
     * пустым Optional, и клан забирал территорию даже когда приват завести не удалось —
     * то есть считал своей землю, которая в реестре приватов принадлежит другому.
     */
    public record ClaimAttachment(AttachResult result, UUID claimId) {

        static ClaimAttachment created(UUID claimId) {
            return new ClaimAttachment(AttachResult.CREATED, claimId);
        }

        static ClaimAttachment skipped() {
            return new ClaimAttachment(AttachResult.SKIPPED, null);
        }

        static ClaimAttachment refused() {
            return new ClaimAttachment(AttachResult.REFUSED, null);
        }
    }

    /**
     * Единая формула геометрии территории вокруг знамени — вызывается и здесь (при заведении
     * привата LoveClaims под территорию), и из {@code ClanManager#initiateClaimConfirmation}
     * (превью до подтверждения захвата). Раньше обе стороны считали рамку одной и той же формулой
     * в двух независимых местах — если конфиг радиуса менялся между вызовами (или один call site
     * забывали обновить), рамки расходились (см. доку {@link ClanTerritory} о том, почему теперь
     * геометрия территории вообще берётся только из LoveClaims после создания привата).
     *
     * <p>Вертикаль — больше не вся высота мира (это защищало приватом целый столбец до бедрока и
     * дальше в небо), а настраиваемая полоса вокруг Y знамени: {@code claim-height-below} блоков
     * вниз и {@code claim-height-above} вверх, обрезанная границами мира.
     */
    public static BoundingBox computeTerritoryBounds(LoveClansPlugin plugin, int bannerX, int bannerY, int bannerZ, World world) {
        int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 35);
        int heightBelow = plugin.getConfig().getInt("integration.advanced-claims.claim-height-below", 64);
        int heightAbove = plugin.getConfig().getInt("integration.advanced-claims.claim-height-above", 128);
        int minY = Math.max(world.getMinHeight(), bannerY - heightBelow);
        int maxY = Math.min(world.getMaxHeight(), bannerY + heightAbove);
        return new BoundingBox(
                bannerX - radius, minY, bannerZ - radius,
                bannerX + radius, maxY, bannerZ + radius
        );
    }

    /**
     * Заводит приват LoveClaims под клановую территорию. Клановая территория без привата не
     * существует — геометрия территории (для войн, осад, защиты) теперь берётся только отсюда,
     * см. {@link #boundingBoxOf(ClanTerritory)}. Поэтому в отличие от прежней версии это больше
     * не мягкая попытка: если ядро выключено или LoveClaims отказал, территорию брать нельзя.
     */
    public ClaimAttachment createOrAttachClaim(Clan clan, ClanTerritory territory) {
        if (!enabled()) {
            return ClaimAttachment.skipped();
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (clan == null || territory == null) {
            plugin.getLogger().warning("createOrAttachClaim called with null clan or territory.");
            return ClaimAttachment.refused();
        }

        World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            plugin.getLogger().warning("createOrAttachClaim called for a non-existent world: " + territory.world());
            return ClaimAttachment.refused();
        }

        if (territory.bannerX() == null || territory.bannerY() == null || territory.bannerZ() == null) {
            // Баннер всегда ставится в ClanManager#confirmPendingClaim до вызова этого метода —
            // если координат нет, значит территория заведена в обход обычного потока (или
            // повреждена), а геометрию посчитать не от чего.
            plugin.getLogger().warning("createOrAttachClaim: territory " + territory.id() + " has no banner coordinates.");
            return ClaimAttachment.refused();
        }

        BoundingBox box = computeTerritoryBounds(plugin, territory.bannerX(), territory.bannerY(), territory.bannerZ(), world);
        Location centerLoc = new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());

        // Use createClanClaim instead of createClaim for clan territories.
        // The owner is the clan's UUID, not the leader's.
        Claim claim;
        try {
            claim = api.createClanClaim(world, box, clan.id(), centerLoc, clanOwnerDisplayName(clan));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "AdvancedClaims call failed for createClanClaim: " + exception.getMessage(), exception);
            return ClaimAttachment.refused();
        }
        if (claim == null) {
            // LoveClaims отказал — обычно потому, что границы пересекают чужой приват.
            // Приваты клана и игрока не вкладываются друг в друга, поэтому территорию
            // здесь брать нельзя.
            return ClaimAttachment.refused();
        }

        UUID claimId = claim.getId();
        syncClanTrust(clan, territory.withAdvancedClaimId(claimId));
        return ClaimAttachment.created(claimId);
    }

    /**
     * Геометрия территории — единственный источник истины теперь LoveClaims. Пусто означает
     * либо ядро/LoveClaims недоступны прямо сейчас, либо приват был удалён в обход LoveClans;
     * вызывающий обязан считать это «геометрия неизвестна», а не «территория без границ».
     */
    public Optional<BoundingBox> boundingBoxOf(ClanTerritory territory) {
        if (territory == null || territory.advancedClaimId() == null) {
            return Optional.empty();
        }
        return findClaim(territory.advancedClaimId()).map(Claim::getBoundingBox);
    }

    /** Внутри территории ли точка — false, если геометрия сейчас недоступна (см. {@link #boundingBoxOf}). */
    public boolean contains(ClanTerritory territory, Location location) {
        if (location == null) {
            return false;
        }
        return boundingBoxOf(territory).map(box -> box.contains(location.toVector())).orElse(false);
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
                updatePlayerTrust(claim, player, member.rank(), clan);
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
        updatePlayerTrust(claim, player, rank, null);
    }

    /**
     * То же, но с оглядкой на право BUILD этого ранга в клане. Раньше право BUILD нигде не
     * проверялось: доступ на клановой земле определялся только рангом, и снять у ранга
     * строительство через меню прав было невозможно — галочка стояла, а стройка шла.
     *
     * @param clan клан, чьи права рангов учитываются; {@code null} — считать по одному рангу
     */
    public void updatePlayerTrust(Claim claim, OfflinePlayer player, ClanRank rank, Clan clan) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (claim == null || player == null || rank == null) {
            plugin.getLogger().warning("updatePlayerTrust called with null claim, player, or rank.");
            return;
        }
        try {
            api.addPlayerToClaim(claim, player, trustLevel(rank, clan));
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

    private TrustLevel trustLevel(ClanRank rank, Clan clan) {
        TrustLevel resolved = configuredTrustLevel(rank);

        // Глава строит всегда; остальным ранг понижается до CONTAINER, если право BUILD снято
        // в меню прав клана. Иначе настройка прав была бы наполовину декоративной.
        if (clan != null && rank != ClanRank.LEADER
                && resolved == TrustLevel.BUILD
                && !clan.getPermission(rank, me.lovelace.loveclans.model.ClanPermission.BUILD)) {
            return TrustLevel.CONTAINER;
        }
        return resolved;
    }

    private TrustLevel configuredTrustLevel(ClanRank rank) {
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
        showClaimBorder(player, box, durationTicks, null);
    }

    /**
     * Показывает границы территории. Если известен {@code claimId} (реальный приват LoveClaims,
     * см. {@link ClanTerritory#advancedClaimId()}), визуализация 1-в-1 совпадает с тем, как
     * LoveClaims сама подсвечивает клановые территории (принудительно красное стекло, а не
     * настройка {@code border.material} по умолчанию) — иначе визуал расходится с тем, что видно
     * при обычном входе/выходе или из GUI самой LoveClaims.
     */
    public void showClaimBorder(Player player, BoundingBox box, long durationTicks, UUID claimId) {
        if (!enabled()) {
            return;
        }
        // Защита от дурачков: Проверка на null для входных параметров
        if (player == null || box == null) {
            plugin.getLogger().warning("showClaimBorder called with null player or bounding box.");
            return;
        }
        try {
            if (claimId != null) {
                api.showBorder(player, box, durationTicks, claimId);
            } else {
                api.showBorder(player, box, durationTicks);
            }
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
