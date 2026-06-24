package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.ClanMember;
import me.lovelace.clans.model.ClanRank;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public final class ClanMemberDetailMenu {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    private final ClansPlugin plugin;

    public ClanMemberDetailMenu(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Clan clan, UUID targetId) {
        ClanMember target = clan.member(targetId).orElse(null);
        if (target == null) return;

        boolean isGuildmaster = clan.member(viewer.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER).orElse(false);
        boolean isSelf = viewer.getUniqueId().equals(targetId);

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() != null ? offline.getName() : targetId.toString().substring(0, 8);

        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.MEMBER_DETAIL, clan.id()), 27,
                plugin.getMessages().component("gui.member-detail.title", Map.of("player", name), viewer));

        fillGlass(inventory);

        // Left: player info
        inventory.setItem(10, ItemBuilder.of(Material.PLAYER_HEAD)
                .name(plugin.getMessages().component("gui.member-detail.info.name", Map.of("player", name), viewer))
                .lore(plugin.getMessages().component("gui.member-detail.info.rank",
                        Map.of("rank", plugin.getMessages().raw(target.rank().key())), viewer))
                .lore(plugin.getMessages().component("gui.member-detail.info.joined",
                        Map.of("date", dateFormat.format(new Date(target.joinedAt()))), viewer))
                .lore(plugin.getMessages().component("gui.member-detail.info.seen",
                        Map.of("date", dateFormat.format(new Date(target.lastSeen()))), viewer))
                .mutate(meta -> {
                    if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(offline);
                    meta.getPersistentDataContainer().set(
                            plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString());
                })
                .build());

        // Right: actions (only for guildmaster, not for self, not for other guildmasters)
        boolean canManage = isGuildmaster && !isSelf && target.rank() != ClanRank.LEADER;

        if (canManage) {
            // Promote
            if (target.rank().nextRank() != null) { // Only show if there's a next rank
                inventory.setItem(14, ItemBuilder.head(ItemBuilder.HEAD_EXPAND)
                        .name(plugin.getMessages().component("gui.member-detail.promote.name", viewer))
                        .lore(plugin.getMessages().component("gui.member-detail.promote.lore", viewer))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()))
                        .build());
            }
            // Demote
            if (target.rank().previousRank() != null) { // Only show if there's a previous rank
                inventory.setItem(15, ItemBuilder.head(ItemBuilder.HEAD_DELETE_NO)
                        .name(plugin.getMessages().component("gui.member-detail.demote.name", viewer))
                        .lore(plugin.getMessages().component("gui.member-detail.demote.lore", viewer))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()))
                        .build());
            }
            // Kick
            inventory.setItem(16, ItemBuilder.head(ItemBuilder.HEAD_DELETE_YES)
                    .name(plugin.getMessages().component("gui.member-detail.kick.name", viewer))
                    .lore(plugin.getMessages().component("gui.member-detail.kick.lore", viewer))
                    .mutate(meta -> meta.getPersistentDataContainer().set(
                            plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()))
                    .build());
            // Transfer leadership
            inventory.setItem(12, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2E4YzA0NjM4NDY3MTg1NDczMmIyNTBlZmY2OGJmM2NhN2U0NzlmNDdlZTYwZmViNDY5ZGM4ZmFjZGU2NDQ2NCJ9fX0=")
                    .name(plugin.getMessages().component("gui.member-detail.transfer.name", viewer))
                    .lore(plugin.getMessages().component("gui.member-detail.transfer.lore", viewer))
                    .mutate(meta -> meta.getPersistentDataContainer().set(
                            plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()))
                    .build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", viewer))
                .build());

        viewer.openInventory(inventory);
    }

    private void fillGlass(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
    }
}