package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.war.ClanWar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Peace between two clans that are at war, besieged or raided: needs the DIPLOMACY right and the consent of the
 * other side. The first proposal waits for the answer; when the other clan proposes back, the conflict ends in a
 * draw (war) or is cancelled (siege, raid). While a war banner is captured nobody can ask for peace - the
 * capitulation countdown is the attacker's win and one side must not take it away alone. Main thread only.
 */
public final class PeaceService {
    private static final long OFFER_TTL_MILLIS = 5 * 60_000L;

    private final LoveClansPlugin plugin;
    private final PeaceOffers offers = new PeaceOffers(OFFER_TTL_MILLIS);

    public PeaceService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void forget(UUID clanId) {
        offers.clear(clanId);
    }

    public void proposeOrAccept(Player player, Clan source, Clan target) {
        if (!source.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        boolean atWar = plugin.getWarManager().areAtWar(source.id(), target.id());
        boolean inSiege = !atWar && plugin.getSiegeManager().areInSiege(source.id(), target.id());
        boolean inRaid = !atWar && !inSiege && plugin.getRaidManager().areInRaid(source.id(), target.id());
        if (!atWar && !inSiege && !inRaid) {
            plugin.sendOperationError(player, new IllegalStateException("war.not-at-war"));
            return;
        }
        if (atWar) {
            Optional<ClanWar> war = plugin.getWarManager().activeWar(source.id(), target.id());
            if (war.isPresent() && war.get().capturedBannerBy() != null) {
                plugin.getMessages().send(player, "war.peace-blocked-capture");
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (!offers.offerOrAccept(source.id(), target.id(), now)) {
            plugin.getMessages().send(player, "war.peace-offer-sent", Map.of("tag", target.tag(), "color", target.tagColor()));
            for (Player recipient : plugin.getClanManager().getOnlineMembersWithPermission(target, ClanPermission.DIPLOMACY)) {
                plugin.getMessages().send(recipient, "war.peace-offered", Map.of("tag", source.tag(), "color", source.tagColor()));
            }
            return;
        }
        var future = atWar ? plugin.getWarManager().peaceAsync(source, target)
                : inSiege ? plugin.getSiegeManager().peaceAsync(source, target)
                : plugin.getRaidManager().peaceAsync(source, target);
        future.exceptionally(throwable -> {
            plugin.runSync(() -> plugin.sendOperationError(player, throwable));
            return null;
        });
    }
}
