package me.lovelace.loveclans.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.util.CoinFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A clan without a territory (the banner was lost after the territory was collapsed) buys a new banner from the
 * banner NPC for part of the price, and pays with spirit levels and some experience. Main thread only. Order:
 * check -> charge -> apply penalty -> save -> hand over the banner; if the save fails the coins are returned and
 * the clan is restored from a snapshot, so a clan is never charged without getting the banner. A clan id in
 * {@link #inProgress} blocks a second confirmation while the first one is being saved.
 */
public final class ClanBannerReplacementService {
    static final long DEFAULT_FULL_COST = 10_000L;

    private final LoveClansPlugin plugin;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    public ClanBannerReplacementService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public long cost() {
        long full = Math.max(0L, MoneyConfig.getScaled(plugin.getConfig(), "clans.banner.cost", DEFAULT_FULL_COST));
        return BannerReplacementPenalty.replacementCost(full, plugin.getConfig().getInt("clans.banner.replacement-cost-percent", 50));
    }

    public int spiritLevelsLost() {
        return Math.max(0, plugin.getConfig().getInt("clans.banner.replacement-spirit-levels", 1));
    }

    public int experiencePercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("clans.banner.replacement-exp-percent", 10)));
    }

    /** The clan the player may buy a replacement for, or empty (leader of a clan that has no territory). */
    public Optional<Clan> eligibleClan(Player player) {
        return plugin.getClanManager().getPlayerClan(player.getUniqueId())
                .filter(clan -> clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.LEADER).orElse(false))
                .filter(clan -> !clan.hasCapital());
    }

    public void purchase(Player player, long expectedCost) {
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = clanOpt.get();
        // The state may have changed while the confirmation screen was open: check everything again.
        if (clan.member(player.getUniqueId()).map(m -> m.rank() != ClanRank.LEADER).orElse(true)) {
            plugin.getMessages().send(player, "clan.banner.replacement-leader-only");
            return;
        }
        if (clan.hasCapital()) {
            plugin.getMessages().send(player, "clan.banner.replacement-not-needed");
            return;
        }
        if (plugin.getClanManager().getClanItemFactory().hasExistingBanner(player, "CAPITAL", clan.id())) {
            plugin.getMessages().send(player, "clan.banner.replacement-has-banner");
            return;
        }
        if (!inProgress.add(clan.id())) {
            return;
        }
        boolean released = false;
        try {
            Optional<LoveEconomy> economy = expectedCost > 0 ? LoveCore.service(LoveEconomy.class) : Optional.empty();
            if (expectedCost > 0) {
                if (economy.isEmpty()) {
                    plugin.getMessages().send(player, "clan.creation-economy-unavailable");
                    return;
                }
                // charge() is the authority (has() can be stale): no banner unless the coins were really taken
                if (!economy.get().has(player, expectedCost) || !economy.get().charge(player, expectedCost)) {
                    plugin.getMessages().send(player, "clan.banner.cannot-afford", Map.of("cost", CoinFormat.format(expectedCost)));
                    return;
                }
            }

            ClanSpirit spiritBefore = clan.spirit();
            long experienceBefore = clan.experience();
            int spiritAfter = BannerReplacementPenalty.spiritLevel(spiritBefore.level(), spiritLevelsLost());
            long energyAfter = BannerReplacementPenalty.spiritEnergy(spiritBefore.energy(), spiritAfter,
                    plugin.getSpiritManager().getExpForNextLevel(spiritAfter));
            long expLoss = BannerReplacementPenalty.experienceLoss(experienceBefore,
                    plugin.getClanManager().experienceForLevel(clan.level()), experiencePercent());
            clan.setSpirit(spiritBefore.withLevel(spiritAfter).addEnergy(energyAfter - spiritBefore.energy()));
            clan.removeExperience(expLoss);

            released = true; // from here on the async callback releases the guard
            plugin.getClanManager().updateClanAsync(clan).whenComplete((saved, error) -> plugin.runSync(() -> {
                try {
                    if (error != null) {
                        clan.setSpirit(spiritBefore);
                        clan.setExperience(experienceBefore);
                        plugin.getLogger().warning("Replacement banner could not be saved for clan " + clan.id() + ": " + error.getMessage());
                        if (economy.isPresent() && player.isOnline()) {
                            economy.get().give(player, expectedCost);
                            plugin.getMessages().send(player, "clan.banner.replacement-failed-refunded");
                        } else if (economy.isPresent()) {
                            plugin.getLogger().severe("Player " + player.getName() + " went offline before the failed banner replacement of clan "
                                    + clan.id() + " could be refunded: give back " + expectedCost + " coins manually.");
                        }
                        return;
                    }
                    if (player.isOnline()) {
                        ItemStack banner = plugin.getClanManager().getClanItemFactory().createCapitalBanner(clan.id(), clan.name());
                        if (!player.getInventory().addItem(banner).isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), banner);
                        }
                        plugin.getMessages().send(player, "clan.banner.replacement-bought", Map.of("cost", CoinFormat.format(expectedCost)));
                    } else {
                        plugin.getLogger().severe("Player " + player.getName() + " went offline after paying for the replacement banner of clan "
                                + clan.id() + ": give the banner back manually.");
                    }
                    for (UUID memberId : clan.members().keySet()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null) {
                            plugin.getMessages().send(member, "clan.banner.replacement-penalty",
                                    Map.of("spirit", String.valueOf(spiritBefore.level() - spiritAfter), "exp", String.valueOf(expLoss)));
                        }
                    }
                } finally {
                    inProgress.remove(clan.id());
                }
            }));
        } finally {
            if (!released) {
                inProgress.remove(clan.id());
            }
        }
    }
}
