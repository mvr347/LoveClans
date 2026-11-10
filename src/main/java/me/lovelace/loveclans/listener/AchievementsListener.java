package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanMemberJoinEvent;
import me.lovelace.loveclans.api.events.ClanWarEndEvent;
import me.lovelace.loveclans.integration.AchievementsHook;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.war.WarResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * Feeds the shared achievements (LoveEssential) with clan facts. The founder achievement is granted by
 * {@code ClanManager} right after the clan is indexed, not from {@code ClanCreateEvent}: that event fires before the
 * creation fee is charged and can still be followed by a failure.
 */
public final class AchievementsListener implements Listener {
    public static final String RECRUITER = "clan_recruiter";
    public static final String WAR_WON = "clan.war.won";

    private final LoveClansPlugin plugin;

    public AchievementsListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** The clan that won a war, if it was won (draws and cancelled wars have no winner). */
    static Optional<UUID> winnerClan(ClanWar war, WarResult result) {
        return switch (result) {
            case ATTACKER_WIN -> Optional.of(war.attackerClanId());
            case DEFENDER_WIN -> Optional.of(war.defenderClanId());
            case DRAW, CANCELLED -> Optional.empty();
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberJoin(ClanMemberJoinEvent event) {
        UUID leader = event.clan().leaderId().orElse(null);
        if (leader != null && !leader.equals(event.member().playerId())) {
            AchievementsHook.unlock(leader, RECRUITER);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWarEnd(ClanWarEndEvent event) {
        winnerClan(event.war(), event.result())
                .flatMap(id -> plugin.getClanManager().getClanById(id))
                .ifPresent(clan -> {
                    for (Player member : plugin.getWarManager().onlineMembers(clan).toList()) {
                        AchievementsHook.progress(member.getUniqueId(), WAR_WON, 1);
                    }
                });
    }
}
