package me.lovelace.loveclans.integration;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * Pure-reflection bridge into LoveHunt's {@code BountyClaimEvent} (no compile-time dependency),
 * used to drive the "Обет Крови" clan contract: a CLAN-type bounty placed by this clan, claimed
 * by one of its own members, counts as one turn-in.
 *
 * <p>Note: LoveHunt's own reflection bridge back into clans ({@code ReflectionClansProvider})
 * currently looks up a plugin literally named "Clans" and methods that don't exist on this
 * LoveClans build, so clan-bounty *creation* on LoveHunt's side may not work until that's fixed
 * there — this only affects the turn-in side of the contract.
 */
public final class LoveHuntBountyBridge implements Listener {

    private final LoveClansPlugin plugin;

    public LoveHuntBountyBridge(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the raw reflective listener. Returns false (and logs) if LoveHunt isn't present. */
    @SuppressWarnings("unchecked")
    public boolean register() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LoveHunt")) {
            return false;
        }
        try {
            Class<? extends Event> eventClass =
                    (Class<? extends Event>) Class.forName("me.lovelace.loveHunt.api.event.BountyClaimEvent");
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR,
                    (listener, event) -> handle(event), plugin);
            plugin.getLogger().info("Клановые контракты: подключена интеграция с LoveHunt (Обет Крови).");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Не удалось подключить интеграцию с LoveHunt для клановых контрактов: "
                    + exception.getMessage());
            return false;
        }
    }

    private void handle(Event event) {
        try {
            Object bounty = event.getClass().getMethod("getBounty").invoke(event);
            Object killerObj = event.getClass().getMethod("getKiller").invoke(event);
            if (bounty == null || !(killerObj instanceof Player killer)) {
                return;
            }

            Object type = bounty.getClass().getMethod("type").invoke(bounty);
            if (type == null || !"CLAN".equals(type.toString())) {
                return;
            }

            Object clanTagObj = bounty.getClass().getMethod("clanTag").invoke(bounty);
            String bountyClanTag = clanTagObj == null ? null : clanTagObj.toString();
            if (bountyClanTag == null || bountyClanTag.isBlank()) {
                return;
            }

            plugin.getClanManager().getPlayerClan(killer.getUniqueId()).ifPresent(killerClan -> {
                if (bountyClanTag.equalsIgnoreCase(killerClan.tag())) {
                    recordTurnIn(killerClan, killer);
                }
            });
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Ошибка обработки события LoveHunt BountyClaimEvent: " + exception.getMessage());
        }
    }

    private void recordTurnIn(Clan clan, Player killer) {
        plugin.getContractManager().recordProgress(clan.id(), killer.getUniqueId(), Map.of("bounty_turn_in", true));
    }
}
