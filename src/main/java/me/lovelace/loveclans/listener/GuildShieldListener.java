package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class GuildShieldListener implements Listener {
    private final LoveClansPlugin plugin;

    public GuildShieldListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemFactory().isGuildShieldItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = clanOpt.get();

        // Same rules and effects as the capital-menu button; the item is only spent when the shield really went up.
        if (plugin.getGuildShieldService().activate(player, clan)) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
