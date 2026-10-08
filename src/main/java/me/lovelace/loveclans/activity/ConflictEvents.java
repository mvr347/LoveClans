package me.lovelace.loveclans.activity;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanConflictDeclaredEvent;
import me.lovelace.loveclans.api.events.ClanConflictResolvedEvent;
import me.lovelace.loveclans.api.events.ConflictOutcome;
import me.lovelace.loveclans.model.history.ConflictKind;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Single place where the war, raid and siege managers announce a conflict to the activity and history systems. */
public final class ConflictEvents {
    private ConflictEvents() {
    }

    public static void declared(ConflictKind kind, UUID conflictId, UUID attackerClanId, UUID defenderClanId) {
        Bukkit.getPluginManager().callEvent(new ClanConflictDeclaredEvent(kind, conflictId, attackerClanId, defenderClanId));
    }

    public static void resolved(LoveClansPlugin plugin, ConflictKind kind, UUID conflictId, UUID attackerClanId,
                                UUID defenderClanId, ConflictOutcome outcome) {
        Bukkit.getPluginManager().callEvent(new ClanConflictResolvedEvent(kind, conflictId, attackerClanId, defenderClanId,
                outcome, plugin.getConflictParticipants().drain(conflictId)));
    }

    /** Marks an online clan member as a participant, unless he is AFK (standing in a zone AFK must not earn anything). */
    public static void mark(LoveClansPlugin plugin, UUID conflictId, Player player, UUID clanId) {
        if (player == null || plugin.getAfkManager().isAfk(player.getUniqueId())) return;
        plugin.getConflictParticipants().mark(conflictId, player.getUniqueId(), clanId);
    }
}
