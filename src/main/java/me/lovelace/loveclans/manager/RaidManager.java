package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.raid.ClanRaid;
import me.lovelace.loveclans.model.raid.RaidResult;
import me.lovelace.loveclans.model.raid.RaidState;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Третий тип клановых конфликтов (§3.1): в отличие от войны/осады, набег не трогает мир (нет
 * баннеров/лагерей) - атакующие просто вскрывают сундук защитника (у которого мало людей онлайн)
 * на ограниченное время и выносят часть денег/предметов. Тот же PREPARING->ACTIVE->FINISHED
 * паттерн, что у {@link WarManager}/{@link SiegeManager}, но без физического мира.
 */
public final class RaidManager {
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanRaid> activeRaids = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> raidCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> pendingBossBars = new ConcurrentHashMap<>();
    private final Set<UUID> oneMinuteWarned = ConcurrentHashMap.newKeySet();

    public RaidManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private Duration preStartDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("raid.pre-start-minutes", 5));
    }

    private Duration raidDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("raid.duration-minutes", 10));
    }

    private Duration cooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("raid.cooldown-hours", 24));
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID clan1, UUID clan2) {
        return clan1.compareTo(clan2) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(clan1, clan2)
                : new AbstractMap.SimpleImmutableEntry<>(clan2, clan1);
    }

    private int countOnline(Clan clan) {
        int online = 0;
        for (UUID memberId : clan.members().keySet()) {
            if (Bukkit.getPlayer(memberId) != null) online++;
        }
        return online;
    }

    /** §8.3: a clan can't be raided if its influence is at/above (server average influence) * raid-immunity-multiplier. */
    public boolean isImmuneToRaid(Clan clan) {
        Collection<Clan> all = plugin.getClanManager().getAllClans();
        if (all.isEmpty()) return false;
        double average = all.stream().mapToLong(Clan::influence).average().orElse(0);
        double multiplier = plugin.getConfig().getDouble("influence.raid-immunity-multiplier", 2.0);
        return clan.influence() >= average * multiplier;
    }

    public CompletableFuture<ClanRaid> startRaidAsync(Clan attacker, Clan defender) {
        return plugin.supplySync(() -> {
            if (isInRaid(attacker.id()) || isInRaid(defender.id())) {
                throw new IllegalStateException("raid.already-in-raid");
            }
            if (plugin.getClanManager().inAnyConflict(attacker.id()) || plugin.getClanManager().inAnyConflict(defender.id())) {
                throw new IllegalStateException("raid.conflict-in-progress");
            }
            if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                throw new IllegalStateException("war.cannot-declare-on-ally");
            }
            if (isImmuneToRaid(defender)) {
                throw new IllegalStateException("raid.target-immune");
            }
            int maxDefenderOnline = plugin.getConfig().getInt("raid.max-defender-online", 2);
            if (countOnline(defender) > maxDefenderOnline) {
                throw new IllegalStateException("raid.too-many-defenders");
            }
            if (countOnline(attacker) < plugin.getConfig().getInt("raid.min-attacker-online", 1)) {
                throw new IllegalStateException("raid.not-enough-attackers");
            }

            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(attacker.id(), defender.id());
            long now = System.currentTimeMillis();
            Long lastRaidTime = raidCooldowns.get(cooldownKey);
            if (lastRaidTime != null && (now - lastRaidTime < cooldownDuration().toMillis())) {
                long remainingSeconds = (cooldownDuration().toMillis() - (now - lastRaidTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            ClanRaid raid = new ClanRaid(UUID.randomUUID(), attacker.id(), defender.id(), now,
                    now + preStartDuration().toMillis(), RaidState.PREPARING);
            activeRaids.put(raid.id(), raid);
            raidCooldowns.put(cooldownKey, now);

            beginPendingPhase(raid, attacker, defender);
            return raid;
        });
    }

    private record RaidClans(Clan attacker, Clan defender) {}

    private Optional<RaidClans> resolveClans(ClanRaid raid) {
        Optional<Clan> attacker = plugin.getClanManager().getClanById(raid.attackerClanId());
        Optional<Clan> defender = plugin.getClanManager().getClanById(raid.defenderClanId());
        if (attacker.isEmpty() || defender.isEmpty()) return Optional.empty();
        return Optional.of(new RaidClans(attacker.get(), defender.get()));
    }

    private void beginPendingPhase(ClanRaid raid, Clan attacker, Clan defender) {
        String time = formatDuration(raid.endsAt() - System.currentTimeMillis());
        Component title = plugin.getMessages().component("raid.pending.bossbar", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(), "time", time));
        BossBar bar = BossBar.bossBar(title, 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        pendingBossBars.put(raid.id(), bar);

        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "raid.pending.declared",
                Map.of("tag", defender.tag(), "color", defender.tagColor(), "time", time)));
        onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "raid.pending.declared",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor(), "time", time)));
    }

    private void clearPendingPhase(UUID raidId, RaidClans clans) {
        BossBar bar = pendingBossBars.remove(raidId);
        oneMinuteWarned.remove(raidId);
        if (bar != null && clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> p.hideBossBar(bar));
        }
    }

    private void activateRaid(ClanRaid raid) {
        Optional<RaidClans> clansOpt = resolveClans(raid);
        clearPendingPhase(raid.id(), clansOpt.orElse(null));
        if (clansOpt.isEmpty()) {
            activeRaids.remove(raid.id());
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        long now = System.currentTimeMillis();
        long moneyCap = Math.round(defender.chestMoney() * (plugin.getConfig().getInt("raid.loot-money-percent", 50) / 100.0));
        int unlockedSlots = defender.chestRows() * 9;
        int itemSlotCap = (int) Math.round(unlockedSlots * (plugin.getConfig().getInt("raid.loot-item-slot-percent", 50) / 100.0));

        ClanRaid activated = raid.activate(now + raidDuration().toMillis(), moneyCap, itemSlotCap);
        activeRaids.put(activated.id(), activated);

        onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "raid.start.attacker-title", "raid.start.attacker-subtitle",
                Map.of("tag", defender.tag(), "color", defender.tagColor())));
        onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "raid.start.defender-title", "raid.start.defender-subtitle",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor())));
    }

    // --- Looting ---

    public Optional<ClanRaid> findRaid(UUID first, UUID second) {
        return activeRaids.values().stream().filter(r -> r.between(first, second)).findFirst();
    }

    /** The raid (any phase) where {@code attackerClanId} is the attacking side, if any - used by /clan raid loot|items which only know their own clan. */
    public Optional<ClanRaid> findActiveRaidAsAttacker(UUID attackerClanId) {
        return activeRaids.values().stream()
                .filter(r -> r.attackerClanId().equals(attackerClanId))
                .findFirst();
    }

    private record MoneyLootResult(Clan defender, long amountTaken) {}

    /** Loots up to {@code amount} (capped by what's left of the raid's money allowance and the defender's balance) into the player's inventory. */
    public CompletableFuture<Long> lootMoneyAsync(ClanRaid raid, Player looter, long amount) {
        if (raid == null || looter == null || amount <= 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Raid, looter and amount must be valid."));
        return plugin.supplySync(() -> {
            ClanRaid current = activeRaids.get(raid.id());
            if (current == null || current.state() != RaidState.ACTIVE) {
                throw new IllegalStateException("raid.not-active");
            }
            Clan defender = plugin.getClanManager().getClanById(current.defenderClanId())
                    .orElseThrow(() -> new IllegalStateException("clan.not-found"));
            long take = Math.min(amount, Math.min(current.moneyRemaining(), defender.chestMoney()));
            if (take <= 0) {
                throw new IllegalStateException("raid.nothing-left");
            }
            defender.addChestMoney(-take);
            plugin.getItemsAdderEconomyService().give(looter, plugin.getClanManager().chestCurrencyItem(), take);
            activeRaids.put(current.id(), current.withMoneyLooted(take));
            return new MoneyLootResult(defender, take);
        }).thenCompose(result -> plugin.getStorage().updateClanChestMoney(result.defender().id(), result.defender().chestMoney())
                .thenApply(v -> result.amountTaken()));
    }

    /** Called by RaidLootMenu when a previously-full item slot becomes empty during an active raid loot session. */
    public void recordItemSlotLooted(UUID raidId) {
        ClanRaid current = activeRaids.get(raidId);
        if (current != null && current.state() == RaidState.ACTIVE) {
            activeRaids.put(raidId, current.withItemSlotsLooted(1));
        }
    }

    public Optional<ClanRaid> getRaid(UUID raidId) {
        return Optional.ofNullable(activeRaids.get(raidId));
    }

    // --- Lifecycle queries ---

    public boolean isInRaid(UUID clanId) {
        return activeRaids.values().stream().anyMatch(r -> r.involves(clanId));
    }

    public boolean areInRaid(UUID first, UUID second) {
        return activeRaids.values().stream().anyMatch(r -> r.between(first, second));
    }

    public Collection<ClanRaid> activeRaids() {
        return List.copyOf(activeRaids.values());
    }

    public CompletableFuture<Void> peaceAsync(Clan source, Clan target) {
        return plugin.supplySync(() -> {
            ClanRaid raid = findRaid(source.id(), target.id()).orElseThrow(() -> new IllegalStateException("war.not-at-war"));
            endRaid(raid, RaidResult.CANCELLED);
            onlineMembers(source).forEach(p -> plugin.getMessages().send(p, "war.peace", Map.of("tag", target.tag(), "color", target.tagColor())));
            onlineMembers(target).forEach(p -> plugin.getMessages().send(p, "war.peace", Map.of("tag", source.tag(), "color", source.tagColor())));
            return null;
        });
    }

    private void endRaid(ClanRaid raid, RaidResult result) {
        activeRaids.remove(raid.id());
        Optional<RaidClans> clansOpt = resolveClans(raid);
        clearPendingPhase(raid.id(), clansOpt.orElse(null));
        if (clansOpt.isEmpty() || result == RaidResult.CANCELLED) {
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        if (result == RaidResult.ATTACKER_WIN) {
            onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.victory-title", "raid.end.victory-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor())));
            onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.defeat-title", "raid.end.defeat-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor())));

            double multiplier = plugin.getConfig().getDouble("raid.win-exp-multiplier", 0.5);
            long reward = Math.round(plugin.getConfig().getLong("leveling.war-win-exp", 1200L) * multiplier);
            plugin.getClanManager().addExperienceAsync(attacker, reward).exceptionally(t -> {
                plugin.getLogger().warning("Failed to award raid experience to clan " + attacker.id() + ": " + t.getMessage());
                return null;
            });
            grantBonusItem(attacker);
        } else {
            onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.repelled-title", "raid.end.repelled-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor())));
            onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.failed-title", "raid.end.failed-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor())));
        }

        plugin.getClanManager().recordRaidResultAsync(attacker, result == RaidResult.ATTACKER_WIN).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record raid result for clan " + attacker.id() + ": " + t.getMessage());
            return null;
        });
        plugin.getClanManager().recordRaidResultAsync(defender, result != RaidResult.ATTACKER_WIN).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record raid result for clan " + defender.id() + ": " + t.getMessage());
            return null;
        });
    }

    private void grantBonusItem(Clan attacker) {
        List<String> bonusItems = plugin.getConfig().getStringList("raid.bonus-items");
        if (bonusItems.isEmpty()) return;
        Material material = Material.matchMaterial(bonusItems.get(ThreadLocalRandom.current().nextInt(bonusItems.size())));
        if (material == null) return;
        onlineMembers(attacker).findFirst().ifPresent(player -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material));
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            plugin.getMessages().send(player, "raid.bonus-item", Map.of("item", material.name()));
        });
    }

    public void purgeClan(UUID clanId) {
        raidCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
    }

    public void endActiveRaidsInvolvingClan(UUID clanId) {
        for (ClanRaid raid : activeRaids()) {
            if (raid.involves(clanId)) {
                endRaid(raid, RaidResult.CANCELLED);
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownDuration().toMillis();
        raidCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

        for (ClanRaid raid : activeRaids.values()) {
            if (raid.state() == RaidState.PREPARING) {
                tickPending(raid, now);
                continue;
            }
            if (raid.endsAt() <= now) {
                endRaid(raid, raid.anyLooted() ? RaidResult.ATTACKER_WIN : RaidResult.DEFENDER_WIN);
            }
        }
    }

    private void tickPending(ClanRaid raid, long now) {
        long remainingMs = raid.endsAt() - now;
        if (remainingMs <= 0) {
            activateRaid(raid);
            return;
        }
        BossBar bar = pendingBossBars.get(raid.id());
        Optional<RaidClans> clansOpt = resolveClans(raid);
        if (bar == null || clansOpt.isEmpty()) return;

        long totalMs = preStartDuration().toMillis();
        bar.progress(Math.max(0f, Math.min(1f, (float) remainingMs / (float) totalMs)));
        bar.name(plugin.getMessages().component("raid.pending.bossbar", Map.of(
                "attacker", clansOpt.get().attacker().tag(), "color1", clansOpt.get().attacker().tagColor(),
                "defender", clansOpt.get().defender().tag(), "color2", clansOpt.get().defender().tagColor(),
                "time", formatDuration(remainingMs))));

        if (remainingMs <= 60_000L && oneMinuteWarned.add(raid.id())) {
            java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "raid.pending.one-minute-warning"));
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
