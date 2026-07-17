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
import me.lovelace.loveclans.util.ClanItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanWar> activeWars = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> warCooldowns = new ConcurrentHashMap<>();
    // Прогресс "прочности" знамени во время войны: сколько ударов подряд уже нанесено.
    private final Map<UUID, Integer> bannerHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bannerLastHitAt = new ConcurrentHashMap<>();

    public WarManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Duration warDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("war.duration-minutes", 30));
    }

    private Duration warCooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("war.cooldown-hours", 24));
    }

    private Duration bannerCaptureDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("war.banner-capture-minutes", 3));
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

            if (lastWarTime != null && (now - lastWarTime < warCooldownDuration().toMillis())) {
                long remainingSeconds = (warCooldownDuration().toMillis() - (now - lastWarTime)) / 1000;
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
                    now + warDuration().toMillis(), WarState.ACTIVE, 0, 0);
            ClanWarStartEvent event = new ClanWarStartEvent(war);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            activeWars.put(war.id(), war);
            warCooldowns.put(cooldownKey, now);

            distributeWarCompasses(war);
            resolveContestedTerritory(war).ifPresent(t ->
                    plugin.getAdvancedClaimsHook().setSiegeMode(t.advancedClaimId(), true));
            announceWarStart(war, attacker, defender);

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
                announceWarEnd(war, result);
                confiscateWarItems(war);
                endSiege(war);
                bannerHits.remove(war.id());
                bannerLastHitAt.remove(war.id());
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

            announceWarEnd(war, WarResult.DRAW);
            confiscateWarItems(war);
            endSiege(war);
            bannerHits.remove(war.id());
            bannerLastHitAt.remove(war.id());

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
            announceCaptureReset(war);
        }
    }

    /**
     * Резолвит территорию защитника, соответствующую оспариваемому ключу этой войны (может
     * отсутствовать, если война была начата без территории - например, через админ-команду).
     */
    public Optional<ClanTerritory> resolveContestedTerritory(ClanWar war) {
        if (war.contestedTerritory() == null) {
            return Optional.empty();
        }
        return plugin.getClanManager().getClanById(war.defenderClanId())
                .flatMap(defender -> defender.territories().stream()
                        .filter(t -> t.key().equals(war.contestedTerritory()))
                        .findFirst());
    }

    /**
     * Находит активную войну, в которой указанный клан - защитник, а данная локация совпадает
     * со знаменем оспариваемой территории этой войны. Используется для защиты/захвата знамени:
     * ломать (и тем более захватывать) можно только знамя, реально оспариваемое конкретной войной,
     * а не любой баннер клана просто потому, что клан с кем-то воюет.
     */
    public Optional<ClanWar> findWarByContestedBannerLocation(UUID defenderClanId, Location location) {
        for (ClanWar war : activeWars.values()) {
            if (war.state() != WarState.ACTIVE || !war.defenderClanId().equals(defenderClanId)) {
                continue;
            }
            Optional<ClanTerritory> territoryOpt = resolveContestedTerritory(war);
            if (territoryOpt.isEmpty()) {
                continue;
            }
            ClanTerritory territory = territoryOpt.get();
            if (territory.bannerX() == null || territory.bannerY() == null || territory.bannerZ() == null) {
                continue;
            }
            if (!territory.key().world().equals(location.getWorld().getName())) {
                continue;
            }
            if (territory.bannerX() == location.getBlockX()
                    && territory.bannerY() == location.getBlockY()
                    && territory.bannerZ() == location.getBlockZ()) {
                return Optional.of(war);
            }
        }
        return Optional.empty();
    }

    /**
     * Регистрирует очередной удар по осаждаемому знамени и возвращает суммарное число ударов
     * подряд. Прогресс сбрасывается, если между ударами прошло больше resetMs.
     */
    public int registerBannerHit(UUID warId, long resetMs) {
        long now = System.currentTimeMillis();
        Long lastHit = bannerLastHitAt.get(warId);
        if (lastHit == null || now - lastHit > resetMs) {
            bannerHits.put(warId, 1);
        } else {
            bannerHits.merge(warId, 1, Integer::sum);
        }
        bannerLastHitAt.put(warId, now);
        return bannerHits.getOrDefault(warId, 1);
    }

    public void resetBannerHits(UUID warId) {
        bannerHits.remove(warId);
        bannerLastHitAt.remove(warId);
    }

    /**
     * Знамя территории сломано атакующим - запускает отсчёт до капитуляции клана-защитника
     * и уведомляет обе стороны.
     */
    public void startBannerCapture(ClanWar war, UUID carrierId) {
        activeWars.put(war.id(), war.withBannerCapture(carrierId, System.currentTimeMillis()));
        notifyBannerBroken(war, carrierId);
    }

    public void purgeClan(UUID clanId) {
        warCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
    }

    public void tick() {
        long now = System.currentTimeMillis();
        warCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= warCooldownDuration().toMillis());

        for (ClanWar war : activeWars.values()) {
            if (war.capturedBannerBy() != null) {
                long remainingMs = bannerCaptureDuration().toMillis() - (now - war.bannerCapturedAt());
                if (remainingMs <= 0) {
                    endWarAsync(war.id(), WarResult.ATTACKER_WIN);
                    continue;
                }
                broadcastCapitulationCountdown(war, remainingMs);
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

        Optional<ClanTerritory> defenderTerritoryOpt = resolveContestedTerritory(war);

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
                .forEach(player -> giveTrackingCompass(player, war, bannerLocation, defenderClan));

        defenderClan.members().values().stream()
                .filter(member -> (member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN))
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> giveTrackingCompass(player, war, bannerLocation, attackerClan));
    }

    private void giveTrackingCompass(Player player, ClanWar war, Location targetLocation, Clan enemyClan) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ClanItemFactory.WAR_COMPASS_KEY, PersistentDataType.STRING, war.id().toString());
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
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> clearWarCompass(player, war.id())));

        defenderClanOpt.ifPresent(clan -> clan.members().values().stream()
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> clearWarCompass(player, war.id())));
    }

    private void clearWarCompass(Player player, UUID warId) {
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String taggedWarId = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.WAR_COMPASS_KEY, PersistentDataType.STRING);
                if (warId.toString().equals(taggedWarId)) {
                    player.getInventory().setItem(i, null);
                    plugin.getMessages().send(player, "war.compass-removed");
                }
            }
        }
    }

    /**
     * Изымает предметы, связанные с этой войной, у всех вовлечённых игроков: боевые компасы
     * (у обеих сторон) и захваченное знамя (если оно ещё у носителя) - независимо от того, как
     * закончилась война (мир, победа, поражение или истечение времени).
     */
    private void confiscateWarItems(ClanWar war) {
        removeWarCompasses(war);

        if (war.capturedBannerBy() != null) {
            Player carrier = Bukkit.getPlayer(war.capturedBannerBy());
            if (carrier != null) {
                for (int i = 0; i < carrier.getInventory().getSize(); i++) {
                    ItemStack item = carrier.getInventory().getItem(i);
                    if (plugin.getClanManager().getClanItemFactory().isCapturedBanner(item, war.id())) {
                        carrier.getInventory().setItem(i, null);
                        plugin.getMessages().send(carrier, "war.banner.confiscated");
                    }
                }
            }
        }
    }

    private void endSiege(ClanWar war) {
        resolveContestedTerritory(war).ifPresent(t -> plugin.getAdvancedClaimsHook().setSiegeMode(t.advancedClaimId(), false));
    }

    private void announceWarStart(ClanWar war, Clan attacker, Clan defender) {
        onlineMembers(attacker).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.start.attacker-title", "war.start.attacker-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor()));
            plugin.getMessages().playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
        });
        onlineMembers(defender).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.start.defender-title", "war.start.defender-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
            plugin.getMessages().playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
        });
    }

    private void announceWarEnd(ClanWar war, WarResult result) {
        Optional<Clan> attackerOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderOpt = plugin.getClanManager().getClanById(war.defenderClanId());
        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return;
        }
        Clan attacker = attackerOpt.get();
        Clan defender = defenderOpt.get();

        if (result == WarResult.DRAW) {
            onlineMembers(attacker).forEach(player -> {
                plugin.getMessages().sendTitle(player, "war.end.draw-title", "war.end.draw-subtitle",
                        Map.of("tag", defender.tag(), "color", defender.tagColor()));
                plugin.getMessages().playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            });
            onlineMembers(defender).forEach(player -> {
                plugin.getMessages().sendTitle(player, "war.end.draw-title", "war.end.draw-subtitle",
                        Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
                plugin.getMessages().playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            });
            return;
        }

        Clan winner = result == WarResult.ATTACKER_WIN ? attacker : defender;
        Clan loser = result == WarResult.ATTACKER_WIN ? defender : attacker;

        onlineMembers(winner).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.end.victory-title", "war.end.victory-subtitle",
                    Map.of("tag", loser.tag(), "color", loser.tagColor()));
            plugin.getMessages().playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        });
        onlineMembers(loser).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.end.defeat-title", "war.end.defeat-subtitle",
                    Map.of("tag", winner.tag(), "color", winner.tagColor()));
            plugin.getMessages().playSound(player, Sound.ENTITY_WITHER_DEATH, 1f, 1f);
        });
    }

    private void notifyBannerBroken(ClanWar war, UUID carrierId) {
        Optional<Clan> attackerOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderOpt = plugin.getClanManager().getClanById(war.defenderClanId());
        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return;
        }
        Clan attacker = attackerOpt.get();
        Clan defender = defenderOpt.get();

        onlineMembers(defender).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.banner.broken-defender-title", "war.banner.broken-defender-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
            plugin.getMessages().playSound(player, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        });
        onlineMembers(attacker).forEach(player -> {
            plugin.getMessages().sendTitle(player, "war.banner.broken-attacker-title", "war.banner.broken-attacker-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor()));
            plugin.getMessages().playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        });
    }

    private void announceCaptureReset(ClanWar war) {
        plugin.getClanManager().getClanById(war.attackerClanId()).ifPresent(attacker ->
                onlineMembers(attacker).forEach(player -> plugin.getMessages().send(player, "war.banner.capture-reset")));
        plugin.getClanManager().getClanById(war.defenderClanId()).ifPresent(defender ->
                onlineMembers(defender).forEach(player -> plugin.getMessages().send(player, "war.banner.capture-reset")));
    }

    private void broadcastCapitulationCountdown(ClanWar war, long remainingMs) {
        String time = formatDuration(remainingMs);
        Optional<Clan> attackerOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderOpt = plugin.getClanManager().getClanById(war.defenderClanId());
        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return;
        }
        Clan defender = defenderOpt.get();

        Player carrier = Bukkit.getPlayer(war.capturedBannerBy());
        String carrierName = carrier != null ? carrier.getName() : "?";
        if (carrier != null) {
            carrier.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 40, 0, false, false, false));
        }

        onlineMembers(defenderOpt.get()).forEach(player ->
                plugin.getMessages().sendActionBar(player, "war.banner.capitulation-actionbar-defender",
                        Map.of("time", time, "player", carrierName)));
        onlineMembers(attackerOpt.get()).forEach(player ->
                plugin.getMessages().sendActionBar(player, "war.banner.capitulation-actionbar-attacker",
                        Map.of("time", time, "tag", defender.tag(), "color", defender.tagColor())));
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
