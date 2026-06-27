package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanWarEndEvent;
import me.lovelace.loveclans.api.events.ClanWarStartEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.war.WarResult;
import me.lovelace.loveclans.model.war.WarState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WarManager {
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);
    private static final Duration BANNER_CAPTURE_DURATION = Duration.ofMinutes(3);
    private static final Duration WAR_COOLDOWN_DURATION = Duration.ofHours(24);

    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanWar> activeWars = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> warCooldowns = new ConcurrentHashMap<>();

    public WarManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> getWarPairKey(UUID clan1, UUID clan2) {
        return clan1.compareTo(clan2) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(clan1, clan2)
                : new AbstractMap.SimpleImmutableEntry<>(clan2, clan1);
    }

    public CompletableFuture<ClanWar> startWarAsync(Clan attacker, Clan defender, TerritoryKey territory) {
        return plugin.supplySync(() -> {
            if (activeWars.size() >= 3) {
                throw new IllegalStateException("war.max-wars-reached");
            }
            if (areAtWar(attacker.id(), defender.id())) {
                throw new IllegalStateException("war.already-at-war");
            }
            if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                throw new IllegalStateException("war.cannot-declare-on-ally");
            }

            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = getWarPairKey(attacker.id(), defender.id());
            Long lastWarTime = warCooldowns.get(cooldownKey);
            long now = System.currentTimeMillis();

            if (lastWarTime != null && (now - lastWarTime < WAR_COOLDOWN_DURATION.toMillis())) {
                long remainingSeconds = (WAR_COOLDOWN_DURATION.toMillis() - (now - lastWarTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            int attackerOnline = 0;
            for (UUID memberId : attacker.members().keySet()) {
                if (Bukkit.getPlayer(memberId) != null) attackerOnline++;
            }

            int defenderOnline = 0;
            for (UUID memberId : defender.members().keySet()) {
                if (Bukkit.getPlayer(memberId) != null) defenderOnline++;
            }

            if (attackerOnline < 3 || defenderOnline < 3) {
                throw new IllegalStateException("war.not-enough-members");
            }

            boolean defenderHasOnlineLeaderOrGuardian = defender.members().values().stream()
                    .filter(member -> member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN)
                    .anyMatch(member -> Bukkit.getPlayer(member.playerId()) != null);

            if (!defenderHasOnlineLeaderOrGuardian) {
                throw new IllegalStateException("war.defender-no-online-leader-or-guardian");
            }

            ClanWar war = new ClanWar(UUID.randomUUID(), attacker.id(), defender.id(), territory, now,
                    now + DEFAULT_DURATION.toMillis(), WarState.ACTIVE, 0, 0);
            ClanWarStartEvent event = new ClanWarStartEvent(war);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            activeWars.put(war.id(), war);
            warCooldowns.put(cooldownKey, now);

            distributeWarCompasses(war);

            return war;
        });
    }

    public CompletableFuture<Void> endWarAsync(UUID warId, WarResult result) {
        return plugin.supplySync(() -> {
            ClanWar war = activeWars.remove(warId);
            if (war != null) {
                Bukkit.getPluginManager().callEvent(new ClanWarEndEvent(war.withState(WarState.FINISHED), result));
                long reward = plugin.getConfig().getLong("leveling.war-win-exp", 1200L);
                if (result == WarResult.ATTACKER_WIN) {
                    plugin.getClanManager().getClanById(war.attackerClanId()).ifPresent(clan ->
                            plugin.getClanManager().addExperienceAsync(clan, reward).exceptionally(t -> {
                                plugin.getLogger().warning("Failed to award war experience to clan " + clan.id() + ": " + t.getMessage());
                                return null;
                            }));
                    if (war.capturedBannerBy() != null) {
                        plugin.getClanManager().getClanById(war.defenderClanId()).ifPresent(defender ->
                                plugin.getClanManager().disbandClanAsync(defender, war.capturedBannerBy()).exceptionally(t -> {
                                    plugin.getLogger().warning("Failed to disband clan " + defender.id() + " after war loss: " + t.getMessage());
                                    return null;
                                }));
                    }
                } else if (result == WarResult.DEFENDER_WIN) {
                    plugin.getClanManager().getClanById(war.defenderClanId()).ifPresent(clan ->
                            plugin.getClanManager().addExperienceAsync(clan, reward).exceptionally(t -> {
                                plugin.getLogger().warning("Failed to award war experience to clan " + clan.id() + ": " + t.getMessage());
                                return null;
                            }));
                }
                removeWarCompasses(war);
            }
            return null;
        });
    }

    public CompletableFuture<Void> peaceAsync(Clan sourceClan, Clan targetClan) {
        return plugin.supplySync(() -> {
            Optional<ClanWar> warOpt = activeWar(sourceClan.id(), targetClan.id());
            if (warOpt.isEmpty()) {
                throw new IllegalStateException("war.not-at-war");
            }

            ClanWar war = warOpt.get();
            activeWars.remove(war.id());
            Bukkit.getPluginManager().callEvent(new ClanWarEndEvent(war.withState(WarState.FINISHED), WarResult.DRAW));

            for (UUID memberId : sourceClan.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
            }
            for (UUID memberId : targetClan.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", sourceClan.tag(), "color", sourceClan.tagColor()));
            }
            removeWarCompasses(war);

            return null;
        });
    }

    public boolean areAtWar(UUID firstClanId, UUID secondClanId) {
        return activeWars.values().stream().anyMatch(war -> war.state() == WarState.ACTIVE && war.between(firstClanId, secondClanId));
    }

    public boolean isAtWar(UUID clanId) {
        return activeWars.values().stream().anyMatch(war -> war.involves(clanId));
    }

    public Optional<ClanWar> activeWar(UUID firstClanId, UUID secondClanId) {
        return activeWars.values().stream()
                .filter(war -> war.state() == WarState.ACTIVE && war.between(firstClanId, secondClanId))
                .findFirst();
    }

    public Collection<ClanWar> activeWars() {
        return List.copyOf(activeWars.values());
    }

    public void addKillScore(UUID killerClanId, UUID victimClanId) {
        activeWar(killerClanId, victimClanId).ifPresent(war -> {
            ClanWar updated = killerClanId.equals(war.attackerClanId()) ? war.addAttackerScore(1) : war.addDefenderScore(1);
            activeWars.put(war.id(), updated);
        });
    }

    public void setBannerCapture(UUID warId, UUID playerId) {
        ClanWar war = activeWars.get(warId);
        if (war != null) {
            activeWars.put(warId, war.withBannerCapture(playerId, System.currentTimeMillis()));
        }
    }

    public void resetBannerCapture(UUID warId) {
        ClanWar war = activeWars.get(warId);
        if (war != null) {
            activeWars.put(warId, war.withBannerCapture(null, 0));
        }
    }

    public void purgeClan(UUID clanId) {
        warCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
    }

    public void tick() {
        long now = System.currentTimeMillis();
        warCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= WAR_COOLDOWN_DURATION.toMillis());

        for (ClanWar war : activeWars.values()) {
            if (war.capturedBannerBy() != null && (now - war.bannerCapturedAt() >= BANNER_CAPTURE_DURATION.toMillis())) {
                endWarAsync(war.id(), WarResult.ATTACKER_WIN);
                continue;
            }

            if (war.endsAt() <= now) {
                WarResult result = war.attackerScore() > war.defenderScore()
                        ? WarResult.ATTACKER_WIN
                        : war.defenderScore() > war.attackerScore() ? WarResult.DEFENDER_WIN : WarResult.DRAW;
                endWarAsync(war.id(), result);
            }
        }
    }

    private void distributeWarCompasses(ClanWar war) {
        Optional<Clan> attackerClanOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderClanOpt = plugin.getClanManager().getClanById(war.defenderClanId());

        if (attackerClanOpt.isEmpty() || defenderClanOpt.isEmpty()) {
            plugin.getLogger().warning("Could not find one of the clans for war " + war.id());
            return;
        }

        Clan attackerClan = attackerClanOpt.get();
        Clan defenderClan = defenderClanOpt.get();

        Optional<ClanTerritory> defenderTerritoryOpt = defenderClan.territories().stream()
                .filter(t -> t.key().equals(war.contestedTerritory()))
                .findFirst();

        if (defenderTerritoryOpt.isEmpty()) {
            plugin.getLogger().warning("Defender clan " + defenderClan.name() + " does not have the claimed territory " + war.contestedTerritory());
            return;
        }

        ClanTerritory defenderTerritory = defenderTerritoryOpt.get();
        if (defenderTerritory.bannerX() == null || defenderTerritory.bannerY() == null || defenderTerritory.bannerZ() == null) {
            plugin.getLogger().warning("Defender clan " + defenderClan.name() + " territory " + war.contestedTerritory() + " has no banner coordinates set.");
            return;
        }

        org.bukkit.World bannerWorld = Bukkit.getWorld(defenderTerritory.key().world());
        if (bannerWorld == null) {
            plugin.getLogger().warning("World " + defenderTerritory.key().world() + " is not loaded; cannot distribute war compasses for war " + war.id());
            return;
        }

        Location bannerLocation = new Location(
                bannerWorld,
                defenderTerritory.bannerX(),
                defenderTerritory.bannerY(),
                defenderTerritory.bannerZ()
        );

        attackerClan.members().values().stream()
                .filter(member -> (member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN))
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> giveTrackingCompass(player, bannerLocation, defenderClan));

        defenderClan.members().values().stream()
                .filter(member -> (member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN))
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> giveTrackingCompass(player, bannerLocation, attackerClan));
    }

    private void giveTrackingCompass(Player player, Location targetLocation, Clan enemyClan) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMessages().component("item.war-compass.name", Map.of("tag", enemyClan.tag())));
            meta.lore(List.of(plugin.getMessages().component("item.war-compass.lore")));
            compass.setItemMeta(meta);
        }

        player.setCompassTarget(targetLocation);

        if (player.getInventory().firstEmpty() == -1) {
            player.getInventory().setItem(0, compass);
        } else {
            player.getInventory().addItem(compass);
        }
        plugin.getMessages().send(player, "war.compass-given", Map.of("tag", enemyClan.tag(), "color", enemyClan.tagColor()));
    }

    private void removeWarCompasses(ClanWar war) {
        Optional<Clan> attackerClanOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderClanOpt = plugin.getClanManager().getClanById(war.defenderClanId());

        attackerClanOpt.ifPresent(clan -> clan.members().values().stream()
                .filter(member -> (member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN))
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(this::clearWarCompass));

        defenderClanOpt.ifPresent(clan -> clan.members().values().stream()
                .filter(member -> (member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN))
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(this::clearWarCompass));
    }

    private void clearWarCompass(Player player) {
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    if (meta.displayName().toString().contains("War Compass")) {
                        player.getInventory().setItem(i, null);
                        plugin.getMessages().send(player, "war.compass-removed");
                        break;
                    }
                }
            }
        }
    }
}