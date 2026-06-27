package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public final class ClanRoleSettingsMenu {
    private final LoveClansPlugin plugin;

    public ClanRoleSettingsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.ROLE_SETTINGS, clan.id()), 27,
                plugin.getMessages().component("gui.role-settings.title", player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        ClanRank[] ranks = { ClanRank.RECRUIT, ClanRank.MEMBER, ClanRank.GUARDIAN };
        int[] slots = { 11, 13, 15 };
        for (int i = 0; i < ranks.length; i++) {
            ClanRank rank = ranks[i];
            String headTexture = switch (rank) {
                case RECRUIT -> ItemBuilder.HEAD_RANK_RECRUIT;
                case MEMBER -> ItemBuilder.HEAD_RANK_CLANSMAN;
                case GUARDIAN -> ItemBuilder.HEAD_RANK_GUARDIAN;
                default -> ItemBuilder.HEAD_SETTINGS;
            };
            ItemBuilder builder = ItemBuilder.head(headTexture)
                    .name(plugin.getMessages().component("gui.role-settings.rank.name", Map.of("rank", rank.displayName()), player))
                    .lore(plugin.getMessages().component("gui.role-settings.rank.lore", player));
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, rank.name()));
            inventory.setItem(slots[i], builder.build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, org.bukkit.inventory.ItemStack clickedItem) {
        if (slot == 22) {
            plugin.getGuiManager().openSettings(player, clan);
            return;
        }
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String rankName = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rankName == null) return;
        try {
            ClanRank rank = ClanRank.valueOf(rankName);
            plugin.getGuiManager().openRankPermissions(player, clan, rank);
        } catch (IllegalArgumentException ignored) {}
    }
}
