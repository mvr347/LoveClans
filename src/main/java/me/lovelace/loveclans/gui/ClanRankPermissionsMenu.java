package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanRankPermissionsMenu {
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanRank> openRanks = new ConcurrentHashMap<>();

    public ClanRankPermissionsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan, ClanRank rank) {
        openRanks.put(player.getUniqueId(), rank);

        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.RANK_PERMISSIONS, clan.id()), 27,
                plugin.getMessages().component("gui.rank-permissions.title", Map.of("rank", rank.displayName()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        ClanPermission[] permissions = ClanPermission.values();
        for (int i = 0; i < permissions.length && i < 9; i++) {
            ClanPermission permission = permissions[i];
            boolean enabled = clan.getPermission(rank, permission);
            // BUILD/INVITE/KICK получают собственные текстуры голов; остальные права
            // (захват территорий, улучшения, настройки, дипломатия) переиспользуют иконки главного меню.
            String headTexture = switch (permission) {
                case BUILD -> ItemBuilder.HEAD_PERMISSION_BUILD;
                case INVITE -> ItemBuilder.HEAD_PERMISSION_INVITE;
                case KICK -> ItemBuilder.HEAD_PERMISSION_KICK;
                case CLAIM -> ItemBuilder.HEAD_TERRITORIES;
                case UPGRADE -> ItemBuilder.HEAD_EXPERIENCE;
                case SETTINGS -> ItemBuilder.HEAD_MAIN_SETTINGS;
                case DIPLOMACY -> ItemBuilder.HEAD_DIPLOMACY;
            };
            ItemBuilder builder = ItemBuilder.head(headTexture)
                    .name(plugin.getMessages().component("gui.rank-permissions.permission." + permission.name().toLowerCase(), player))
                    .lore(plugin.getMessages().component(enabled ? "gui.rank-permissions.enabled" : "gui.rank-permissions.disabled", player));
            if (enabled) builder.glow(true);
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, permission.name()));
            inventory.setItem(10 + i, builder.build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, org.bukkit.inventory.ItemStack clickedItem) {
        if (slot == 22) {
            plugin.getGuiManager().openRoleSettings(player, clan);
            return;
        }
        ClanRank rank = openRanks.get(player.getUniqueId());
        if (rank == null) return;
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String permName = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (permName == null) return;
        try {
            ClanPermission permission = ClanPermission.valueOf(permName);
            boolean current = clan.getPermission(rank, permission);
            clan.setPermission(rank, permission, !current);
            plugin.getClanManager().updateClanAsync(clan)
                    .thenRun(() -> plugin.runSync(() -> open(player, clan, rank)));
        } catch (IllegalArgumentException ignored) {}
    }

    public void clearPlayer(UUID playerId) {
        openRanks.remove(playerId);
    }
}
