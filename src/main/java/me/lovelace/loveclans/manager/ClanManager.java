package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanClaimEvent;
import me.lovelace.loveclans.api.events.ClanCreateEvent;
import me.lovelace.loveclans.api.events.ClanDiplomacyChangeEvent;
import me.lovelace.loveclans.api.events.ClanDisbandEvent;
import me.lovelace.loveclans.api.events.ClanLevelUpEvent;
import me.lovelace.loveclans.api.events.ClanMemberJoinEvent;
import me.lovelace.loveclans.api.events.ClanMemberLeaveEvent;
import me.lovelace.loveclans.api.events.ClanRankChangeEvent;
import me.lovelace.loveclans.api.events.ClanUnclaimEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanInvite;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.storage.ClanStorage;
import me.lovelace.loveclans.util.ClanItemFactory;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ClanManager {
    private final LoveClansPlugin plugin;
    private final ClanStorage storage;
    private final ClanItemFactory clanItemFactory;
    private final Map<UUID, Clan> clansById = new ConcurrentHashMap<>();
    private final Map<String, UUID> clanByTag = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> clanByPlayer = new ConcurrentHashMap<>();
    private final Map<TerritoryKey, UUID> clanByTerritory = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClanInvite>> invitesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClanApplication>> applicationsByClan = new ConcurrentHashMap<>();
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();

    public ClanManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.clanItemFactory = new ClanItemFactory(plugin);
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllClansAsync().thenAccept(clans -> {
            clansById.clear();
            clanByTag.clear();
            clanByPlayer.clear();
            clanByTerritory.clear();
            invitesByPlayer.clear();
            applicationsByClan.clear();
            for (Clan clan : clans) {
                indexClan(clan);
            }
            plugin.getLogger().info("Loaded " + clans.size() + " clans.");
        }).thenCompose(v -> storage.loadAllApplicationsAsync().thenAccept(applications -> {
            for (ClanApplication application : applications) {
                applicationsByClan.computeIfAbsent(application.clanId(), k -> new ArrayList<>()).add(application);
            }
            plugin.getLogger().info("Loaded " + applications.size() + " clan applications.");
        }));
    }

    public Optional<Clan> getClanById(UUID clanId) {
        return Optional.ofNullable(clansById.get(clanId));
    }

    public Optional<Clan> getClanByTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        UUID id = clanByTag.get(normalizeTag(tag));
        return id == null ? Optional.empty() : getClanById(id);
    }

    public Optional<Clan> getPlayerClan(UUID playerId) {
        if (playerId == null) return Optional.empty();
        UUID clanId = clanByPlayer.get(playerId);
        return clanId == null ? Optional.empty() : getClanById(clanId);
    }

    public Optional<Clan> getClanAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        if (plugin.getAdvancedClaimsHook().enabled()) {
            Optional<UUID> ownerId = plugin.getAdvancedClaimsHook().getClaimOwner(location);
            if (ownerId.isPresent()) {
                return getClanById(ownerId.get());
            }
        }

        return getClanAt(TerritoryKey.fromLocation(location));
    }

    public Optional<Clan> getClanAt(TerritoryKey key) {
        if (key == null) return Optional.empty();
        UUID clanId = clanByTerritory.get(key);
        return clanId == null ? Optional.empty() : getClanById(clanId);
    }

    public Collection<Clan> getAllClans() {
        return List.copyOf(clansById.values());
    }

    public List<ClanApplication> getClanApplications(UUID clanId) {
        if (clanId == null) return List.of();
        return List.copyOf(applicationsByClan.getOrDefault(clanId, List.of()));
    }

    public List<ClanInvite> getClanInvites(UUID clanId) {
        if (clanId == null) return List.of();
        long now = System.currentTimeMillis();
        return invitesByPlayer.values().stream()
                .flatMap(List::stream)
                .filter(invite -> invite.clanId().equals(clanId) && !invite.expired(now))
                .toList();
    }

    public List<ClanInvite> getPlayerInvites(UUID playerId) {
        if (playerId == null) return List.of();
        long now = System.currentTimeMillis();
        return invitesByPlayer.getOrDefault(playerId, List.of()).stream()
                .filter(invite -> !invite.expired(now))
                .toList();
    }

    public void removeInvite(UUID playerId, UUID clanId) {
        if (playerId == null || clanId == null) return;
        List<ClanInvite> invites = invitesByPlayer.get(playerId);
        if (invites != null) invites.removeIf(inv -> inv.clanId().equals(clanId));
    }

    public CompletableFuture<Void> rejectApplicationSelfAsync(UUID clanId, UUID applicantId) {
        if (clanId == null || applicantId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan ID and Applicant ID cannot be null."));
        return plugin.supplySync(() -> {
            List<ClanApplication> applications = applicationsByClan.computeIfAbsent(clanId, k -> new ArrayList<>());
            applications.removeIf(app -> app.applicantId().equals(applicantId));
            return null;
        }).thenCompose(v -> storage.deleteApplicationAsync(clanId, applicantId));
    }

    private final Map<UUID, UUID> pendingAllianceRequests = new ConcurrentHashMap<>();

    public void addAllianceRequest(UUID sourceClanId, UUID targetClanId) {
        if (sourceClanId == null || targetClanId == null) return;
        pendingAllianceRequests.put(sourceClanId, targetClanId);
    }

    public boolean hasPendingAllianceFrom(UUID sourceClanId, UUID targetClanId) {
        if (sourceClanId == null || targetClanId == null) return false;
        return targetClanId.equals(pendingAllianceRequests.get(sourceClanId));
    }

    public void removeAllianceRequest(UUID sourceClanId) {
        if (sourceClanId == null) return;
        pendingAllianceRequests.remove(sourceClanId);
    }

    public CompletableFuture<Void> acceptAllianceAsync(Clan acceptorClan, Clan requesterClan, UUID actorId) {
        if (acceptorClan == null || requesterClan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clans and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!acceptorClan.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            pendingAllianceRequests.remove(requesterClan.id());
            acceptorClan.setDiplomacy(requesterClan.id(), DiplomacyRelation.ALLY);
            requesterClan.setDiplomacy(acceptorClan.id(), DiplomacyRelation.ALLY);
            Bukkit.getPluginManager().callEvent(new ClanDiplomacyChangeEvent(acceptorClan.id(), requesterClan.id(), DiplomacyRelation.ALLY));
            return null;
        }).thenCompose(v -> storage.saveDiplomacyAsync(acceptorClan.id(), requesterClan.id(), DiplomacyRelation.ALLY)
                .thenCompose(x -> storage.saveDiplomacyAsync(requesterClan.id(), acceptorClan.id(), DiplomacyRelation.ALLY)));
    }

    public CompletableFuture<Void> declineAllianceAsync(Clan declinerClan, Clan requesterClan, UUID actorId) {
        if (declinerClan == null || requesterClan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clans and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!declinerClan.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            pendingAllianceRequests.remove(requesterClan.id());
            return null;
        });
    }

    public Optional<Player> getOnlineLeader(Clan clan) {
        if (clan == null) return Optional.empty();
        return clan.leaderId().flatMap(id -> Optional.ofNullable(Bukkit.getPlayer(id)));
    }

    public Optional<Player> getOnlineGuardianOrLeader(Clan clan) {
        if (clan == null) return Optional.empty();
        Optional<Player> leader = getOnlineLeader(clan);
        if (leader.isPresent()) return leader;

        return clan.members().values().stream()
                .filter(member -> member.rank() == ClanRank.GUARDIAN)
                .map(member -> Bukkit.getPlayer(member.playerId()))
                .filter(Objects::nonNull)
                .findFirst();
    }

    // Возвращает всех онлайн-участников клана, у которых есть указанное право
    // (например, INVITE) — используется для рассылки уведомлений о заявках в клан.
    public List<Player> getOnlineMembersWithPermission(Clan clan, ClanPermission permission) {
        if (clan == null) return List.of();
        return clan.members().values().stream()
                .filter(member -> clan.getPermission(member.rank(), permission))
                .map(member -> Bukkit.getPlayer(member.playerId()))
                .filter(Objects::nonNull)
                .toList();
    }

    public ItemStack getClanHomeBanner(Clan clan) {
        if (clan == null) return new ItemStack(Material.AIR);
        return clanItemFactory.createCapitalBanner(clan.id(), clan.name());
    }

    public CompletableFuture<Clan> createClanAsync(String name, String tag, UUID founderId, boolean open) {
        if (founderId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Founder ID cannot be null."));
        return plugin.supplySync(() -> {
            validateName(name);
            validateTag(tag);
            if (getPlayerClan(founderId).isPresent()) {
                throw new IllegalStateException("clan.already-in-clan");
            }
            if (getClanByTag(tag).isPresent()) {
                throw new IllegalStateException("clan.tag-exists");
            }

            Player founder = Bukkit.getPlayer(founderId);
            if (founder != null && clanItemFactory.hasExistingBanner(founder, "CAPITAL", null)) {
                throw new IllegalStateException("clan.founder-has-capital-banner");
            }

            Material emblem = Material.matchMaterial(plugin.getConfig().getString("clans.default-emblem", "WHITE_BANNER"));
            if (emblem == null) {
                emblem = Material.WHITE_BANNER;
            }
            Clan clan = Clan.create(UUID.randomUUID(), name, tag, plugin.getConfig().getString("clans.default-tag-color", "<gold>"), emblem,
                    founderId, plugin.getConfig().getInt("limits.base-chest-rows", 3), open);
            ClanCreateEvent event = new ClanCreateEvent(clan, founderId);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            indexClan(clan);

            if (founder != null) {
                founder.getInventory().addItem(clanItemFactory.createCapitalBanner(clan.id(), clan.name()));
                plugin.getMessages().send(founder, "territory.banner-given");
            }

            return clan;
        }).thenCompose(clan -> storage.saveClanAsync(clan).thenApply(ignored -> clan));
    }

    public CompletableFuture<Void> disbandClanAsync(Clan clan, UUID actorId) {
        if (clan == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Clan cannot be null."));
        return plugin.supplySync(() -> {
            if (actorId != null && !clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            ClanDisbandEvent event = new ClanDisbandEvent(clan, actorId);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            for (UUID memberId : clan.members().keySet()) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null) {
                    player.closeInventory();
                }
            }
            for (ClanTerritory territory : clan.territories()) {
                plugin.getAdvancedClaimsHook().deleteClaim(territory.advancedClaimId());
                clanByTerritory.remove(territory.key());
            }
            unindexClan(clan);
            applicationsByClan.remove(clan.id());
            plugin.getWarManager().purgeClan(clan.id());
            plugin.getRitualManager().purgeClan(clan.id());
            plugin.getSpiritManager().purgeClan(clan.id());
            return null;
        }).thenCompose(ignored -> storage.deleteClanAsync(clan.id()).thenCompose(v -> storage.deleteAllApplicationsForClanAsync(clan.id())));
    }

    public CompletableFuture<ClanInvite> invitePlayerAsync(Clan clan, UUID inviterId, UUID invitedPlayerId) {
        if (clan == null || inviterId == null || invitedPlayerId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and player IDs cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(inviterId, ClanPermission.INVITE)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (plugin.getWarManager().isAtWar(clan.id())) {
                throw new IllegalStateException("gui.capital.war-blocked");
            }
            if (clan.hasMember(invitedPlayerId) || getPlayerClan(invitedPlayerId).isPresent()) {
                throw new IllegalStateException("clan.already-in-clan");
            }
            if (isClanFull(clan)) {
                throw new IllegalStateException("clan.member-limit-reached");
            }
            long expiresAt = System.currentTimeMillis() + plugin.getConfig().getLong("clans.invite-expire-seconds", 120L) * 1000L;
            ClanInvite invite = new ClanInvite(clan.id(), invitedPlayerId, inviterId, expiresAt);
            invitesByPlayer.computeIfAbsent(invitedPlayerId, ignored -> new ArrayList<>()).removeIf(old -> old.clanId().equals(clan.id()) || old.expired(System.currentTimeMillis()));
            invitesByPlayer.computeIfAbsent(invitedPlayerId, ignored -> new ArrayList<>()).add(invite);
            return invite;
        });
    }

    public CompletableFuture<Clan> acceptInviteAsync(UUID playerId, String tag) {
        if (playerId == null || tag == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player ID and tag cannot be null."));
        return plugin.supplySync(() -> {
            Clan clan = getClanByTag(tag).orElseThrow(() -> new IllegalStateException("clan.invite-missing"));
            List<ClanInvite> invites = invitesByPlayer.getOrDefault(playerId, List.of());
            long now = System.currentTimeMillis();
            ClanInvite invite = invites.stream()
                    .filter(candidate -> candidate.clanId().equals(clan.id()) && !candidate.expired(now))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("clan.invite-missing"));
            invites.remove(invite);
            return clan;
        }).thenCompose(clan -> addMemberAsync(clan, playerId, ClanRank.RECRUIT));
    }

    public CompletableFuture<ClanApplication> applyToClanAsync(Clan clan, UUID applicantId) {
        if (clan == null || applicantId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and applicant ID cannot be null."));
        return plugin.supplySync(() -> {
            if (getPlayerClan(applicantId).isPresent()) {
                throw new IllegalStateException("clan.already-in-clan");
            }
            if (isClanFull(clan)) {
                throw new IllegalStateException("clan.member-limit-reached");
            }
            List<ClanApplication> applications = applicationsByClan.computeIfAbsent(clan.id(), ignored -> new ArrayList<>());
            boolean alreadyApplied = applications.stream().anyMatch(app -> app.applicantId().equals(applicantId));
            if (alreadyApplied) {
                throw new IllegalStateException("clan.already-applied");
            }

            ClanApplication application = new ClanApplication(clan.id(), applicantId, System.currentTimeMillis());
            applications.add(application);
            return application;
        }).thenCompose(application -> storage.saveApplicationAsync(application).thenApply(ignored -> application));
    }

    public CompletableFuture<Clan> acceptApplicationAsync(Clan clan, UUID actorId, UUID applicantId) {
        if (clan == null || actorId == null || applicantId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and applicant IDs cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.INVITE)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (plugin.getWarManager().isAtWar(clan.id())) {
                throw new IllegalStateException("gui.capital.war-blocked");
            }
            List<ClanApplication> applications = applicationsByClan.computeIfAbsent(clan.id(), ignored -> new ArrayList<>());
            ClanApplication application = applications.stream()
                    .filter(app -> app.applicantId().equals(applicantId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("clan.application-not-found"));
            applications.remove(application);
            return clan;
        }).thenCompose(c -> addMemberAsync(c, applicantId, ClanRank.RECRUIT)
                .thenCompose(updatedClan -> storage.deleteApplicationAsync(c.id(), applicantId).thenApply(v -> updatedClan)));
    }

    public CompletableFuture<Void> rejectApplicationAsync(Clan clan, UUID actorId, UUID applicantId) {
        if (clan == null || actorId == null || applicantId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and applicant IDs cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.INVITE)) {
                throw new IllegalStateException("general.no-permission");
            }
            List<ClanApplication> applications = applicationsByClan.computeIfAbsent(clan.id(), ignored -> new ArrayList<>());
            ClanApplication application = applications.stream()
                    .filter(app -> app.applicantId().equals(applicantId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("clan.application-not-found"));
            applications.remove(application);
            return null;
        }).thenCompose(v -> storage.deleteApplicationAsync(clan.id(), applicantId));
    }

    public CompletableFuture<Clan> addMemberAsync(Clan clan, UUID playerId, ClanRank rank) {
        if (clan == null || playerId == null || rank == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, player ID and rank cannot be null."));
        return plugin.supplySync(() -> {
            if (getPlayerClan(playerId).isPresent()) {
                throw new IllegalStateException("clan.already-in-clan");
            }
            if (isClanFull(clan)) {
                throw new IllegalStateException("clan.member-limit-reached");
            }
            clan.addMember(playerId, rank);
            ClanMember member = clan.member(playerId).orElseThrow();
            clanByPlayer.put(playerId, clan.id());
            Bukkit.getPluginManager().callEvent(new ClanMemberJoinEvent(clan, member));

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            for (ClanTerritory territory : clan.territories()) {
                if (territory.advancedClaimId() != null) {
                    plugin.getAdvancedClaimsHook().findClaim(territory.advancedClaimId()).ifPresent(claimObject ->
                            plugin.getAdvancedClaimsHook().updatePlayerTrust(claimObject, offlinePlayer, rank));
                }
            }

            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) playerName = playerId.toString();
            for (UUID memberId : clan.members().keySet()) {
                Player clanMember = Bukkit.getPlayer(memberId);
                if (clanMember != null) {
                    plugin.getMessages().send(clanMember, "clan.joined-broadcast", Map.of("player", playerName));
                }
            }

            return clan;
        }).thenCompose(saved -> storage.saveMemberAsync(saved.id(), saved.member(playerId).orElseThrow()).thenApply(ignored -> saved));
    }

    public CompletableFuture<Void> removeMemberAsync(Clan clan, UUID actorId, UUID playerId, boolean kicked) {
        if (clan == null || playerId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and player ID cannot be null."));
        return plugin.supplySync(() -> {
            if (kicked && (actorId == null || !clan.hasPermission(actorId, ClanPermission.KICK))) {
                throw new IllegalStateException("general.no-permission");
            }
            ClanMember target = clan.member(playerId).orElseThrow(() -> new IllegalStateException("clan.not-in-clan"));
            if (kicked) {
                ClanMember actor = clan.member(actorId).orElseThrow(() -> new IllegalStateException("clan.not-in-clan"));
                if (!actor.rank().canManage(target.rank())) {
                    throw new IllegalStateException("clan.rank-too-low");
                }
            }
            if (target.rank() == ClanRank.LEADER && clan.members().size() > 1) {
                throw new IllegalStateException("clan.not-leader");
            }
            clan.removeMember(playerId);
            clanByPlayer.remove(playerId);

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            for (ClanTerritory territory : clan.territories()) {
                if (territory.advancedClaimId() != null) {
                    plugin.getAdvancedClaimsHook().findClaim(territory.advancedClaimId()).ifPresent(claimObject ->
                            plugin.getAdvancedClaimsHook().removePlayerTrust(claimObject, offlinePlayer));
                }
            }

            Bukkit.getPluginManager().callEvent(new ClanMemberLeaveEvent(clan, playerId, kicked));
            if (clan.members().isEmpty()) {
                for (ClanTerritory territory : clan.territories()) {
                    plugin.getAdvancedClaimsHook().deleteClaim(territory.advancedClaimId());
                }
                unindexClan(clan);
                applicationsByClan.remove(clan.id());
            }
            return clan.members().isEmpty();
        }).thenCompose(empty -> empty ? storage.deleteClanAsync(clan.id()).thenCompose(v -> storage.deleteAllApplicationsForClanAsync(clan.id())) : storage.deleteMemberAsync(clan.id(), playerId));
    }

    public CompletableFuture<Clan> setRankAsync(Clan clan, UUID actorId, UUID playerId, ClanRank rank) {
        if (clan == null || actorId == null || playerId == null || rank == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor, player and rank cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.KICK)) {
                throw new IllegalStateException("general.no-permission");
            }
            ClanMember target = clan.member(playerId).orElseThrow(() -> new IllegalStateException("clan.not-in-clan"));
            if (target.rank() == ClanRank.LEADER || rank == ClanRank.LEADER) {
                throw new IllegalStateException("clan.not-leader");
            }
            ClanRank oldRank = target.rank();
            clan.setRank(playerId, rank);
            Bukkit.getPluginManager().callEvent(new ClanRankChangeEvent(clan, playerId, oldRank, rank));

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            for (ClanTerritory territory : clan.territories()) {
                if (territory.advancedClaimId() != null) {
                    plugin.getAdvancedClaimsHook().findClaim(territory.advancedClaimId()).ifPresent(claimObject ->
                            plugin.getAdvancedClaimsHook().updatePlayerTrust(claimObject, offlinePlayer, rank));
                }
            }

            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) playerName = playerId.toString();
            for (UUID memberId : clan.members().keySet()) {
                Player clanMember = Bukkit.getPlayer(memberId);
                if (clanMember != null) {
                    plugin.getMessages().send(clanMember, "clan.rank-changed-broadcast", Map.of("player", playerName, "rank", rank.displayName()));
                }
            }

            return clan;
        }).thenCompose(saved -> storage.saveMemberAsync(saved.id(), saved.member(playerId).orElseThrow()).thenApply(ignored -> saved));
    }

    public CompletableFuture<Void> transferLeadershipAsync(Clan clan, UUID actorId, UUID newLeaderId) {
        if (clan == null || actorId == null || newLeaderId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and new leader IDs cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            // Передача лидерства запрещена, если у клана ещё не установлена столица —
            // без столицы клан считается не до конца сформированным.
            if (clan.getCapitalTerritory().isEmpty()) {
                throw new IllegalStateException("clan.transfer-requires-capital");
            }
            clan.member(newLeaderId).orElseThrow(() -> new IllegalStateException("clan.not-in-clan"));
            clan.setRank(actorId, ClanRank.GUARDIAN);
            clan.setRank(newLeaderId, ClanRank.LEADER);
            Bukkit.getPluginManager().callEvent(new ClanRankChangeEvent(clan, newLeaderId, ClanRank.RECRUIT, ClanRank.LEADER));

            OfflinePlayer oldLeaderPlayer = Bukkit.getOfflinePlayer(actorId);
            OfflinePlayer newLeaderPlayer = Bukkit.getOfflinePlayer(newLeaderId);
            for (ClanTerritory territory : clan.territories()) {
                if (territory.advancedClaimId() != null) {
                    plugin.getAdvancedClaimsHook().findClaim(territory.advancedClaimId()).ifPresent(claimObject -> {
                        plugin.getAdvancedClaimsHook().updatePlayerTrust(claimObject, oldLeaderPlayer, ClanRank.GUARDIAN);
                        plugin.getAdvancedClaimsHook().updatePlayerTrust(claimObject, newLeaderPlayer, ClanRank.LEADER);
                    });
                }
            }

            String newLeaderName = Bukkit.getOfflinePlayer(newLeaderId).getName();
            if (newLeaderName == null) newLeaderName = newLeaderId.toString();
            for (UUID memberId : clan.members().keySet()) {
                Player clanMember = Bukkit.getPlayer(memberId);
                if (clanMember != null) {
                    plugin.getMessages().send(clanMember, "clan.leadership-transferred-broadcast", Map.of("player", newLeaderName));
                }
            }

            return null;
        }).thenCompose(c -> storage.saveMemberAsync(clan.id(), clan.member(actorId).orElseThrow())
                .thenCompose(v -> storage.saveMemberAsync(clan.id(), clan.member(newLeaderId).orElseThrow())));
    }

    public CompletableFuture<Clan> changeTagColorAsync(Clan clan, UUID actorId, String colorTag) {
        if (clan == null || actorId == null || colorTag == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor ID and color tag cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            clan.setTagColor(colorTag);
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<Clan> renameClanAsync(Clan clan, UUID actorId, String newName) {
        if (clan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            validateName(newName);
            clan.setName(newName);
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<Clan> changeClanTagAsync(Clan clan, UUID actorId, String newTag) {
        if (clan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            validateTag(newTag);
            Optional<Clan> existing = getClanByTag(newTag);
            if (existing.isPresent() && !existing.get().id().equals(clan.id())) {
                throw new IllegalStateException("clan.tag-exists");
            }
            String oldTag = normalizeTag(clan.tag());
            clan.setTag(newTag);
            clanByTag.remove(oldTag);
            clanByTag.put(normalizeTag(clan.tag()), clan.id());
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<Clan> changeClanEmblemAsync(Clan clan, UUID actorId, Material newEmblem) {
        if (clan == null || actorId == null || newEmblem == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor ID and emblem cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("gui.settings.change-banner.invalid-item");
            }
            if (!newEmblem.toString().endsWith("_BANNER")) {
                throw new IllegalStateException("gui.settings.change-banner.invalid-item");
            }
            clan.setEmblem(newEmblem);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (ClanTerritory territory : clan.territories()) {
                    if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
                        World world = Bukkit.getWorld(territory.key().world());
                        if (world != null) {
                            org.bukkit.block.Block block = world.getBlockAt(territory.bannerX(), territory.bannerY(), territory.bannerZ());
                            if (block.getType().toString().endsWith("_BANNER")) {
                                block.setType(newEmblem);
                            }
                        }
                    }
                }
            });

            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<Clan> setClanOpenStatusAsync(Clan clan, UUID actorId, boolean open) {
        if (clan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            clan.setOpen(open);
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<ClanTerritory> updateTerritoryAsync(Clan clan, ClanTerritory territory) {
        if (clan == null || territory == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and territory cannot be null."));
        return plugin.supplySync(() -> {
            clan.addTerritory(territory);
            return territory;
        }).thenCompose(updated -> storage.saveTerritoryAsync(updated).thenApply(ignored -> updated));
    }

    public CompletableFuture<Boolean> claimTerritoryAsync(Clan clan, Location location, Player actor, String bannerType) {
        if (clan == null || location == null || actor == null || bannerType == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, location, actor and banner type cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actor.getUniqueId(), ClanPermission.CLAIM)) {
                throw new IllegalStateException("general.no-permission");
            }

            if (pendingClaims.containsKey(actor.getUniqueId())) {
                confirmPendingClaim(actor, location);
                return true;
            } else {
                return initiateClaimConfirmation(actor, clan, location, bannerType);
            }
        });
    }

    public boolean initiateClaimConfirmation(Player player, Clan clan, Location location, String bannerType) {
        if (!plugin.getAdvancedClaimsHook().enabled()) {
            plugin.getMessages().send(player, "territory.advancedclaims-disabled");
            return false;
        }

        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) {
            plugin.getMessages().send(player, "territory.invalid-world");
            return false;
        }

        Location spawnLoc = location.getWorld().getSpawnLocation();
        int spawnProtectionRadius = plugin.getConfig().getInt("limits.spawn-protection-radius", 500);
        if (spawnLoc.distance(location) < spawnProtectionRadius) {
            plugin.getMessages().send(player, "territory.too-close-to-spawn");
            return false;
        }

        if (clan.territories().size() >= maxTerritories(clan)) {
            plugin.getMessages().send(player, "territory.limit-reached");
            return false;
        }

        TerritoryKey key = TerritoryKey.fromLocation(location);

        if (getClanAt(key).isPresent()) {
            plugin.getMessages().send(player, "territory.already-claimed");
            return false;
        }

        if (plugin.getAdvancedClaimsHook().isClaimed(location)) {
            plugin.getMessages().send(player, "territory.already-claimed-by-advancedclaims");
            return false;
        }

        int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 12);
        BoundingBox visualizationBox = new BoundingBox(
                location.getBlockX() - radius, location.getBlockY() - radius, location.getBlockZ() - radius,
                location.getBlockX() + radius, location.getBlockY() + radius, location.getBlockZ() + radius
        );

        PendingClaim pendingClaim = new PendingClaim(player.getUniqueId(), clan, location, bannerType, visualizationBox);
        pendingClaims.put(player.getUniqueId(), pendingClaim);

        long borderDuration = plugin.getConfig().getLong("integration.advanced-claims.border-display-ticks", 100L);
        plugin.getAdvancedClaimsHook().showClaimBorder(player, visualizationBox, borderDuration);

        plugin.getMessages().send(player, "territory.claim-confirm-chat");
        player.showTitle(Title.title(
                plugin.getMessages().component("territory.claim-confirm-title", Map.of(), player),
                plugin.getMessages().component("territory.claim-confirm-subtitle", Map.of(), player),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(borderDuration / 20), Duration.ofMillis(500))
        ));

        return true;
    }

    public CompletableFuture<ClanTerritory> confirmPendingClaim(Player player, Location location) {
        PendingClaim pendingClaim = pendingClaims.remove(player.getUniqueId());
        if (pendingClaim == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("territory.claim-no-pending"));
        }

        if (!pendingClaim.location().getBlock().equals(location.getBlock())) {
            plugin.getAdvancedClaimsHook().hideClaimBorder(player);
            return CompletableFuture.failedFuture(new IllegalStateException("territory.claim-location-mismatch"));
        }

        Clan clan = pendingClaim.clan();
        TerritoryKey key = TerritoryKey.fromLocation(location);
        String bannerType = pendingClaim.bannerType();
        ItemStack bannerItem = clanItemFactory.createBannerByType(bannerType, clan.id(), clan.name());

        ClanTerritory territory = new ClanTerritory(clan.id(), location.getWorld().getName(), pendingClaim.visualizationBox(), player.getUniqueId(), System.currentTimeMillis())
                .withBannerCoords(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                .withCapital(bannerType.equals("CAPITAL"));

        ClanClaimEvent event = new ClanClaimEvent(clan, territory, player.getUniqueId());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.getAdvancedClaimsHook().hideClaimBorder(player);
            return CompletableFuture.failedFuture(new IllegalStateException("general.error"));
        }

        return plugin.supplySync(() -> {
            UUID claimId = plugin.getAdvancedClaimsHook().createOrAttachClaim(clan, territory).orElse(null);
            return territory.withAdvancedClaimId(claimId);
        }).thenCompose(savedTerritory -> {
            clan.addTerritory(savedTerritory);
            clanByTerritory.put(key, clan.id());

            if (bannerType.equals("CAPITAL")) {
                clan.setHomeLocation(location);
                return storage.saveClanAsync(clan).thenCompose(v -> storage.saveTerritoryAsync(savedTerritory)).thenApply(v -> savedTerritory);
            }
            return storage.saveTerritoryAsync(savedTerritory).thenApply(v -> savedTerritory);
        }).thenApply(savedTerritory -> {
            plugin.runSync(() -> {
                location.getBlock().setType(bannerItem.getType());
                player.getInventory().removeItem(bannerItem);
                plugin.getAdvancedClaimsHook().hideClaimBorder(player);
                plugin.getMessages().send(player, "territory.claimed-success", Map.of("clan", clan.name(), "tag", clan.tag()));
                addExperienceAsync(clan, plugin.getConfig().getLong("leveling.territory-claim-exp", 150L));
            });
            return savedTerritory;
        }).exceptionally(ex -> {
            plugin.runSync(() -> {
                plugin.sendOperationError(player, ex);
                plugin.getAdvancedClaimsHook().hideClaimBorder(player);
                player.getInventory().addItem(bannerItem);
            });
            return null;
        });
    }

    public Optional<PendingClaim> cancelPendingClaim(UUID playerId) {
        PendingClaim cancelledClaim = pendingClaims.remove(playerId);
        if (cancelledClaim != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.getAdvancedClaimsHook().hideClaimBorder(player);
                plugin.getMessages().send(player, "territory.claim-cancelled");
            }
        }
        return Optional.ofNullable(cancelledClaim);
    }

    public boolean hasPendingClaim(UUID playerId) {
        return pendingClaims.containsKey(playerId);
    }

    public CompletableFuture<Clan> relocateHomeAsync(Clan clan, UUID actorId, Location location) {
        if (clan == null || actorId == null || location == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor and location cannot be null."));
        return plugin.supplySync(() -> {
            boolean canManage = clan.member(actorId)
                    .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                    .orElse(false);
            if (!canManage) {
                throw new IllegalStateException("gui.capital.no-permission");
            }
            clan.setHomeLocation(location);
            return clan;
        }).thenCompose(c -> storage.saveClanAsync(c).thenApply(ignored -> c));
    }

    public CompletableFuture<Void> relocateCapitalTerritoryAsync(Clan clan, UUID actorId) {
        if (clan == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan and actor cannot be null."));
        return plugin.supplySync(() -> {
            boolean canManage = clan.member(actorId)
                    .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                    .orElse(false);
            if (!canManage) {
                throw new IllegalStateException("gui.capital.no-permission");
            }
            if (plugin.getWarManager().isAtWar(clan.id())) {
                throw new IllegalStateException("gui.capital.war-blocked");
            }
            ClanTerritory territory = clan.territories().stream()
                    .filter(ClanTerritory::isCapital)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("territory.capital.not-found"));

            plugin.getAdvancedClaimsHook().deleteClaim(territory.advancedClaimId());
            clan.removeTerritory(territory.id());
            clanByTerritory.remove(territory.key());
            clan.setHomeLocation(null);

            if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    World world = Bukkit.getWorld(territory.key().world());
                    if (world != null) {
                        org.bukkit.block.Block block = world.getBlockAt(territory.bannerX(), territory.bannerY(), territory.bannerZ());
                        if (block.getType().toString().endsWith("_BANNER")) {
                            block.setType(Material.AIR);
                        }
                    }
                });
            }

            Player actor = Bukkit.getPlayer(actorId);
            if (actor != null) {
                ItemStack bannerItem = clanItemFactory.createCapitalBanner(clan.id(), clan.name());
                if (actor.getInventory().addItem(bannerItem).size() > 0) {
                    actor.getWorld().dropItemNaturally(actor.getLocation(), bannerItem);
                }
            }

            Bukkit.getPluginManager().callEvent(new ClanUnclaimEvent(clan, territory, actorId));
            return territory;
        }).thenCompose(territory -> storage.deleteTerritoryAsync(territory.id())
                .thenCompose(v -> storage.saveClanAsync(clan)).thenApply(v -> null));
    }

    public CompletableFuture<Void> unclaimTerritoryAsync(Clan clan, TerritoryKey key, UUID actorId) {
        if (clan == null || key == null || actorId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, key and actor ID cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.CLAIM)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (plugin.getWarManager().activeWars().stream().anyMatch(war -> war.involves(clan.id()))) {
                throw new IllegalStateException("war.cannot-unclaim");
            }
            ClanTerritory territory = clan.territories().stream()
                    .filter(t -> t.key().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("territory.not-claimed"));

            plugin.getAdvancedClaimsHook().deleteClaim(territory.advancedClaimId());
            clan.removeTerritory(territory.id());
            clanByTerritory.remove(key);

            if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    World world = Bukkit.getWorld(territory.key().world());
                    if (world != null) {
                        org.bukkit.block.Block block = world.getBlockAt(territory.bannerX(), territory.bannerY(), territory.bannerZ());
                        if (block.getType().toString().endsWith("_BANNER")) {
                            block.setType(Material.AIR);
                        }
                    }
                });
            }

            Bukkit.getPluginManager().callEvent(new ClanUnclaimEvent(clan, territory, actorId));
            return territory;
        }).thenCompose(territory -> storage.deleteTerritoryAsync(territory.id()));
    }

    public CompletableFuture<Clan> setDiplomacyAsync(Clan source, Clan target, DiplomacyRelation relation, UUID actorId) {
        if (source == null || target == null || relation == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Source, target and relation cannot be null."));
        return plugin.supplySync(() -> {
            if (actorId != null && !source.hasPermission(actorId, ClanPermission.DIPLOMACY)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (source.id().equals(target.id())) {
                throw new IllegalStateException("general.error");
            }

            if (source.territories().isEmpty() || target.territories().isEmpty()) {
                throw new IllegalStateException("diplomacy.requires-home");
            }

            if (relation == DiplomacyRelation.ALLY) {
                addAllianceRequest(source.id(), target.id());
                getOnlineLeader(target).ifPresent(leader ->
                        plugin.getMessages().sendClickableAlliance(leader, source.tag()));
                return source;
            }
            source.setDiplomacy(target.id(), relation);
            Bukkit.getPluginManager().callEvent(new ClanDiplomacyChangeEvent(source.id(), target.id(), relation));
            return source;
        }).thenCompose(clan -> relation == DiplomacyRelation.ALLY ? CompletableFuture.completedFuture(clan)
                : storage.saveDiplomacyAsync(clan.id(), target.id(), relation).thenApply(ignored -> clan));
    }

    public CompletableFuture<Clan> addExperienceAsync(Clan clan, long amount) {
        if (clan == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Clan cannot be null."));
        return plugin.supplySync(() -> {
            int maxLevel = plugin.getConfig().getInt("limits.max-level", 20);
            int oldLevel = clan.level();
            long boostedAmount = Math.round(amount * experienceBonusMultiplier(clan));
            clan.addExperience(boostedAmount);
            while (clan.level() < maxLevel && clan.experience() >= experienceForLevel(clan.level() + 1)) {
                clan.levelUp();
                Bukkit.getPluginManager().callEvent(new ClanLevelUpEvent(clan, oldLevel, clan.level()));
                oldLevel = clan.level();
            }
            return clan;
        }).thenCompose(c -> storage.saveClanAsync(c).thenApply(ignored -> c))
                .thenCompose(c -> plugin.runSync(() -> plugin.getSpiritManager().addSpiritExperience(c, Math.max(0L, amount / 4), "Опыт клана")).thenApply(ignored -> c));
    }

    public CompletableFuture<Clan> updateClanAsync(Clan clan) {
        if (clan == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Clan cannot be null."));
        return storage.saveClanAsync(clan).thenApply(ignored -> clan);
    }

    public void updateLastSeen(UUID playerId, long timestamp) {
        if (playerId == null) return;
        getPlayerClan(playerId).ifPresent(clan -> {
            clan.markSeen(playerId, timestamp);
            clan.member(playerId).ifPresent(member -> storage.saveMemberAsync(clan.id(), member));
        });
    }

    public boolean isClanFull(Clan clan) {
        if (clan == null) return true;
        return clan.members().size() >= maxMembers(clan);
    }

    public int maxMembers(Clan clan) {
        if (clan == null) return 0;
        return plugin.getConfig().getInt("limits.base-members", 10)
                + clan.upgradeLevel(ClanUpgrade.MEMBERS) * plugin.getConfig().getInt("limits.members-per-upgrade", 3);
    }

    public int maxTerritories(Clan clan) {
        if (clan == null) return 0;
        return plugin.getConfig().getInt("limits.base-territories", 4)
                + clan.level() * plugin.getConfig().getInt("limits.territories-per-level", 1)
                + clan.upgradeLevel(ClanUpgrade.TERRITORIES) * plugin.getConfig().getInt("limits.territories-per-upgrade", 2);
    }

    public double experienceBonusMultiplier(Clan clan) {
        if (clan == null) return 1.0D;
        double perUpgrade = plugin.getConfig().getDouble("limits.experience-percent-per-upgrade", 5.0D);
        return 1.0D + (clan.upgradeLevel(ClanUpgrade.EXPERIENCE) * perUpgrade / 100.0D);
    }

    public int maxUpgradeLevel(ClanUpgrade upgrade) {
        return plugin.getConfig().getInt("upgrades." + upgrade.name() + ".max-level", 1);
    }

    public CompletableFuture<Clan> purchaseUpgradeAsync(Clan clan, UUID actorId, ClanUpgrade upgrade) {
        if (clan == null || actorId == null || upgrade == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor ID and upgrade cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.UPGRADE)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (clan.upgradePoints() <= 0) {
                throw new IllegalStateException("gui.upgrades.not-enough-points");
            }
            int maxLevel = maxUpgradeLevel(upgrade);
            if (clan.upgradeLevel(upgrade) >= maxLevel) {
                throw new IllegalStateException("gui.upgrades.max-level-reached");
            }
            clan.setUpgradeLevel(upgrade, clan.upgradeLevel(upgrade) + 1);
            clan.removeUpgradePoints(1);
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public CompletableFuture<Clan> chooseSpiritAbilityAsync(Clan clan, UUID actorId, me.lovelace.loveclans.model.spirit.SpiritAbility ability) {
        if (clan == null || actorId == null || ability == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Clan, actor ID and ability cannot be null."));
        return plugin.supplySync(() -> {
            if (!clan.hasPermission(actorId, ClanPermission.SETTINGS)) {
                throw new IllegalStateException("general.no-permission");
            }
            if (clan.spirit().level() < 10) {
                throw new IllegalStateException("gui.spirit.ability.locked");
            }
            if (ability == clan.spirit().ability()) {
                return clan;
            }
            long now = System.currentTimeMillis();
            long remaining = clan.spirit().abilityCooldownRemaining(now);
            if (clan.spirit().ability() != null && remaining > 0) {
                throw new SpiritAbilityCooldownException(remaining);
            }
            clan.setSpirit(clan.spirit().withAbility(ability, now));
            return clan;
        }).thenCompose(updatedClan -> storage.saveClanAsync(updatedClan).thenApply(ignored -> updatedClan));
    }

    public long experienceForLevel(int level) {
        if (level <= 1) {
            return 0L;
        }
        double base = plugin.getConfig().getDouble("leveling.base-exp", 1000D);
        double multiplier = plugin.getConfig().getDouble("leveling.multiplier", 1.35D);
        return Math.round(base * Math.pow(multiplier, level - 2));
    }

    public List<Clan> topClansByLevel(int limit) {
        return clansById.values().stream()
                .sorted(Comparator.comparingInt(Clan::level).thenComparingLong(Clan::experience).reversed())
                .limit(limit)
                .toList();
    }

    private void indexClan(Clan clan) {
        if (clan == null) return;
        clansById.put(clan.id(), clan);
        clanByTag.put(normalizeTag(clan.tag()), clan.id());
        for (UUID playerId : clan.members().keySet()) {
            clanByPlayer.put(playerId, clan.id());
        }
        for (ClanTerritory territory : clan.territories()) {
            clanByTerritory.put(territory.key(), clan.id());
        }
    }

    private void unindexClan(Clan clan) {
        if (clan == null) return;
        clansById.remove(clan.id());
        clanByTag.remove(normalizeTag(clan.tag()));
        for (UUID playerId : clan.members().keySet()) {
            clanByPlayer.remove(playerId);
        }
        for (ClanTerritory territory : clan.territories()) {
            clanByTerritory.remove(territory.key());
        }
    }

    private void requireRank(Clan clan, UUID playerId, ClanRank minimum) {
        if (clan == null || playerId == null || minimum == null)
            throw new IllegalStateException("clan.not-in-clan");
        ClanMember member = clan.member(playerId).orElseThrow(() -> new IllegalStateException("clan.not-in-clan"));
        if (!member.rank().atLeast(minimum)) {
            throw new IllegalStateException("clan.rank-too-low");
        }
    }

    private void validateTag(String tag) {
        int min = plugin.getConfig().getInt("clans.tag.min-length", 3);
        int max = plugin.getConfig().getInt("clans.tag.max-length", 6);
        String pattern = plugin.getConfig().getString("clans.tag.pattern", "^[A-Za-z0-9_]+$");
        if (tag == null || tag.length() < min || tag.length() > max || !Pattern.compile(pattern).matcher(tag).matches()) {
            throw new IllegalStateException("clan.invalid-tag");
        }
    }

    private void validateName(String name) {
        int min = plugin.getConfig().getInt("clans.name.min-length", 4);
        int max = plugin.getConfig().getInt("clans.name.max-length", 10);
        if (name == null || name.length() < min || name.length() > max) {
            throw new IllegalStateException("clan.invalid-name");
        }
    }

    private String normalizeTag(String tag) {
        if (tag == null) return "";
        return tag.toLowerCase(Locale.ROOT);
    }

    public ClanItemFactory getClanItemFactory() {
        return clanItemFactory;
    }
}