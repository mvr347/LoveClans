package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.GuildmasterMenu;
import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import me.lovelace.loveclans.integration.NpcBindings;
import me.lovelace.loveclans.integration.NpcService;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

/**
 * Right click on the Guildmaster NPC (NPC of the configured provider bound by key: {@code /loveclansadmin createnpc guildmaster}).
 * Without a clan, or in a clan without the vows permission and without anything to recognize, the player simply
 * gets the clan list ({@code /clans}); otherwise {@link GuildmasterMenu} with the buttons that apply to them.
 */
public final class GuildmasterListener implements Listener {

    private final LoveClansPlugin plugin;
    private final NpcService npcs;

    public GuildmasterListener(LoveClansPlugin plugin, NpcService npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(NpcClickEvent event) {
        // Cheapest checks first: NpcClickEvent is fired for every NPC of every provider.
        if (event.click() != NpcClick.RIGHT || !npcs.isBound(event.npc(), NpcBindings.Slot.GUILDMASTER)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        Optional<Clan> clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clan.isPresent() && GuildmasterMenu.hasMenu(clan.get(), player.getUniqueId())) {
            plugin.getGuiManager().openGuildmaster(player, clan.get());
        } else {
            plugin.getGuiManager().openClanList(player);
        }
    }
}
