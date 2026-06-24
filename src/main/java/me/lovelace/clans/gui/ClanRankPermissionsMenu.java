package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.ClanPermission;
import me.lovelace.clans.model.ClanRank;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class ClanRankPermissionsMenu extends ClanMenuHolder {
    private final ClansPlugin plugin;
    private final ClanRank rank;

    public ClanRankPermissionsMenu(ClansPlugin plugin, Clan clan, ClanRank rank) {
        super(ClanMenuType.RANK_PERMISSIONS, clan.id());
        this.plugin = plugin;
        this.rank = rank;
    }

    public ClanRank getRank() { // Added public getter for rank
        return rank;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.rank-permissions.title", Map.of("rank", plugin.getMessages().raw(rank.key())), player));

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        ClanPermission[] permissions = ClanPermission.values();

        for (int i = 0; i < Math.min(slots.length, permissions.length); i++) {
            ClanPermission permission = permissions[i];
            boolean hasPerm = clan.getPermission(rank, permission);
            
            Material material = hasPerm ? Material.LIME_DYE : Material.GRAY_DYE;
            String status = hasPerm ? "<green>Включено</green>" : "<red>Выключено</red>";

            inventory.setItem(slots[i], ItemBuilder.of(material)
                    .name(plugin.getMessages().component("gui.rank-permissions.item.name", Map.of("perm", plugin.getMessages().raw(permission.key())), player))
                    .lore(plugin.getMessages().component("gui.rank-permissions.item.status", Map.of("status", status), player))
                    .lore(plugin.getMessages().component("gui.rank-permissions.item.click", player))
                    .mutate(meta -> meta.getPersistentDataContainer().set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, permission.name()))
                    .build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }
}