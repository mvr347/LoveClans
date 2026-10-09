package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanInvite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerConnectionListener implements Listener {
    private final LoveClansPlugin plugin;

    public PlayerConnectionListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getClanManager().updateLastSeen(player.getUniqueId(), System.currentTimeMillis());
        plugin.getClanManager().deliverPendingItems(player);
        // boss bars and the war compass for someone who logs in while their clan is at war
        plugin.getWarManager().syncPlayer(player);
        plugin.getRaidManager().syncPlayer(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            sendLoginSummary(player);
        }, 20L);
    }

    private void sendLoginSummary(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(playerId);

        List<String> events = new ArrayList<>();
        Component actionButton = null;

        if (clanOpt.isPresent()) {
            Clan clan = clanOpt.get();
            boolean canManageApps = clan.member(playerId)
                    .map(m -> m.rank() == me.lovelace.loveclans.model.ClanRank.LEADER || m.rank() == me.lovelace.loveclans.model.ClanRank.GUARDIAN)
                    .orElse(false);
            if (canManageApps) {
                int apps = plugin.getClanManager().getClanApplications(clan.id()).size();
                if (apps > 0) {
                    events.add(apps + " " + formatPlural(apps, "заявка", "заявки", "заявок"));
                }
            }

            plugin.getWarManager().getActiveWar(clan.id()).ifPresent(war -> {
                UUID enemyClanId = war.attackerClanId().equals(clan.id()) ? war.defenderClanId() : war.attackerClanId();
                String enemyTag = plugin.getClanManager().getClanById(enemyClanId).map(Clan::tag).orElse("???");
                events.add("война с [" + enemyTag + "]");
            });

            plugin.getSiegeManager().getActiveSiege(clan.id()).ifPresent(siege -> {
                UUID enemyClanId = siege.attackerClanId().equals(clan.id()) ? siege.defenderClanId() : siege.attackerClanId();
                String enemyTag = plugin.getClanManager().getClanById(enemyClanId).map(Clan::tag).orElse("???");
                events.add("осада с [" + enemyTag + "]");
            });

            plugin.getRaidManager().getActiveRaid(clan.id()).ifPresent(raid -> {
                UUID enemyClanId = raid.attackerClanId().equals(clan.id()) ? raid.defenderClanId() : raid.attackerClanId();
                String enemyTag = plugin.getClanManager().getClanById(enemyClanId).map(Clan::tag).orElse("???");
                events.add("набег от [" + enemyTag + "]");
            });

            if (clan.isChestTaxLocked()) {
                events.add("сундук заблокирован из-за недоимки");
            }

            if (!events.isEmpty()) {
                actionButton = Component.text(" ")
                        .append(plugin.getMessages().component("login-summary.menu-button", player)
                                .clickEvent(ClickEvent.runCommand("/clan"))
                                .hoverEvent(HoverEvent.showText(Component.text("Открыть меню клана"))));
            }
        } else {
            List<ClanInvite> invites = plugin.getClanManager().getPlayerInvites(playerId);
            if (!invites.isEmpty()) {
                int count = invites.size();
                events.add(count + " " + formatPlural(count, "приглашение в клан", "приглашения в клан", "приглашений в клан"));
                actionButton = Component.text(" ")
                        .append(plugin.getMessages().component("login-summary.open-button", player)
                                .clickEvent(ClickEvent.runCommand("/clan applications"))
                                .hoverEvent(HoverEvent.showText(Component.text("Открыть приглашения"))));
            }
        }

        if (events.isEmpty()) {
            return;
        }

        String eventsStr = String.join("<gray> · </gray><white>", events);
        Component titleComp = plugin.getMessages().component(
                "login-summary.title",
                Map.of("events", eventsStr),
                player
        );

        if (actionButton != null) {
            player.sendMessage(titleComp.append(actionButton));
        } else {
            player.sendMessage(titleComp);
        }
    }

    private static String formatPlural(int number, String one, String two, String five) {
        int n = Math.abs(number) % 100;
        int n1 = n % 10;
        if (n > 10 && n < 20) return five;
        if (n1 > 1 && n1 < 5) return two;
        if (n1 == 1) return one;
        return five;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        plugin.getClanManager().updateLastSeen(playerId, System.currentTimeMillis());

        plugin.getChatInputListener(playerId);

        plugin.getGuiManager().clearPlayerCache(playerId);
        plugin.getClanTradeSessionManager().handlePlayerQuit(player);

        if (plugin.getClanCommand() != null) {
            plugin.getClanCommand().clearDebounce(playerId);
        }

        // Otherwise the "/clan home" warmup's repeating BukkitTask keeps ticking against a
        // disconnected (stale) Player object indefinitely - it's only ever cancelled today by
        // the player moving or re-issuing the command, neither of which can happen once offline.
        if (plugin.getClanManager().hasPendingHomeTeleport(playerId)) {
            plugin.getClanManager().cancelHomeTeleport(playerId, null);
        }
    }
}