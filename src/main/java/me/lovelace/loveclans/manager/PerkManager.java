package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPerk;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

/**
 * Периодически применяет "всегда включённые" боевые эффекты клановых перков (§7) онлайн-игрокам:
 * постоянная Сила I для перка ВОЙНА и бонусные сердца в бою для перка ЗЕМЛЕДЕЛИЕ во время войны
 * клана. Остальные эффекты перков (добыча, урожай, урон по знамёнам) обрабатываются по событиям
 * в {@link me.lovelace.loveclans.listener.PerkEffectListener} и в местах, где эти события уже
 * существуют (см. ClanProtectionListener для бонуса к урону по знамёнам).
 */
public final class PerkManager {
    private static final String HARVESTER_HEARTS_KEY = "clans_perk_harvester_hearts";

    private final LoveClansPlugin plugin;
    private final NamespacedKey heartsKey;
    private BukkitTask task;

    public PerkManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.heartsKey = new NamespacedKey(plugin, HARVESTER_HEARTS_KEY);
    }

    public void start() {
        long periodTicks = 20L * Math.max(1, plugin.getConfig().getInt("perks.tick-seconds", 15));
        int durationTicks = (int) Math.min(Integer.MAX_VALUE, periodTicks * 2);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(durationTicks), periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick(int durationTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            Optional<ClanPerk> perk = clanOpt.flatMap(Clan::perk);

            boolean warrior = perk.map(p -> p == ClanPerk.WARRIOR).orElse(false);
            if (warrior) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 0, true, false, false));
            } else if (player.hasPotionEffect(PotionEffectType.STRENGTH)) {
                player.removePotionEffect(PotionEffectType.STRENGTH);
            }

            boolean harvesterAtWar = perk.map(p -> p == ClanPerk.HARVESTER).orElse(false)
                    && clanOpt.map(clan -> plugin.getWarManager().isAtWar(clan.id())).orElse(false);
            double bonusHealth = harvesterAtWar
                    ? plugin.getConfig().getInt("perks.harvester.pvp-bonus-hearts", 2) * 2.0
                    : 0.0;
            applyHeartsModifier(player, bonusHealth);
        }
    }

    private void applyHeartsModifier(Player player, double bonusHealth) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(heartsKey))
                .findFirst().orElse(null);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
        if (bonusHealth > 0) {
            attribute.addModifier(new AttributeModifier(heartsKey, bonusHealth, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
