package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.model.siege.ClanSiege;
import me.lovelace.loveclans.model.siege.SiegeCamp;
import me.lovelace.loveclans.model.siege.SiegeResult;
import me.lovelace.loveclans.model.siege.SiegeState;
import me.lovelace.loveclans.util.ClanItemFactory;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Второй тип клановых конфликтов (§3.1): вместо одного знамени атакующие ставят вокруг границы
 * territoriи защитника 3-4 осадных лагеря, каждый со своим независимым отсчётом. Как и война,
 * начинается не сразу, а после паузы (босс-бар, см. {@link WarManager} за тем же паттерном).
 */
public final class SiegeManager {
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanSiege> activeSieges = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> siegeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> pendingBossBars = new ConcurrentHashMap<>();
    private final Set<UUID> oneMinuteWarned = ConcurrentHashMap.newKeySet();

    public SiegeManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Duration preStartDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.pre-start-minutes", 20));
    }

    private Duration siegeDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.duration-minutes", 20));
    }

    private Duration cooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("siege.cooldown-hours", 24));
    }

    private Duration campRespawnDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("siege.camp-respawn-minutes", 2));
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID clan1, UUID clan2) {
        return clan1.compareTo(clan2) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(clan1, clan2)
                : new AbstractMap.SimpleImmutableEntry<>(clan2, clan1);
    }

    public CompletableFuture<ClanSiege> startSiegeAsync(Clan attacker, Clan defender, TerritoryKey territoryKey) {
        return plugin.supplySync(() -> {
            if (activeSieges.size() >= plugin.getConfig().getInt("siege.max-concurrent", 3)) {
                throw new IllegalStateException("siege.max-sieges-reached");
            }
            if (isInSiege(attacker.id()) || isInSiege(defender.id())) {
                throw new IllegalStateException("siege.already-in-siege");
            }
            if (plugin.getWarManager().isAtWar(attacker.id()) || plugin.getWarManager().isAtWar(defender.id())) {
                throw new IllegalStateException("siege.war-in-progress");
            }
            if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                throw new IllegalStateException("war.cannot-declare-on-ally");
            }

            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(attacker.id(), defender.id());
            long now = System.currentTimeMillis();
            Long lastSiegeTime = siegeCooldowns.get(cooldownKey);
            if (lastSiegeTime != null && (now - lastSiegeTime < cooldownDuration().toMillis())) {
                long remainingSeconds = (cooldownDuration().toMillis() - (now - lastSiegeTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            int attackerOnline = countOnline(attacker);
            int defenderOnline = countOnline(defender);
            if (attackerOnline < plugin.getConfig().getInt("siege.min-attacker-online", 1)) {
                throw new IllegalStateException("siege.not-enough-attackers");
            }
            if (defenderOnline < plugin.getConfig().getInt("siege.min-defender-online", 5)) {
                throw new IllegalStateException("siege.not-enough-defenders");
            }

            ClanSiege siege = new ClanSiege(UUID.randomUUID(), attacker.id(), defender.id(), territoryKey,
                    now, now + preStartDuration().toMillis(), SiegeState.PREPARING, List.of());
            activeSieges.put(siege.id(), siege);
            siegeCooldowns.put(cooldownKey, now);

            beginPendingPhase(siege, attacker, defender);
            return siege;
        });
    }

    private int countOnline(Clan clan) {
        int online = 0;
        for (UUID memberId : clan.members().keySet()) {
            if (Bukkit.getPlayer(memberId) != null) online++;
        }
        return online;
    }

    private record SiegeClans(Clan attacker, Clan defender) {}

    private Optional<SiegeClans> resolveClans(ClanSiege siege) {
        Optional<Clan> attacker = plugin.getClanManager().getClanById(siege.attackerClanId());
        Optional<Clan> defender = plugin.getClanManager().getClanById(siege.defenderClanId());
        if (attacker.isEmpty() || defender.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SiegeClans(attacker.get(), defender.get()));
    }

    private void beginPendingPhase(ClanSiege siege, Clan attacker, Clan defender) {
        String time = formatDuration(siege.endsAt() - System.currentTimeMillis());
        Component title = plugin.getMessages().component("siege.pending.bossbar", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(), "time", time));
        BossBar bar = BossBar.bossBar(title, 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        pendingBossBars.put(siege.id(), bar);

        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "siege.pending.declared",
                Map.of("tag", defender.tag(), "color", defender.tagColor(), "time", time)));
        onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "siege.pending.declared",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor(), "time", time)));
    }

    private void clearPendingPhase(UUID siegeId, SiegeClans clans) {
        BossBar bar = pendingBossBars.remove(siegeId);
        oneMinuteWarned.remove(siegeId);
        if (bar != null && clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> p.hideBossBar(bar));
        }
    }

    private void activateSiege(ClanSiege siege) {
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        clearPendingPhase(siege.id(), clansOpt.orElse(null));
        if (clansOpt.isEmpty()) {
            activeSieges.remove(siege.id());
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        Optional<ClanTerritory> territoryOpt = resolveContestedTerritory(siege);
        Location center = territoryOpt.map(this::territoryCenter).orElse(null);
        if (center == null) {
            plugin.getLogger().warning("Siege " + siege.id() + " has no resolvable territory - cancelling.");
            activeSieges.remove(siege.id());
            return;
        }

        long now = System.currentTimeMillis();
        List<SiegeCamp> camps = spawnCamps(siege.id(), center, now);
        ClanSiege activated = siege.activate(now + siegeDuration().toMillis(), camps);
        activeSieges.put(activated.id(), activated);

        onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "siege.start.attacker-title", "siege.start.attacker-subtitle",
                Map.of("tag", defender.tag(), "color", defender.tagColor())));
        onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "siege.start.defender-title", "siege.start.defender-subtitle",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor())));
    }

    private Location territoryCenter(ClanTerritory territory) {
        World world = Bukkit.getWorld(territory.key().world());
        if (world == null) {
            return null;
        }
        if (territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
            return new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());
        }
        var box = territory.boundingBox();
        return new Location(world, box.getCenterX(), box.getCenterY(), box.getCenterZ());
    }

    private List<SiegeCamp> spawnCamps(UUID siegeId, Location center, long now) {
        int min = plugin.getConfig().getInt("siege.camp-count-min", 3);
        int max = Math.max(min, plugin.getConfig().getInt("siege.camp-count-max", 4));
        int count = min + (max > min ? ThreadLocalRandom.current().nextInt(max - min + 1) : 0);
        double distance = plugin.getConfig().getInt("siege.camp-distance-blocks", 50);

        List<SiegeCamp> camps = new ArrayList<>(count);
        double baseAngle = ThreadLocalRandom.current().nextDouble(0, 360);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(baseAngle + (360.0 / count) * i + ThreadLocalRandom.current().nextDouble(-10, 10));
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            World world = center.getWorld();
            int y = world.getHighestBlockYAt(x, z) + 1;
            camps.add(placeCamp(siegeId, i, world, x, y, z, now));
        }
        return camps;
    }

    private SiegeCamp placeCamp(UUID siegeId, int index, World world, int x, int y, int z, long standingSince) {
        var block = world.getBlockAt(x, y, z);
        block.setType(Material.CAMPFIRE);
        if (block.getState() instanceof Campfire campfire) {
            campfire.getPersistentDataContainer().set(ClanItemFactory.SIEGE_ID_KEY, PersistentDataType.STRING, siegeId.toString());
            campfire.getPersistentDataContainer().set(ClanItemFactory.SIEGE_CAMP_INDEX_KEY, PersistentDataType.INTEGER, index);
            campfire.update(true);
        }
        return new SiegeCamp(index, world.getName(), x, y, z, standingSince, false, 0L);
    }

    private void removeCampBlock(SiegeCamp camp) {
        World world = Bukkit.getWorld(camp.world());
        if (world == null) {
            return;
        }
        var block = world.getBlockAt(camp.x(), camp.y(), camp.z());
        if (block.getType() == Material.CAMPFIRE) {
            block.setType(Material.AIR);
        }
    }

    /**
     * Called by the block-break listener when a defender breaks an enemy camp. Returns true if the
     * break was accepted (camp reset, respawn scheduled) - false if the siege/camp couldn't be
     * resolved (already gone, stale reference, etc).
     */
    public boolean breakCamp(UUID siegeId, int campIndex) {
        ClanSiege siege = activeSieges.get(siegeId);
        if (siege == null || siege.state() != SiegeState.ACTIVE || campIndex < 0 || campIndex >= siege.camps().size()) {
            return false;
        }
        SiegeCamp camp = siege.camps().get(campIndex);
        if (camp.broken()) {
            return false;
        }
        long now = System.currentTimeMillis();
        ClanSiege updated = siege.withCamp(camp.brokenNow(now, campRespawnDuration().toMillis()));
        activeSieges.put(siegeId, updated);
        removeCampBlock(camp);

        resolveClans(siege).ifPresent(clans -> {
            onlineMembers(clans.attacker()).forEach(p -> plugin.getMessages().send(p, "siege.camp.broken-attacker",
                    Map.of("index", String.valueOf(campIndex + 1))));
            onlineMembers(clans.defender()).forEach(p -> plugin.getMessages().send(p, "siege.camp.broken-defender",
                    Map.of("index", String.valueOf(campIndex + 1))));
        });
        return true;
    }

    public Optional<ClanTerritory> resolveContestedTerritory(ClanSiege siege) {
        if (siege.contestedTerritory() == null) {
            return Optional.empty();
        }
        return plugin.getClanManager().getClanById(siege.defenderClanId())
                .flatMap(defender -> defender.territories().stream()
                        .filter(t -> t.key().equals(siege.contestedTerritory()))
                        .findFirst());
    }

    public record SiegeCampRef(UUID siegeId, UUID defenderClanId, int campIndex) {}

    /** Finds the active, unbroken camp block (if any) at this exact location. */
    public Optional<SiegeCampRef> findCampAt(Location location) {
        for (ClanSiege siege : activeSieges.values()) {
            if (siege.state() != SiegeState.ACTIVE) continue;
            for (SiegeCamp camp : siege.camps()) {
                if (!camp.broken() && camp.world().equals(location.getWorld().getName())
                        && camp.x() == location.getBlockX() && camp.y() == location.getBlockY() && camp.z() == location.getBlockZ()) {
                    return Optional.of(new SiegeCampRef(siege.id(), siege.defenderClanId(), camp.index()));
                }
            }
        }
        return Optional.empty();
    }

    public boolean areInSiege(UUID first, UUID second) {
        return activeSieges.values().stream().anyMatch(s -> s.between(first, second));
    }

    public boolean isInSiege(UUID clanId) {
        return activeSieges.values().stream().anyMatch(s -> s.involves(clanId));
    }

    public Optional<ClanSiege> findSiege(UUID first, UUID second) {
        return activeSieges.values().stream().filter(s -> s.between(first, second)).findFirst();
    }

    public Collection<ClanSiege> activeSieges() {
        return List.copyOf(activeSieges.values());
    }

    public CompletableFuture<Void> peaceAsync(Clan source, Clan target) {
        return plugin.supplySync(() -> {
            Optional<ClanSiege> siegeOpt = findSiege(source.id(), target.id());
            if (siegeOpt.isEmpty()) {
                throw new IllegalStateException("war.not-at-war");
            }
            endSiege(siegeOpt.get(), SiegeResult.CANCELLED);
            for (UUID memberId : source.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", target.tag(), "color", target.tagColor()));
            }
            for (UUID memberId : target.members().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) plugin.getMessages().send(p, "war.peace", Map.of("tag", source.tag(), "color", source.tagColor()));
            }
            return null;
        });
    }

    private void endSiege(ClanSiege siege, SiegeResult result) {
        activeSieges.remove(siege.id());
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        clearPendingPhase(siege.id(), clansOpt.orElse(null));
        for (SiegeCamp camp : siege.camps()) {
            if (!camp.broken()) {
                removeCampBlock(camp);
            }
        }
        if (clansOpt.isEmpty()) {
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        if (result == SiegeResult.CANCELLED) {
            return;
        }

        Clan winner = result == SiegeResult.ATTACKER_WIN ? attacker : defender;
        Clan loser = result == SiegeResult.ATTACKER_WIN ? defender : attacker;
        onlineMembers(winner).forEach(p -> plugin.getMessages().sendTitle(p, "siege.end.victory-title", "siege.end.victory-subtitle",
                Map.of("tag", loser.tag(), "color", loser.tagColor())));
        onlineMembers(loser).forEach(p -> plugin.getMessages().sendTitle(p, "siege.end.defeat-title", "siege.end.defeat-subtitle",
                Map.of("tag", winner.tag(), "color", winner.tagColor())));

        long reward = plugin.getConfig().getLong("leveling.war-win-exp", 1200L);
        plugin.getClanManager().addExperienceAsync(winner, reward).exceptionally(t -> {
            plugin.getLogger().warning("Failed to award siege experience to clan " + winner.id() + ": " + t.getMessage());
            return null;
        });
        plugin.getClanManager().recordSiegeResultAsync(winner, true).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record siege win for clan " + winner.id() + ": " + t.getMessage());
            return null;
        });
        plugin.getClanManager().recordSiegeResultAsync(loser, false).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record siege loss for clan " + loser.id() + ": " + t.getMessage());
            return null;
        });

        if (result == SiegeResult.ATTACKER_WIN && plugin.getConfig().getBoolean("siege.reward-artifact", true)) {
            grantRandomArtifact(attacker);
        }
    }

    private void grantRandomArtifact(Clan clan) {
        ArtifactType[] types = ArtifactType.values();
        ArtifactType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        Optional<Player> recipient = clan.members().values().stream()
                .filter(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .map(ClanMember::playerId).map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst()
                .or(() -> onlineMembers(clan).findFirst());
        recipient.ifPresent(player -> {
            ItemStack artifact = plugin.getArtifactManager().createArtifact(type);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(artifact);
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            plugin.getMessages().send(player, "siege.artifact-reward");
        });
    }

    public void purgeClan(UUID clanId) {
        siegeCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
    }

    public void endActiveSiegesInvolvingClan(UUID clanId) {
        for (ClanSiege siege : activeSieges()) {
            if (!siege.involves(clanId)) continue;
            endSiege(siege, SiegeResult.CANCELLED);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownDuration().toMillis();
        siegeCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

        for (ClanSiege siege : activeSieges.values()) {
            if (siege.state() == SiegeState.PREPARING) {
                tickPending(siege, now);
                continue;
            }
            tickActive(siege, now);
        }
    }

    private void tickPending(ClanSiege siege, long now) {
        long remainingMs = siege.endsAt() - now;
        if (remainingMs <= 0) {
            activateSiege(siege);
            return;
        }
        BossBar bar = pendingBossBars.get(siege.id());
        Optional<SiegeClans> clansOpt = resolveClans(siege);
        if (bar == null || clansOpt.isEmpty()) {
            return;
        }
        long totalMs = preStartDuration().toMillis();
        bar.progress(Math.max(0f, Math.min(1f, (float) remainingMs / (float) totalMs)));
        bar.name(plugin.getMessages().component("siege.pending.bossbar", Map.of(
                "attacker", clansOpt.get().attacker().tag(), "color1", clansOpt.get().attacker().tagColor(),
                "defender", clansOpt.get().defender().tag(), "color2", clansOpt.get().defender().tagColor(),
                "time", formatDuration(remainingMs))));

        if (remainingMs <= 60_000L && oneMinuteWarned.add(siege.id())) {
            java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "siege.pending.one-minute-warning"));
        }
    }

    private void tickActive(ClanSiege siege, long now) {
        long durationMs = siegeDuration().toMillis();
        boolean allCampsHeld = true;
        List<SiegeCamp> updatedCamps = null;

        for (int i = 0; i < siege.camps().size(); i++) {
            SiegeCamp camp = siege.camps().get(i);
            if (camp.broken()) {
                allCampsHeld = false;
                if (camp.respawnAt() <= now) {
                    SiegeCamp respawned = camp.respawned(now);
                    placeCamp(siege.id(), camp.index(), Bukkit.getWorld(camp.world()), camp.x(), camp.y(), camp.z(), now);
                    if (updatedCamps == null) updatedCamps = new ArrayList<>(siege.camps());
                    updatedCamps.set(i, respawned);
                }
            } else if (now - camp.standingSince() < durationMs) {
                allCampsHeld = false;
            }
        }

        if (updatedCamps != null) {
            ClanSiege updated = new ClanSiege(siege.id(), siege.attackerClanId(), siege.defenderClanId(),
                    siege.contestedTerritory(), siege.startedAt(), siege.endsAt(), siege.state(), List.copyOf(updatedCamps));
            activeSieges.put(updated.id(), updated);
            siege = updated;
        }

        if (allCampsHeld && !siege.camps().isEmpty()) {
            endSiege(siege, SiegeResult.ATTACKER_WIN);
        }
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().values().stream()
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull);
    }
}
