package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class ClanInfoMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    private Inventory inventory;

    public ClanInfoMenu(LoveClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
    }

    public Clan clan() {
        return clan;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.info.title", Map.of("tag", clan.coloredTag(), "name", clan.name()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
        ItemBuilder info = ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.info.name", Map.of("tag", clan.coloredTag(), "name", clan.name()), player))
                .lore(plugin.getMessages().component("gui.info.level", Map.of("level", String.valueOf(clan.level())), player))
                .lore(plugin.getMessages().component("gui.info.members",
                        Map.of("current", String.valueOf(clan.members().size()),
                                "max", String.valueOf(plugin.getClanManager().maxMembers(clan))), player));

        clan.leaderId().ifPresent(leaderId -> {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            String leaderName = leader.getName() != null ? leader.getName() : leaderId.toString().substring(0, 8);
            info.lore(plugin.getMessages().component("gui.info.leader", Map.of("player", leaderName), player));
        });

        inventory.setItem(13, info.build());

        boolean isMember = plugin.getClanManager().getPlayerClan(player.getUniqueId())
                .map(c -> c.id().equals(clan.id())).orElse(false);

        if (!isMember) {
            if (clan.isOpen()) {
                inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_INVITE)
                        .name(plugin.getMessages().component("gui.info.apply.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply.lore", player))
                        .build());
            } else {
                inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                        .name(plugin.getMessages().component("gui.info.apply-closed.name", player))
                        .lore(plugin.getMessages().component("gui.info.apply-closed.lore", player))
                        .build());
            }
        }

        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
