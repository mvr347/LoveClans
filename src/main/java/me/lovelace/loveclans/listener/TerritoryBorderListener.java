package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclaims.api.event.ClaimBorderCrossEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Replaces LoveClaims' generic enter/leave text on clan territories with a relation-aware action bar line.
 * The line is mandatory, so it is sent straight to the action bar and ignores the /lovenotify toggles;
 * nothing is shown on exit. Registered only when LoveClaims is present.
 */
public final class TerritoryBorderListener implements Listener {
    private final LoveClansPlugin plugin;

    public TerritoryBorderListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBorderCross(ClaimBorderCrossEvent event) {
        if (!event.isClanTerritory()) {
            return;
        }
        Optional<Clan> owner = findOwner(event.getClaimId());
        if (owner.isEmpty()) {
            return;
        }
        event.setHandled(true);
        if (!event.isEnter()) {
            return;
        }
        Player player = event.getPlayer();
        Clan ownerClan = owner.get();
        UUID viewerClanId = plugin.getClanManager().getPlayerClan(player.getUniqueId()).map(Clan::id).orElse(null);
        boolean atWar = viewerClanId != null && plugin.getWarManager().areAtWar(viewerClanId, ownerClan.id());
        TerritoryEnterText.Kind kind = TerritoryEnterText.choose(viewerClanId, ownerClan.id(), atWar);
        player.sendActionBar(plugin.getMessages().component(kind.langKey(),
                Map.of("tag", ownerClan.tag(), "color", ownerClan.tagColor()), player));
    }

    private Optional<Clan> findOwner(UUID claimId) {
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            for (ClanTerritory territory : clan.territories()) {
                if (claimId.equals(territory.advancedClaimId())) {
                    return Optional.of(clan);
                }
            }
        }
        return Optional.empty();
    }
}
