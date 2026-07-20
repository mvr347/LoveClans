package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClanMemberDetailMenu {
    private final LoveClansPlugin plugin;

    public ClanMemberDetailMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan, UUID targetId) {
        Optional<ClanMember> memberOpt = clan.member(targetId);
        if (memberOpt.isEmpty()) {
            plugin.getGuiManager().openMembers(player, clan);
            return;
        }
        ClanMember member = memberOpt.get();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() != null ? offline.getName() : targetId.toString().substring(0, 8);

        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.MEMBER_DETAIL, clan.id()), 27,
                plugin.getMessages().component("gui.member-detail.title", Map.of("player", name), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        ItemBuilder head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(plugin.getMessages().component("gui.member-detail.name", Map.of("player", name), player))
                .lore(plugin.getMessages().component("gui.member-detail.rank", Map.of("rank", member.rank().displayName()), player));
        head.mutate(meta -> {
            if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(offline);
        });
        inventory.setItem(4, head.build());

        boolean isLeader = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER)
                .orElse(false);
        boolean isSelf = member.playerId().equals(player.getUniqueId());

        // Слоты 11/13/15 — передача лидерства/повышение/понижение. Кнопки отображаются всегда
        // (не пропадают), но становятся неактивными (серый череп + причина в lore), если действие
        // сейчас недоступно — так игрок видит весь набор возможностей, а не гадает, почему кнопки нет.
        boolean canTransfer = isLeader && !isSelf;
        inventory.setItem(11, canTransfer
                ? item(ItemBuilder.HEAD_EXPAND, "gui.member-detail.transfer.name", "gui.member-detail.transfer.lore", player, targetId)
                : inactiveItem("gui.member-detail.transfer.name",
                        isSelf ? "gui.member-detail.transfer.disabled-self" : "gui.member-detail.transfer.disabled-not-leader", player));

        boolean canPromote = !isSelf && member.rank().nextRank() != null && member.rank().nextRank() != ClanRank.LEADER;
        inventory.setItem(13, canPromote
                ? item(ItemBuilder.HEAD_INFO, "gui.member-detail.promote.name", "gui.member-detail.promote.lore", player, targetId)
                : inactiveItem("gui.member-detail.promote.name", "gui.member-detail.promote.disabled-max-rank", player));

        boolean canDemote = !isSelf && member.rank().previousRank() != null;
        inventory.setItem(15, canDemote
                ? item(ItemBuilder.HEAD_BACK, "gui.member-detail.demote.name", "gui.member-detail.demote.lore", player, targetId)
                : inactiveItem("gui.member-detail.demote.name", "gui.member-detail.demote.disabled-min-rank", player));

        if (!isSelf && member.rank() != ClanRank.LEADER) {
            inventory.setItem(16, item(ItemBuilder.HEAD_BARRIER, "gui.member-detail.kick.name", "gui.member-detail.kick.lore", player, targetId));
        }

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack item(String headTexture, String nameKey, String loreKey, Player player, UUID targetId) {
        ItemBuilder builder = ItemBuilder.head(headTexture)
                .name(plugin.getMessages().component(nameKey, player))
                .lore(plugin.getMessages().component(loreKey, player));
        builder.mutate(meta -> meta.getPersistentDataContainer()
                .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()));
        return builder.build();
    }

    // Неактивная кнопка: серая голова, без PDC-тэга — клик по ней не обрабатывается
    // (handleInventoryClick игнорирует слоты без распознанного targetId/действия).
    private org.bukkit.inventory.ItemStack inactiveItem(String nameKey, String reasonKey, Player player) {
        return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component(nameKey, player))
                .lore(plugin.getMessages().component(reasonKey, player))
                .build();
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, org.bukkit.inventory.ItemStack clickedItem) {
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        if (slot == 25) {
            plugin.getGuiManager().openMembers(player, clan);
            return;
        }
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String rawId = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rawId == null) return;

        UUID targetId;
        try {
            targetId = UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        Optional<ClanMember> targetMemberOpt = clan.member(targetId);
        if (targetMemberOpt.isEmpty()) return;
        ClanRank targetRank = targetMemberOpt.get().rank();

        switch (slot) {
            case 13 -> {
                ClanRank newRank = targetRank.nextRank();
                if (newRank != null && newRank != ClanRank.LEADER) {
                    plugin.getClanManager().setRankAsync(clan, player.getUniqueId(), targetId, newRank)
                            .thenAccept(updated -> plugin.runSync(() -> open(player, updated, targetId)))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                }
            }
            case 15 -> {
                ClanRank newRank = targetRank.previousRank();
                if (newRank != null) {
                    plugin.getClanManager().setRankAsync(clan, player.getUniqueId(), targetId, newRank)
                            .thenAccept(updated -> plugin.runSync(() -> open(player, updated, targetId)))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                }
            }
            case 16 -> {
                String targetName = nameOf(targetId);
                plugin.getClanManager().removeMemberAsync(clan, player.getUniqueId(), targetId, true)
                        .thenRun(() -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "clan.kicked", Map.of("player", targetName));
                            plugin.getGuiManager().openMembers(player, clan);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            }
            case 11 -> plugin.getGuiManager().openConfirm(player, clan,
                    plugin.getMessages().component("gui.confirm.transfer.title", player), Component.empty(),
                    () -> plugin.getClanManager().transferLeadershipAsync(clan, player.getUniqueId(), targetId)
                            .thenRun(() -> plugin.runSync(() -> {
                                plugin.getGuiManager().openMembers(player, clan);
                                // Если новый лидер сейчас смотрит на главное меню клана (например, с другого
                                // устройства/окна), его меню нужно перерисовать — иначе кнопка «Покинуть клан»
                                // останется видна, хотя теперь он лидер и не должен её видеть.
                                Player newLeader = Bukkit.getPlayer(targetId);
                                if (newLeader != null) {
                                    plugin.getGuiManager().refreshMainMenuIfOpen(newLeader, clan);
                                }
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                    () -> plugin.runSync(() -> open(player, clan, targetId))
            );
            default -> {}
        }
    }

    private String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }
}
