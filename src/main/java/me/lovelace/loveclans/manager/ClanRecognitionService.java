package me.lovelace.loveclans.manager;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.CoinFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paid, instant clan recognition (the Guildmaster NPC). The owner pays {@code clans.recognition.cost} and the
 * clan is recognized at once. Everything is decided on the main thread: the order is check -> charge -> mark ->
 * save, and if the save fails the coins are given back and the mark is undone, so a clan is never charged
 * without being recognized. A clan id in {@link #inProgress} blocks a second confirmation while the first one
 * is still being saved (double click, two clients).
 */
public final class ClanRecognitionService {

    /** 25 gold coins with the default 1 / 100 / 2 000 / 20 000 denominations - used only when the key is absent. */
    static final long DEFAULT_COST = 50_000L;

    private final LoveClansPlugin plugin;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    public ClanRecognitionService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public long cost() {
        return Math.max(0L, MoneyConfig.getScaled(plugin.getConfig(), "clans.recognition.cost", DEFAULT_COST));
    }

    public static boolean isOwner(Clan clan, UUID playerId) {
        return clan.leaderId().filter(playerId::equals).isPresent();
    }

    /** Main thread only. {@code expectedCost} is the price the player was shown on the confirmation screen. */
    public void recognize(Player player, long expectedCost) {
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = clanOpt.get();
        // State may have changed while the confirmation screen was open: re-check everything.
        if (!isOwner(clan, player.getUniqueId())) {
            plugin.getMessages().send(player, "recognition.owner-only");
            return;
        }
        if (clan.isRecognized()) {
            plugin.getMessages().send(player, "recognition.already");
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
                // charge() is the authority (has() can be stale): no recognition unless the coins were really taken
                if (!economy.get().has(player, expectedCost) || !economy.get().charge(player, expectedCost)) {
                    plugin.getMessages().send(player, "recognition.cannot-afford", Map.of("cost", CoinFormat.format(expectedCost)));
                    return;
                }
            }

            clan.setRecognized(true);
            clan.setUnpaidTaxSince(0L);
            released = true; // from here on the async callback releases the guard
            plugin.getClanManager().updateClanAsync(clan).whenComplete((saved, error) -> plugin.runSync(() -> {
                try {
                    if (error != null) {
                        clan.setRecognized(false);
                        plugin.getLogger().warning("Clan recognition could not be saved for clan " + clan.id() + ": " + error.getMessage());
                        if (economy.isPresent() && player.isOnline()) {
                            economy.get().give(player, expectedCost);
                            plugin.getMessages().send(player, "recognition.failed-refunded");
                        } else if (economy.isPresent()) {
                            // Coins live in the player's inventory, so there is nowhere to put them while offline.
                            plugin.getLogger().severe("Player " + player.getName() + " went offline before the failed recognition of clan "
                                    + clan.id() + " could be refunded: give back " + expectedCost + " coins manually.");
                        }
                        return;
                    }
                    for (UUID memberId : clan.members().keySet()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null) {
                            plugin.getMessages().send(member, "recognition.success", Map.of("tag", clan.tag(), "color", clan.tagColor()));
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
