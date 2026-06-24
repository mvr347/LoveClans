package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;

public final class ClanInfoMenu implements InventoryHolder {

    private final ClansPlugin plugin;
    private final Player player;
    private final Clan clan;

    public ClanInfoMenu(ClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
    }

    public Clan clan() {
        return clan;
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.clan-info.title", Map.of("tag", clan.tagColor() + clan.tag()), player));

        // Header
        inventory.setItem(4, ItemBuilder.of(clan.emblem())
                .name(plugin.getMessages().component("gui.clan-info.header.name",
                        Map.of("tag", clan.tagColor() + clan.tag(), "name", clan.name()), player))
                .lore(plugin.getMessages().component("gui.clan-info.header.level",
                        Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.clan-info.header.members",
                        Map.of("current", String.valueOf(clan.members().size()),
                               "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player))
                .lore(clan.isOpen()
                        ? plugin.getMessages().component("gui.clan-info.header.open", player)
                        : plugin.getMessages().component("gui.clan-info.header.closed", player))
                .build());

        // Leader
        clan.leaderId().ifPresent(leaderId -> {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            String leaderName = leader.getName() != null ? leader.getName() : leaderId.toString().substring(0, 8);
            inventory.setItem(13, ItemBuilder.of(Material.PLAYER_HEAD)
                    .mutate(meta -> {
                        if (meta instanceof SkullMeta skullMeta) {
                            skullMeta.setOwningPlayer(leader);
                        }
                    })
                    .name(plugin.getMessages().component("gui.clan-info.leader.name", Map.of("player", leaderName), player))
                    .lore(leader.isOnline()
                            ? plugin.getMessages().component("gui.clan-info.leader.lore_online", player)
                            : plugin.getMessages().component("gui.clan-info.leader.lore_offline", player))
                    .build());
        });

        // Apply button
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isEmpty()) {
            if (clan.isOpen()) {
                inventory.setItem(22, ItemBuilder.of(Material.GREEN_WOOL)
                        .name(plugin.getMessages().component("gui.clan-info.apply", player))
                        .build());
            } else {
                inventory.setItem(22, ItemBuilder.of(Material.RED_WOOL)
                        .name(plugin.getMessages().component("gui.clan-list.clan-item.apply-closed", player))
                        .build());
            }
        }

        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return null; // This is a one-time menu
    }
}
