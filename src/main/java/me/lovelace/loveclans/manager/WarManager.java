package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanWarEndEvent;
import me.lovelace.loveclans.api.events.ClanWarStartEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import org.bukkit.util.BoundingBox;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.war.WarResult;
import me.lovelace.loveclans.model.war.WarState;
import me.lovelace.loveclans.util.ClanItemFactory;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WarManager {
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanWar> activeWars = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> warCooldowns = new ConcurrentHashMap<>();
    /** Full snapshot (facing, patterns, PDC) of each suppressed banner, so it comes back exactly as it stood. */
    private final Map<UUID, org.bukkit.block.BlockState> originalContestedBanners = new ConcurrentHashMap<>();
    /** Boss bars of wars in the ACTIVE phase: score, time left and banner state for both sides. */
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();

    /** Restores the pair cooldowns after a restart (blocking read, called once from an async startup task). */
    public void loadCooldowns() {
        plugin.getConflictCooldownStore().loadInto(me.lovelace.loveclans.storage.ConflictCooldownStore.WAR, warCooldowns);
    }
    // Прогресс "прочности" знамени во время войны: сколько ударов подряд уже нанесено и когда был
    // последний удар. Один record на войну вместо двух параллельных карт - чтобы оба значения
    // всегда обновлялись/удалялись вместе и не могли разойтись.
    private record BannerProgress(int hits, long lastHitAt) {}
    private final Map<UUID, BannerProgress> bannerProgress = new ConcurrentHashMap<>();
    /** Войны, где один из кланов идёт на реванш: id войны -> клан, которому положена прибавка. */
    private final Map<UUID, UUID> rematchClaims = new ConcurrentHashMap<>();
    /** Когда по этой войне в последний раз начисляли очки за удержание спорной территории. */
    private final Map<UUID, Long> lastControlAward = new ConcurrentHashMap<>();
    // Боевой таймер (§3.2): босс-бар и разовое предупреждение "за минуту", показываемые обеим
    // сторонам, пока война находится в состоянии PREPARING (объявлена, но ещё не началась).
    private final Map<UUID, BossBar> pendingBossBars = new ConcurrentHashMap<>();
    private final Set<UUID> oneMinuteWarned = ConcurrentHashMap.newKeySet();

    public WarManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Duration preStartDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("war.pre-start-minutes", 10));
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

    private boolean isTerritoryAlreadyContested(UUID defenderClanId, TerritoryKey territoryKey) {
        return activeWars.values().stream().anyMatch(w ->
                w.defenderClanId().equals(defenderClanId) && territoryKey.equals(w.contestedTerritory()));
    }

    public CompletableFuture<ClanWar> startWarAsync(Clan attacker, Clan defender, TerritoryKey territory) {
        return startWarAsync(attacker, defender, territory, false);
    }

    /**
     * @param force пропускает все проверки готовности (кулдаун, минимум онлайн, союз, конфликт
     *              осады/набега и т.д.) — только для тестовой admin-команды
     *              ({@code /loveclansadmin war forcestart}). Событие {@link ClanWarStartEvent} и
     *              весь остальной эффект (индексация, кулдаун, снятие блокад) отрабатывают как обычно.
     */
    public CompletableFuture<ClanWar> startWarAsync(Clan attacker, Clan defender, TerritoryKey territory, boolean force) {
        return plugin.supplySync(() -> {
            // Never skipped, not even by force: a clan fighting itself farms its own rewards and loot.
            if (attacker.id().equals(defender.id())) {
                throw new IllegalStateException("war.cannot-target-self");
            }
            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = getWarPairKey(attacker.id(), defender.id());
            long now = System.currentTimeMillis();

            if (!force) {
                // Без установленной капитальной территории войну объявлять некуда/не за что —
                // компас, захват знамени и осадный режим territory-based, и не работали бы ни для
                // атакующего (нечего защищать при ответном ударе), ни для защитника (WarManager
                // резолвит оспариваемую территорию по capital/territory клана). force=true
                // (тестовая admin-команда forcestart) намеренно пропускает эту проверку тоже.
                if (!attacker.hasCapital()) {
                    throw new IllegalStateException("war.attacker-no-capital");
                }
                if (!defender.hasCapital()) {
                    throw new IllegalStateException("war.defender-no-capital");
                }
                if (activeWars.size() >= plugin.getConfig().getInt("war.max-concurrent", 3)) {
                    throw new IllegalStateException("war.max-wars-reached");
                }
                if (areAtWar(attacker.id(), defender.id())) {
                    throw new IllegalStateException("war.already-at-war");
                }
                // Осада/набег выставляют своё состояние на тех же кланах и территориях, поэтому война
                // поверх них приводит к конфликту осадных режимов (одна закончится и снимет siege mode,
                // пока вторая ещё идёт). SiegeManager и RaidManager симметрично отказывают в старте,
                // если уже идёт война, - без этой проверки ограничение обходилось порядком объявления.
                if (plugin.getSiegeManager().isInSiege(attacker.id()) || plugin.getSiegeManager().isInSiege(defender.id())) {
                    throw new IllegalStateException("siege.already-in-siege");
                }
                if (plugin.getRaidManager().isInRaid(attacker.id()) || plugin.getRaidManager().isInRaid(defender.id())) {
                    throw new IllegalStateException("raid.already-in-raid");
                }
                if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                    throw new IllegalStateException("war.cannot-declare-on-ally");
                }
                // Two different attackers contesting the same defender territory would share one
                // siege flag on the defender's claim; whichever war ends first would incorrectly
                // lift siege mode while the other is still active. Simplest correct fix: only one
                // war may contest a given territory at a time.
                if (territory != null && isTerritoryAlreadyContested(defender.id(), territory)) {
                    throw new IllegalStateException("war.territory-already-contested");
                }

                Long lastWarTime = warCooldowns.get(cooldownKey);
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

                int minOnline = plugin.getConfig().getInt("war.min-online", 3);
                if (attackerOnline < minOnline || defenderOnline < minOnline) {
                    throw new IllegalStateException("war.not-enough-members");
                }

                boolean defenderHasOnlineLeaderOrGuardian = defender.members().values().stream()
                        .filter(member -> member.rank() == ClanRank.LEADER || member.rank() == ClanRank.GUARDIAN)
                        .anyMatch(member -> Bukkit.getPlayer(member.playerId()) != null);

                if (!defenderHasOnlineLeaderOrGuardian) {
                    throw new IllegalStateException("war.defender-no-online-leader-or-guardian");
                }
            }

            ClanWar war = new ClanWar(UUID.randomUUID(), attacker.id(), defender.id(), territory, now,
                    now + preStartDuration().toMillis(), WarState.PREPARING, 0, 0);
            ClanWarStartEvent event = new ClanWarStartEvent(war);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            activeWars.put(war.id(), war);
            warCooldowns.put(cooldownKey, now);
            plugin.getConflictCooldownStore().saveAsync(me.lovelace.loveclans.storage.ConflictCooldownStore.WAR, cooldownKey, now);
            plugin.getDiplomacyManager().liftBlockadesBetween(attacker.id(), defender.id());

            beginPendingPhase(war, attacker, defender);
            me.lovelace.loveclans.activity.ConflictEvents.declared(me.lovelace.loveclans.model.history.ConflictKind.WAR,
                    war.id(), attacker.id(), defender.id());

            return war;
        });
    }

    /** Shows the pending-phase boss bar/chat message to both sides; does not touch gameplay yet. */
    private void beginPendingPhase(ClanWar war, Clan attacker, Clan defender) {
        String time = formatDuration(war.endsAt() - System.currentTimeMillis());
        Component title = plugin.getMessages().component("war.pending.bossbar", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(), "time", time));
        BossBar bar = BossBar.bossBar(title, 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        pendingBossBars.put(war.id(), bar);

        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "war.pending.declared",
                Map.of("tag", defender.tag(), "color", defender.tagColor(), "time", time)));
        onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "war.pending.declared",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor(), "time", time)));
    }

    /** Promotes a PREPARING war to ACTIVE: gameplay (compasses, siege mode, start titles) begins now. */
    private void activateWar(ClanWar war) {
        Optional<WarClans> warClansOpt = resolveWarClans(war);
        clearPendingPhase(war.id(), warClansOpt.orElse(null));
        if (warClansOpt.isEmpty()) {
            activeWars.remove(war.id());
            return;
        }
        long now = System.currentTimeMillis();
        ClanWar activated = war.activate(now + warDuration().toMillis());
        activeWars.put(activated.id(), activated);

        distributeWarCompasses(activated);
        checkRematch(activated);
        resolveContestedTerritory(activated).ifPresent(t ->
                plugin.getAdvancedClaimsHook().setSiegeMode(t.advancedClaimId(), true));
        announceWarStart(activated, warClansOpt.get().attacker(), warClansOpt.get().defender());
        beginActivePhase(activated, warClansOpt.get().attacker(), warClansOpt.get().defender());
    }

    /**
     * Admin tool ({@code /loveclansadmin war skip}): ends the preparation timer of the war between the two clans and
     * starts the fight right now. Main thread only.
     *
     * @return false when there is no war in preparation between them
     */
    public boolean skipPreparation(UUID clanA, UUID clanB) {
        for (ClanWar war : activeWars.values()) {
            if (war.state() != WarState.PREPARING) continue;
            boolean pair = (war.attackerClanId().equals(clanA) && war.defenderClanId().equals(clanB))
                    || (war.attackerClanId().equals(clanB) && war.defenderClanId().equals(clanA));
            if (pair) {
                activateWar(war);
                return true;
            }
        }
        return false;
    }

    /** The war this clan is currently in (any phase), if any. */
    public Optional<ClanWar> warOf(UUID clanId) {
        return activeWars.values().stream()
                .filter(w -> w.attackerClanId().equals(clanId) || w.defenderClanId().equals(clanId))
                .findFirst();
    }

    /** Time left of the current phase as m:ss, for messages. */
    public String remainingText(ClanWar war) {
        return formatDuration(war.endsAt() - System.currentTimeMillis());
    }

    /** Where the banner at stake stands, if the war has a resolvable territory with banner coordinates. */
    public Optional<Location> contestedBannerLocation(ClanWar war) {
        return resolveContestedTerritory(war).flatMap(t -> {
            if (t.bannerX() == null || t.bannerY() == null || t.bannerZ() == null) return Optional.empty();
            org.bukkit.World world = Bukkit.getWorld(t.key().world());
            return world == null ? Optional.empty() : Optional.of(new Location(world, t.bannerX(), t.bannerY(), t.bannerZ()));
        });
    }

    private boolean broadcastEnabled() {
        return plugin.getConfig().getBoolean("war.broadcast.enabled", true);
    }

    private boolean effectsEnabled() {
        return plugin.getConfig().getBoolean("war.effects.enabled", true);
    }

    private Component activeBarTitle(ClanWar war, Clan attacker, Clan defender, long remainingMs) {
        return plugin.getMessages().component("war.bossbar.active", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(),
                "s1", String.valueOf(war.attackerScore()), "s2", String.valueOf(war.defenderScore()),
                "time", formatDuration(remainingMs),
                "banner", plugin.getMessages().raw(war.isBannerSuppressed() ? "war.bossbar.banner-broken" : "war.bossbar.banner-intact")));
    }

    private void beginActivePhase(ClanWar war, Clan attacker, Clan defender) {
        BossBar bar = BossBar.bossBar(activeBarTitle(war, attacker, defender, war.endsAt() - System.currentTimeMillis()),
                1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        activeBossBars.put(war.id(), bar);
        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        if (broadcastEnabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getMessages().send(p, "war.broadcast.started", Map.of(
                        "attacker", attacker.tag(), "color1", attacker.tagColor(),
                        "defender", defender.tag(), "color2", defender.tagColor()));
            }
        }
    }

    private void updateActiveBar(ClanWar war, long now) {
        BossBar bar = activeBossBars.get(war.id());
        Optional<WarClans> clans = resolveWarClans(war);
        if (bar == null || clans.isEmpty()) return;
        long remaining = war.endsAt() - now;
        long total = warDuration().toMillis();
        bar.progress(Math.max(0f, Math.min(1f, (float) remaining / (float) Math.max(1L, total))));
        bar.color(remaining <= 300_000L ? BossBar.Color.YELLOW : BossBar.Color.RED);
        bar.name(activeBarTitle(war, clans.get().attacker(), clans.get().defender(), Math.max(0L, remaining)));
    }

    /** A red beam over the banner everyone is fighting for (grey while the banner is suppressed). */
    private void spawnBannerEffects(ClanWar war) {
        if (!effectsEnabled()) return;
        contestedBannerLocation(war).ifPresent(loc -> {
            org.bukkit.World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
            Particle.DustOptions dust = new Particle.DustOptions(
                    war.isBannerSuppressed() ? Color.GRAY : Color.RED, 1.6f);
            for (int i = 0; i < 10; i++) {
                world.spawnParticle(Particle.DUST, loc.getX() + 0.5, loc.getY() + 1.0 + i * 0.6, loc.getZ() + 0.5,
                        2, 0.15, 0.1, 0.15, 0.0, dust);
            }
        });
    }

    /** Late joiners and re-logins get the bars and the compass of the war their clan is in. */
    public void syncPlayer(Player player) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).flatMap(c -> warOf(c.id())).ifPresent(war -> {
            BossBar pending = pendingBossBars.get(war.id());
            if (pending != null) player.showBossBar(pending);
            BossBar active = activeBossBars.get(war.id());
            if (active != null) player.showBossBar(active);
            if (war.state() == WarState.ACTIVE && !hasWarCompass(player, war.id())) {
                Optional<Location> target = contestedBannerLocation(war);
                Optional<Clan> enemy = plugin.getClanManager().getClanById(
                        war.attackerClanId().equals(plugin.getClanManager().getPlayerClan(player.getUniqueId()).map(Clan::id).orElse(null))
                                ? war.defenderClanId() : war.attackerClanId());
                if (target.isPresent() && enemy.isPresent()) {
                    giveTrackingCompass(player, war, target.get(), enemy.get());
                }
            }
        });
    }

    private boolean hasWarCompass(Player player, UUID warId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String tagged = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.WAR_COMPASS_KEY, PersistentDataType.STRING);
                if (warId.toString().equals(tagged)) return true;
            }
        }
        return false;
    }

    /** Server-wide result announcement; the prize of a war is the tribute (ModifierManager), not treasury loot. */
    private void announceResult(ClanWar war, WarResult result) {
        if (result != WarResult.ATTACKER_WIN && result != WarResult.DEFENDER_WIN) return;
        Optional<WarClans> clans = resolveWarClans(war);
        if (clans.isEmpty()) return;
        Clan winner = result == WarResult.ATTACKER_WIN ? clans.get().attacker() : clans.get().defender();
        Clan loser = result == WarResult.ATTACKER_WIN ? clans.get().defender() : clans.get().attacker();

        if (broadcastEnabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getMessages().send(p, "war.broadcast.ended", Map.of(
                        "winner", winner.tag(), "color1", winner.tagColor(),
                        "loser", loser.tag(), "color2", loser.tagColor(),
                        "s1", String.valueOf(result == WarResult.ATTACKER_WIN ? war.attackerScore() : war.defenderScore()),
                        "s2", String.valueOf(result == WarResult.ATTACKER_WIN ? war.defenderScore() : war.attackerScore())));
            }
        }
    }

    private void clearPendingPhase(UUID warId, WarClans clans) {
        BossBar bar = pendingBossBars.remove(warId);
        BossBar activeBar = activeBossBars.remove(warId);
        oneMinuteWarned.remove(warId);
        if (clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> {
                        if (bar != null) p.hideBossBar(bar);
                        if (activeBar != null) p.hideBossBar(activeBar);
                    });
        }
    }

    public CompletableFuture<Void> endWarAsync(UUID warId, WarResult result) {
        return plugin.supplySync(() -> {
            ClanWar war = activeWars.remove(warId);
            if (war != null) {
                clearPendingPhase(war.id(), resolveWarClans(war).orElse(null));
                Bukkit.getPluginManager().callEvent(new ClanWarEndEvent(war.withState(WarState.FINISHED), result));

                // Notifications/cleanup must run BEFORE any disband below: disbandClanAsync runs
                // synchronously (already on the main thread) and unindexes the defender clan, so
                // if it ran first, announceWarEnd/confiscateWarItems/endSiege would find the
                // defender already gone and silently skip the victory/defeat titles and the
                // defender's compass confiscation for this exact war.
                announceWarEnd(war, result);
                resetStreaks(war);
                restoreBannerBlock(war);
                confiscateWarItems(war);
                endSiege(war);
                resetBannerHits(war.id());
                rematchClaims.remove(war.id());
                lastControlAward.remove(war.id());
                archiveWar(war, result);
                me.lovelace.loveclans.activity.ConflictEvents.resolved(plugin, me.lovelace.loveclans.model.history.ConflictKind.WAR,
                        war.id(), war.attackerClanId(), war.defenderClanId(), outcomeOf(result));

                announceResult(war, result);

                long reward = plugin.getConfig().getLong("leveling.war-win-exp", 1200L);
                int durationDays = plugin.getConfig().getInt("war.reparations.duration-days", 5);
                long dailyAmount = plugin.getConfig().getLong("war.reparations.daily-amount", 500L);

                if (result == WarResult.ATTACKER_WIN) {
                    plugin.getClanManager().getClanById(war.attackerClanId()).ifPresent(clan -> {
                        plugin.getClanManager().addExperienceAsync(clan, reward).exceptionally(t -> {
                            plugin.getLogger().warning("Failed to award war experience to clan " + clan.id() + ": " + t.getMessage());
                            return null;
                        });
                        plugin.getClanManager().recordWarResultAsync(clan, true).exceptionally(t -> {
                            plugin.getLogger().warning("Failed to record war win for clan " + clan.id() + ": " + t.getMessage());
                            return null;
                        });
                        reportWarWinToCore(clan.id());
                    });
                    plugin.getClanManager().getClanById(war.defenderClanId()).ifPresent(clan ->
                            plugin.getClanManager().recordWarResultAsync(clan, false).exceptionally(t -> {
                                plugin.getLogger().warning("Failed to record war loss for clan " + clan.id() + ": " + t.getMessage());
                                return null;
                            }));
                    plugin.getDiplomacyManager().liftBlockadeOnVictory(war.attackerClanId(), war.defenderClanId());

                    // Репарации и Casus мести (newwars-1.md)
                    plugin.getModifierManager().grantReparations(war.defenderClanId(), war.attackerClanId(), durationDays, dailyAmount);
                    plugin.getModifierManager().grantJustCasusBoth(war.defenderClanId(), war.attackerClanId(), "revenge_war");

                } else if (result == WarResult.DEFENDER_WIN) {
                    plugin.getClanManager().getClanById(war.defenderClanId()).ifPresent(clan -> {
                        plugin.getClanManager().addExperienceAsync(clan, reward).exceptionally(t -> {
                            plugin.getLogger().warning("Failed to award war experience to clan " + clan.id() + ": " + t.getMessage());
                            return null;
                        });
                        plugin.getClanManager().recordWarResultAsync(clan, true).exceptionally(t -> {
                            plugin.getLogger().warning("Failed to record war win for clan " + clan.id() + ": " + t.getMessage());
                            return null;
                        });
                        reportWarWinToCore(clan.id());
                    });
                    plugin.getClanManager().getClanById(war.attackerClanId()).ifPresent(clan ->
                            plugin.getClanManager().recordWarResultAsync(clan, false).exceptionally(t -> {
                                plugin.getLogger().warning("Failed to record war loss for clan " + clan.id() + ": " + t.getMessage());
                                return null;
                            }));
                    plugin.getDiplomacyManager().liftBlockadeOnVictory(war.defenderClanId(), war.attackerClanId());

                    // Репарации и Casus мести (newwars-1.md)
                    plugin.getModifierManager().grantReparations(war.attackerClanId(), war.defenderClanId(), durationDays, dailyAmount);
                    plugin.getModifierManager().grantJustCasusBoth(war.attackerClanId(), war.defenderClanId(), "revenge_war");
                }
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
            clearPendingPhase(war.id(), resolveWarClans(war).orElse(null));
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
            // A suppressed banner has to come back, whatever way the war ended
            restoreBannerBlock(war);
            confiscateWarItems(war);
            endSiege(war);
            resetBannerHits(war.id());
            rematchClaims.remove(war.id());
            lastControlAward.remove(war.id());
            resetStreaks(war);
            archiveWar(war, WarResult.DRAW);
            me.lovelace.loveclans.activity.ConflictEvents.resolved(plugin, me.lovelace.loveclans.model.history.ConflictKind.WAR,
                    war.id(), war.attackerClanId(), war.defenderClanId(), me.lovelace.loveclans.api.events.ConflictOutcome.DRAW);

            return null;
        });
    }

    /** True for both PREPARING (declared, not yet active) and ACTIVE wars - see {@link #isAtWar(UUID)}. */
    public boolean areAtWar(UUID firstClanId, UUID secondClanId) {
        return activeWars.values().stream().anyMatch(war -> war.between(firstClanId, secondClanId));
    }

    public boolean isAtWar(UUID clanId) {
        return activeWars.values().stream().anyMatch(war -> war.involves(clanId));
    }

    /** Matches PREPARING or ACTIVE - FINISHED wars are never kept in {@code activeWars}. */
    public Optional<ClanWar> activeWar(UUID firstClanId, UUID secondClanId) {
        return activeWars.values().stream()
                .filter(war -> war.between(firstClanId, secondClanId))
                .findFirst();
    }

    public Optional<ClanWar> getActiveWar(UUID clanId) {
        return activeWars.values().stream()
                .filter(war -> war.involves(clanId))
                .findFirst();
    }

    public Collection<ClanWar> activeWars() {
        return List.copyOf(activeWars.values());
    }

    public void addKillScore(UUID killerClanId, UUID victimClanId) {
        addScore(killerClanId, victimClanId, 1);
    }

    public void addKillScore(Player killer, Player victim, Clan killerClan, Clan victimClan) {
        // Only kills inside a running war count: no points, streaks or sounds for a plain fight between two clans
        if (killerClan.id().equals(victimClan.id())
                || activeWar(killerClan.id(), victimClan.id()).filter(w -> w.state() == WarState.ACTIVE).isEmpty()) {
            return;
        }
        int baseScore = plugin.getConfig().getInt("war.objectives.kill-score", 1);
        ClanMember victimMember = victimClan.member(victim.getUniqueId()).orElse(null);
        if (victimMember != null) {
            if (victimMember.rank() == ClanRank.LEADER) {
                baseScore = plugin.getConfig().getInt("war.objectives.kill-score-leader", 5);
            } else if (victimMember.rank() == ClanRank.GUARDIAN) {
                baseScore = plugin.getConfig().getInt("war.objectives.kill-score-guardian", 3);
            }
        }

        int currentStreak = killStreaks.compute(killer.getUniqueId(), (k, v) -> v == null ? 1 : v + 1);
        killStreaks.remove(victim.getUniqueId());

        int bonus = 0;
        if (currentStreak == 3) {
            bonus = 2;
            plugin.getMessages().send(killer, "war.streak", Map.of("kills", "3", "points", "2"));
        } else if (currentStreak == 5) {
            bonus = 5;
            plugin.getMessages().send(killer, "war.streak", Map.of("kills", "5", "points", "5"));
        }

        activeWar(killerClan.id(), victimClan.id()).ifPresent(w ->
                me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, w.id(), killer, killerClan.id()));
        addScore(killerClan.id(), victimClan.id(), baseScore + bonus);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    /** A finished war ends every streak of both clans: the next war starts from zero. */
    private void resetStreaks(ClanWar war) {
        resolveWarClans(war).ifPresent(c -> java.util.stream.Stream.concat(c.attacker().members().keySet().stream(),
                c.defender().members().keySet().stream()).forEach(killStreaks::remove));
    }

    /**
     * Начисляет очки войны с учётом прибавки за реванш. Все источники очков должны идти
     * через этот метод, иначе реванш будет работать только для убийств.
     */
    public void addScore(UUID scoringClanId, UUID opponentClanId, int amount) {
        if (amount <= 0) {
            return;
        }
        // Points count only once the war is ACTIVE: during PREPARING nobody may bank a head start.
        activeWar(scoringClanId, opponentClanId).filter(w -> w.state() == WarState.ACTIVE).ifPresent(war -> {
            int awarded = applyRematchBonus(war.id(), scoringClanId, amount);
            ClanWar updated = scoringClanId.equals(war.attackerClanId())
                    ? war.addAttackerScore(awarded)
                    : war.addDefenderScore(awarded);
            activeWars.put(war.id(), updated);
        });
    }

    /**
     * Прибавка клану, который в прошлый раз проиграл этому же противнику. Округляем вверх,
     * иначе при мелких начислениях бонус пропадал бы целиком.
     */
    private int applyRematchBonus(UUID warId, UUID scoringClanId, int amount) {
        if (!scoringClanId.equals(rematchClaims.get(warId))) {
            return amount;
        }
        double percent = plugin.getConfig().getDouble("history.rematch-bonus-percent", 15.0);
        if (percent <= 0) {
            return amount;
        }
        return (int) Math.ceil(amount * (1.0 + percent / 100.0));
    }

    /**
     * Смотрит в архив: если клан проиграл прошлый конфликт этому же противнику, война
     * для него становится реваншем с прибавкой к очкам. До появления архива история
     * противостояния нигде не хранилась, и такой бонус выдать было не из чего.
     */
    private void checkRematch(ClanWar war) {
        double percent = plugin.getConfig().getDouble("history.rematch-bonus-percent", 15.0);
        if (percent <= 0) {
            return;
        }
        plugin.getConflictArchive().historyBetween(war.attackerClanId(), war.defenderClanId(), 1)
                .thenAccept(records -> {
                    if (records.isEmpty()) {
                        return;
                    }
                    var previous = records.get(0);
                    UUID avenger = previous.lostBy(war.attackerClanId()) ? war.attackerClanId()
                            : previous.lostBy(war.defenderClanId()) ? war.defenderClanId()
                            : null;
                    if (avenger == null) {
                        return;
                    }
                    plugin.runSync(() -> {
                        rematchClaims.put(war.id(), avenger);
                        plugin.getClanManager().getClanById(avenger).ifPresent(clan ->
                                onlineMembers(clan).forEach(player -> plugin.getMessages().send(player,
                                        "history.rematch-bonus",
                                        Map.of("percent", String.valueOf((int) Math.round(percent))))));
                    });
                });
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
        BannerProgress previous = bannerProgress.get(warId);
        int hits = (previous == null || now - previous.lastHitAt() > resetMs) ? 1 : previous.hits() + 1;
        bannerProgress.put(warId, new BannerProgress(hits, now));
        return hits;
    }

    public void resetBannerHits(UUID warId) {
        bannerProgress.remove(warId);
    }

    /** Сколько ударов подряд нужно нанести по знамени территории, прежде чем оно сломается. */
    public int bannerBreakHitsRequired() {
        return Math.max(1, plugin.getConfig().getInt("war.banner-break-hits", 5));
    }

    /** Через сколько мс без ударов прогресс поломки знамени сбрасывается. */
    public long bannerBreakResetMillis() {
        return Math.max(1, plugin.getConfig().getLong("war.banner-break-progress-reset-seconds", 15)) * 1000L;
    }

    public void suppressBanner(ClanWar war) {
        int suppressScore = plugin.getConfig().getInt("war.banner-suppress-score", 25);
        ClanWar updated = war.withBannerSuppressed(true, System.currentTimeMillis()).addAttackerScore(suppressScore);
        activeWars.put(war.id(), updated);

        resolveContestedTerritory(war).ifPresent(territory -> {
            World world = Bukkit.getWorld(territory.world());
            if (world != null && territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
                Location bannerLoc = new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());
                org.bukkit.block.Block block = bannerLoc.getBlock();
                originalContestedBanners.putIfAbsent(war.id(), block.getState());
                org.bukkit.block.data.BlockData before = block.getBlockData();
                if (before instanceof org.bukkit.block.data.Rotatable rotatable) {
                    org.bukkit.block.data.Rotatable gray = (org.bukkit.block.data.Rotatable) Material.GRAY_BANNER.createBlockData();
                    gray.setRotation(rotatable.getRotation());
                    block.setBlockData(gray);
                } else if (before instanceof org.bukkit.block.data.Directional directional) {
                    org.bukkit.block.data.Directional gray = (org.bukkit.block.data.Directional) Material.GRAY_WALL_BANNER.createBlockData();
                    gray.setFacing(directional.getFacing());
                    block.setBlockData(gray);
                } else {
                    block.setType(Material.GRAY_BANNER);
                }
                if (block.getState() instanceof org.bukkit.block.Banner bannerState) {
                    bannerState.getPersistentDataContainer().set(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING,
                            territory.isCapital() ? "CAPITAL" : "TERRITORY");
                    bannerState.getPersistentDataContainer().set(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING, war.defenderClanId().toString());
                    bannerState.update(true);
                }
                world.spawnParticle(Particle.ASH, bannerLoc.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.05);
                world.playSound(bannerLoc, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.8f);
            }
        });

        notifyBannerBroken(updated);
    }

    /** Puts the suppressed banner back; safe to call for any war, it does nothing when nothing was suppressed. */
    public void restoreBannerBlock(ClanWar war) {
        org.bukkit.block.BlockState original = originalContestedBanners.remove(war.id());
        if (original == null) return;
        original.update(true, false);
    }

    public void startBannerCapture(ClanWar war, UUID carrierId) {
        suppressBanner(war);
    }

    public void purgeClan(UUID clanId) {
        warCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
        plugin.getConflictCooldownStore().deleteClanAsync(clanId);
    }

    /**
     * Немедленно завершает все активные войны с участием этого клана. Нужно вызывать при
     * расформировании клана (добровольном или как следствие захвата знамени) - иначе война и
     * осадный режим на территории противника остаются висеть на клане, которого больше нет,
     * до истечения таймера войны. Должен вызываться до того, как клан будет удалён из
     * ClanManager (иначе getClanById для второй стороны войны ещё найдётся, а для этого клана -
     * уже нет).
     */
    public void endActiveWarsInvolvingClan(UUID clanId) {
        for (ClanWar war : activeWars()) {
            if (!war.involves(clanId)) {
                continue;
            }
            activeWars.remove(war.id());
            clearPendingPhase(war.id(), resolveWarClans(war).orElse(null));
            // Fire the same event and run the same cleanup/reward as every other war-end path
            // (endWarAsync, peaceAsync) so third-party listeners and the surviving clan see
            // consistent behaviour regardless of why the war ended.
            Bukkit.getPluginManager().callEvent(new ClanWarEndEvent(war.withState(WarState.FINISHED), WarResult.CANCELLED));

            UUID survivorClanId = war.attackerClanId().equals(clanId) ? war.defenderClanId() : war.attackerClanId();
            plugin.getClanManager().getClanById(survivorClanId).ifPresent(survivor -> {
                onlineMembers(survivor).forEach(player -> plugin.getMessages().send(player, "war.ended-by-disband"));
                // A war that never started (or a disband timed to dodge it) pays nothing.
                if (war.state() != WarState.ACTIVE) return;
                long reward = plugin.getConfig().getLong("leveling.war-win-exp", 1200L);
                plugin.getClanManager().addExperienceAsync(survivor, reward).exceptionally(t -> {
                    plugin.getLogger().warning("Failed to award war experience to clan " + survivor.id() + ": " + t.getMessage());
                    return null;
                });
            });

            announceWarEnd(war, WarResult.CANCELLED);
            restoreBannerBlock(war);
            confiscateWarItems(war);
            endSiege(war);
            resetBannerHits(war.id());
            rematchClaims.remove(war.id());
            lastControlAward.remove(war.id());
            plugin.getConflictParticipants().forget(war.id());
        }
    }

    /**
     * Сворачивает все активные войны при выключении плагина: снимает осадный режим с оспариваемых
     * территорий, восстанавливает сломанные знамёна и изымает боевые компасы.
     *
     * <p>Критично именно для осадного режима: LoveClaims хранит флаг {@code is_under_siege} в своей
     * БД, а войны нигде не персистятся. Без этой уборки территория остаётся помеченной как
     * осаждаемая навсегда - после рестарта войны уже нет, и снять флаг нечем.
     *
     * <p>Намеренно не вызывает {@link #endWarAsync}: на выключении не нужно назначать победителя,
     * начислять опыт и рассылать {@link ClanWarEndEvent} в уже отключающиеся плагины.
     */
    public void shutdown() {
        for (ClanWar war : activeWars()) {
            activeWars.remove(war.id());
            clearPendingPhase(war.id(), resolveWarClans(war).orElse(null));
            restoreBannerBlock(war);
            confiscateWarItems(war);
            endSiege(war);
            resetBannerHits(war.id());
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldownMillis = warCooldownDuration().toMillis();
        warCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

        for (ClanWar war : activeWars.values()) {
            if (war.state() == WarState.PREPARING) {
                tickPendingWar(war, now);
                continue;
            }

            if (war.isBannerSuppressed()) {
                resolveContestedTerritory(war).ifPresent(territory -> {
                    World world = Bukkit.getWorld(territory.world());
                    if (world != null && territory.bannerX() != null && territory.bannerY() != null && territory.bannerZ() != null) {
                        Location bannerLoc = new Location(world, territory.bannerX(), territory.bannerY(), territory.bannerZ());
                        Optional<WarClans> clansOpt = resolveWarClans(war);
                        if (clansOpt.isPresent()) {
                            boolean defenderNear = onlineMembers(clansOpt.get().defender())
                                    .anyMatch(p -> p.getWorld().equals(world) && p.getLocation().distanceSquared(bannerLoc) <= 9.0);
                            if (defenderNear) {
                                double repairSeconds = plugin.getConfig().getDouble("war.banner-repair-seconds", 60.0);
                                double delta = 100.0 / Math.max(1.0, repairSeconds);
                                double nextProgress = war.bannerRepairProgress() + delta;
                                world.spawnParticle(Particle.HAPPY_VILLAGER, bannerLoc.clone().add(0.5, 0.8, 0.5), 4, 0.2, 0.2, 0.2, 0.02);
                                onlineMembers(clansOpt.get().defender())
                                        .filter(p -> p.getWorld().equals(world) && p.getLocation().distanceSquared(bannerLoc) <= 9.0)
                                        .forEach(p -> p.sendActionBar(Component.text("§aПочинка знамени: " + (int) nextProgress + "%")));

                                if (nextProgress >= 100.0) {
                                    ClanWar restored = war.withBannerRestored();
                                    activeWars.put(war.id(), restored);
                                    restoreBannerBlock(restored);
                                    world.playSound(bannerLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                                    announceBannerRepaired(restored);
                                } else {
                                    activeWars.put(war.id(), war.withRepairProgress(nextProgress));
                                }
                            } else {
                                world.spawnParticle(Particle.SMOKE, bannerLoc.clone().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1, 0.01);
                            }
                        }
                    }
                });
            }

            tickTerritoryControl(war, now);
            updateActiveBar(war, now);
            spawnBannerEffects(war);

            if (war.endsAt() <= now) {
                WarResult result = war.attackerScore() > war.defenderScore()
                        ? WarResult.ATTACKER_WIN
                        : war.defenderScore() > war.attackerScore() ? WarResult.DEFENDER_WIN : WarResult.DRAW;
                endWarAsync(war.id(), result);
            }
        }
    }

    private void announceBannerRepaired(ClanWar war) {
        Optional<WarClans> clansOpt = resolveWarClans(war);
        if (clansOpt.isEmpty()) return;
        onlineMembers(clansOpt.get().defender()).forEach(p ->
                p.sendTitle("§a§lЗНАМЯ ВОССТАНОВЛЕНО!", "§7Знамя починено и снова готово к защите!", 10, 40, 10));
        onlineMembers(clansOpt.get().attacker()).forEach(p ->
                p.sendTitle("§c§lЗНАМЯ ВОССТАНОВЛЕНО!", "§7Защитники починили знамя!", 10, 40, 10));
    }

    private void tickTerritoryControl(ClanWar war, long now) {
        int score = plugin.getConfig().getInt("war.objectives.control-score", 2);
        int everySeconds = plugin.getConfig().getInt("war.objectives.control-tick-seconds", 20);
        int minMembers = plugin.getConfig().getInt("war.objectives.control-min-members", 2);
        if (score <= 0 || everySeconds <= 0) {
            return;
        }

        Optional<ClanTerritory> territoryOpt = resolveContestedTerritory(war);
        if (territoryOpt.isEmpty()) {
            return;
        }
        Optional<BoundingBox> boxOpt = plugin.getAdvancedClaimsHook().boundingBoxOf(territoryOpt.get());
        if (boxOpt.isEmpty()) {
            return;
        }
        BoundingBox box = boxOpt.get();

        Optional<WarClans> clansOpt = resolveWarClans(war);
        if (clansOpt.isEmpty()) {
            return;
        }
        String territoryWorld = territoryOpt.get().world();
        List<Player> attackerPresent = onlineMembers(clansOpt.get().attacker())
                .filter(p -> p.getWorld().getName().equals(territoryWorld) && box.contains(p.getLocation().toVector()))
                .toList();
        List<Player> defenderPresent = onlineMembers(clansOpt.get().defender())
                .filter(p -> p.getWorld().getName().equals(territoryWorld) && box.contains(p.getLocation().toVector()))
                .toList();
        long attackers = attackerPresent.size();
        long defenders = defenderPresent.size();
        attackerPresent.forEach(p -> me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, war.id(), p, war.attackerClanId()));
        defenderPresent.forEach(p -> me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, war.id(), p, war.defenderClanId()));

        Long last = lastControlAward.get(war.id());
        long elapsed = last == null ? everySeconds * 1000L : (now - last);
        long remainingSec = Math.max(0, (everySeconds * 1000L - elapsed) / 1000);

        World world = Bukkit.getWorld(territoryWorld);
        if (world != null) {
            String dominanceMsg;
            if (attackers > defenders && attackers >= minMembers) {
                dominanceMsg = "Доминирование: ATK " + attackers + " — " + defenders + " DEF  |  +" + score + " очков через " + remainingSec + "с";
            } else if (defenders > attackers && defenders >= minMembers) {
                dominanceMsg = "Доминирование: ATK " + attackers + " — " + defenders + " DEF  |  +" + score + " очков через " + remainingSec + "с";
            } else {
                dominanceMsg = "Оспариваемая земля: ATK " + attackers + " — " + defenders + " DEF";
            }
            for (Player p : java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender())).toList()) {
                if (p.getWorld().equals(world) && box.contains(p.getLocation().toVector())) {
                    p.sendActionBar(Component.text("§6" + dominanceMsg));
                }
            }
        }

        if (elapsed >= everySeconds * 1000L) {
            lastControlAward.put(war.id(), now);
            if (attackers > defenders && attackers >= minMembers) {
                addScore(war.attackerClanId(), war.defenderClanId(), score);
                onlineMembers(clansOpt.get().attacker()).forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f));
            } else if (defenders > attackers && defenders >= minMembers) {
                addScore(war.defenderClanId(), war.attackerClanId(), score);
                onlineMembers(clansOpt.get().defender()).forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f));
            }
        }
    }

    private void announceControl(Clan clan, int score) {
        onlineMembers(clan).forEach(player -> {
            plugin.getMessages().send(player, "war.objectives.control-held", Map.of("score", String.valueOf(score)));
            if (effectsEnabled()) {
                plugin.getMessages().playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            }
        });
    }

    private void tickPendingWar(ClanWar war, long now) {
        long remainingMs = war.endsAt() - now;
        if (remainingMs <= 0) {
            activateWar(war);
            return;
        }

        BossBar bar = pendingBossBars.get(war.id());
        Optional<WarClans> warClansOpt = resolveWarClans(war);
        if (bar == null || warClansOpt.isEmpty()) {
            return;
        }
        long totalMs = preStartDuration().toMillis();
        bar.progress(Math.max(0f, Math.min(1f, (float) remainingMs / (float) totalMs)));
        bar.name(plugin.getMessages().component("war.pending.bossbar", Map.of(
                "attacker", warClansOpt.get().attacker().tag(), "color1", warClansOpt.get().attacker().tagColor(),
                "defender", warClansOpt.get().defender().tag(), "color2", warClansOpt.get().defender().tagColor(),
                "time", formatDuration(remainingMs))));

        if (remainingMs <= 60_000L && oneMinuteWarned.add(war.id())) {
            java.util.stream.Stream.concat(onlineMembers(warClansOpt.get().attacker()), onlineMembers(warClansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "war.pending.one-minute-warning"));
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

        // Every online member gets one, not only the leader and guardians; later joiners get it in syncPlayer()
        onlineMembers(attackerClan).forEach(player -> giveTrackingCompass(player, war, bannerLocation, defenderClan));
        onlineMembers(defenderClan).forEach(player -> giveTrackingCompass(player, war, bannerLocation, attackerClan));
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

        // A lodestone compass keeps pointing at the banner without touching the player's ordinary compasses
        if (compass.getItemMeta() instanceof CompassMeta compassMeta) {
            compassMeta.setLodestone(targetLocation);
            compassMeta.setLodestoneTracked(false);
            compass.setItemMeta(compassMeta);
        }

        // Never overwrite an occupied slot (e.g. slot 0) - a full inventory drops the compass
        // at the player's feet instead of destroying whatever item was already there.
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(compass);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
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

    private record WarClans(Clan attacker, Clan defender) {}

    private Optional<WarClans> resolveWarClans(ClanWar war) {
        Optional<Clan> attackerOpt = plugin.getClanManager().getClanById(war.attackerClanId());
        Optional<Clan> defenderOpt = plugin.getClanManager().getClanById(war.defenderClanId());
        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WarClans(attackerOpt.get(), defenderOpt.get()));
    }

    /**
     * Кладёт завершённую войну в архив. Отменённые войны не записываются: они ничем
     * не закончились, и в истории противостояния им не место.
     */
    private static me.lovelace.loveclans.api.events.ConflictOutcome outcomeOf(WarResult result) {
        return switch (result) {
            case ATTACKER_WIN -> me.lovelace.loveclans.api.events.ConflictOutcome.ATTACKER_WIN;
            case DEFENDER_WIN -> me.lovelace.loveclans.api.events.ConflictOutcome.DEFENDER_WIN;
            default -> me.lovelace.loveclans.api.events.ConflictOutcome.DRAW;
        };
    }

    private void archiveWar(ClanWar war, WarResult result) {
        if (result == WarResult.CANCELLED) {
            return;
        }
        UUID winner = switch (result) {
            case ATTACKER_WIN -> war.attackerClanId();
            case DEFENDER_WIN -> war.defenderClanId();
            default -> null;
        };
        plugin.getConflictArchive().record(ConflictKind.WAR, war.attackerClanId(), war.defenderClanId(),
                winner, war.attackerScore(), war.defenderScore(), war.startedAt());
    }

    private void announceWarEnd(ClanWar war, WarResult result) {
        Optional<WarClans> warClansOpt = resolveWarClans(war);
        if (warClansOpt.isEmpty()) {
            return;
        }
        Clan attacker = warClansOpt.get().attacker();
        Clan defender = warClansOpt.get().defender();

        if (result == WarResult.DRAW || result == WarResult.CANCELLED) {
            String titleKey = result == WarResult.CANCELLED ? "war.end.cancelled-title" : "war.end.draw-title";
            String subtitleKey = result == WarResult.CANCELLED ? "war.end.cancelled-subtitle" : "war.end.draw-subtitle";
            onlineMembers(attacker).forEach(player -> {
                plugin.getMessages().sendTitle(player, titleKey, subtitleKey,
                        Map.of("tag", defender.tag(), "color", defender.tagColor()));
                plugin.getMessages().playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            });
            onlineMembers(defender).forEach(player -> {
                plugin.getMessages().sendTitle(player, titleKey, subtitleKey,
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

    private void notifyBannerBroken(ClanWar war) {
        Optional<WarClans> warClansOpt = resolveWarClans(war);
        if (warClansOpt.isEmpty()) {
            return;
        }
        Clan attacker = warClansOpt.get().attacker();
        Clan defender = warClansOpt.get().defender();

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

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().values().stream()
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull);
    }

    /**
     * Сообщает ядру о выигранной войне, если LoveCore установлен. Проверка присутствия
     * плагина обязательна: классы {@code lovecore-api} подключены только в scope provided —
     * без ядра на сервере их вообще нет на classpath.
     */
    private void reportWarWinToCore(UUID clanId) {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") == null) {
            return;
        }
        try {
            dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.stats.StatBus.class)
                    .ifPresent(bus -> bus.recordClan(clanId, dev.lovelace.lovecore.api.stats.Metrics.CLAN_WARS_WON, 1));
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось отчитаться перед LoveCore о выигранной войне: " + t.getMessage());
        }
    }

    public long getCooldownRemaining(UUID clan1, UUID clan2) {
        AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = getWarPairKey(clan1, clan2);
        Long last = warCooldowns.get(cooldownKey);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long duration = warCooldownDuration().toMillis();
        return elapsed < duration ? (duration - elapsed) : 0;
    }

    public int activeWarsCount() {
        return activeWars.size();
    }
}
