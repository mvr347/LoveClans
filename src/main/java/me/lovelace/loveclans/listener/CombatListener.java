package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.war.WarState;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class CombatListener implements Listener {
    private final LoveClansPlugin plugin;

    public CombatListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = player(event.getDamager());
        Player victim = player(event.getEntity());
        if (attacker == null || victim == null) {
            return;
        }
        Optional<Clan> attackerClan = plugin.getClanManager().getPlayerClan(attacker.getUniqueId());
        Optional<Clan> victimClan = plugin.getClanManager().getPlayerClan(victim.getUniqueId());
        if (attackerClan.isEmpty() || victimClan.isEmpty()) {
            return;
        }
        if (attackerClan.get().id().equals(victimClan.get().id()) && !plugin.getConfig().getBoolean("clans.friendly-fire", false)) {
            event.setCancelled(true);
            return;
        }

        if (isActiveWarBetween(attackerClan.get().id(), victimClan.get().id())) {
            applyAggressivePlaystyleBonus(event, attacker.getUniqueId());
        }
    }

    private boolean isActiveWarBetween(UUID clanA, UUID clanB) {
        for (ClanWar war : plugin.getWarManager().activeWars()) {
            if (war.state() == WarState.ACTIVE && war.between(clanA, clanB)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Бонус к урону во время войны клана за агрессивный стиль игры атакующего
     * (LoveCore.BehaviorLevels, ставит LoveBehavior) — "военные действия" отдают агрессивным
     * игрокам больше, симметрично тому, как миролюбивым отдают больше в торговле.
     */
    private void applyAggressivePlaystyleBonus(EntityDamageByEntityEvent event, UUID attackerId) {
        if (!plugin.getConfig().getBoolean("war.aggressive-playstyle-bonus.enabled", true)) {
            return;
        }
        int threshold = plugin.getConfig().getInt("war.aggressive-playstyle-bonus.playstyle-threshold", 0);
        double bonusPercent = plugin.getConfig().getDouble("war.aggressive-playstyle-bonus.damage-percent", 0.10);
        dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.social.BehaviorLevels.class)
                .filter(levels -> levels.playstyleLevel(attackerId) <= threshold)
                .ifPresent(levels -> event.setDamage(event.getDamage() * (1.0 + bonusPercent)));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // Handle banner drop on death. Deliberately not gated on the victim still being in a
        // clan: they may have been kicked/left while carrying a captured banner, and the war's
        // capturedBannerBy() would otherwise keep pointing at them with no way to clear it.
        for (ClanWar war : plugin.getWarManager().activeWars()) {
            if (war.capturedBannerBy() != null && war.capturedBannerBy().equals(victim.getUniqueId())) {
                plugin.getWarManager().resetBannerCapture(war.id());
                // Remove only the captured banner tagged with this war, not any other banner
                // (e.g. a decorative one) the victim might be carrying.
                event.getDrops().removeIf(item -> plugin.getClanManager().getClanItemFactory().isCapturedBanner(item, war.id()));
                plugin.getMessages().send(victim, "war.banner-dropped");
                break;
            }
        }

        if (killer == null) {
            return;
        }
        Optional<Clan> killerClan = plugin.getClanManager().getPlayerClan(killer.getUniqueId());
        Optional<Clan> victimClan = plugin.getClanManager().getPlayerClan(victim.getUniqueId());
        if (killerClan.isEmpty() || victimClan.isEmpty()) {
            return;
        }
        plugin.getWarManager().addKillScore(killerClan.get().id(), victimClan.get().id());
    }

    private Player player(Entity entity) {
        return entity instanceof Player player ? player : null;
    }
}