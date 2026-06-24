package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanRitualStartEvent;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.model.ritual.ClanRitual;
import me.lovelace.loveclans.model.ritual.GuardianRitual;
import me.lovelace.loveclans.model.ritual.HarvestRitual;
import me.lovelace.loveclans.model.ritual.RitualType;
import me.lovelace.loveclans.model.ritual.WarDrumRitual;
import org.bukkit.Bukkit;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RitualManager {
    private final LoveClansPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ClanRitual> activeRituals = new ConcurrentHashMap<>();

    public RitualManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<ClanRitual> startRitualAsync(Clan clan, UUID actorId, RitualType type) {
        return plugin.supplySync(() -> {
            clan.member(actorId)
                    .filter(member -> member.rank().atLeast(ClanRank.GUARDIAN))
                    .orElseThrow(() -> new IllegalStateException("clan.rank-too-low"));
            long now = System.currentTimeMillis();
            long availableAt = cooldowns.getOrDefault(clan.id(), 0L);
            if (availableAt > now) {
                throw new IllegalStateException("ritual.cooldown");
            }
            long durationMillis = plugin.getConfig().getLong("mechanics.rituals.duration-seconds", 600L) * 1000L;
            ClanRitual ritual = create(type, clan.id(), now, now + durationMillis);
            ClanRitualStartEvent event = new ClanRitualStartEvent(clan, ritual, actorId);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("general.error");
            }
            activeRituals.put(clan.id(), ritual);
            long cooldownMillis = plugin.getConfig().getLong("mechanics.rituals.cooldown-hours", 24L) * 60L * 60L * 1000L;
            cooldowns.put(clan.id(), now + cooldownMillis);
            clan.setSpirit(clan.spirit().awakenUntil(ritual.endsAt()));
            applyRitualEffects(clan, ritual);
            return ritual;
        }).thenCompose(ritual -> plugin.getClanManager().addExperienceAsync(clan, type.experienceReward()).thenApply(ignored -> ritual));
    }

    public Optional<ClanRitual> activeRitual(UUID clanId) {
        ClanRitual ritual = activeRituals.get(clanId);
        if (ritual == null || !ritual.active(System.currentTimeMillis())) {
            activeRituals.remove(clanId);
            return Optional.empty();
        }
        return Optional.of(ritual);
    }

    public long cooldownRemaining(UUID clanId) {
        return Math.max(0L, cooldowns.getOrDefault(clanId, 0L) - System.currentTimeMillis());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        activeRituals.entrySet().removeIf(entry -> !entry.getValue().active(now));
    }

    public void purgeClan(UUID clanId) {
        cooldowns.remove(clanId);
        activeRituals.remove(clanId);
    }

    private ClanRitual create(RitualType type, UUID clanId, long startedAt, long endsAt) {
        return switch (type) {
            case HARVEST -> new HarvestRitual(clanId, startedAt, endsAt);
            case WAR_DRUM -> new WarDrumRitual(clanId, startedAt, endsAt);
            case GUARDIAN -> new GuardianRitual(clanId, startedAt, endsAt);
        };
    }

    private void applyRitualEffects(Clan clan, ClanRitual ritual) {
        int durationTicks = (int) ((ritual.endsAt() - ritual.startedAt()) / 50L);
        for (UUID playerId : clan.members().keySet()) {
            Optional.ofNullable(Bukkit.getPlayer(playerId)).ifPresent(player -> {
                switch (ritual.type()) {
                    case HARVEST -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 1, true, false, true));
                    case WAR_DRUM -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 0, true, false, true));
                    case GUARDIAN -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 0, true, false, true));
                }
                ClanSpirit spirit = clan.spirit();
                clan.setSpirit(spirit.addEnergy(25));
            });
        }
    }
}
