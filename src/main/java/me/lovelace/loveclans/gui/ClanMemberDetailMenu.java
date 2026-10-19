package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPermission;
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

import java.util.List;
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
        String name = offline.getName() != null ? offline.getName() : plugin.getMessages().raw("gui.members.unknown-player");

        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.MEMBER_DETAIL, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, 27,
                plugin.getMessages().component("gui.member-detail.title", Map.of("player", name), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        // Слот 0: голова изучаемого участника — "профиль косвенно" (это не сам игрок,
        // открывающий меню, а третье лицо, которым он управляет).
        Map<String, String> headValues = Map.of(
                "player", name,
                "rank", member.rank().displayName(),
                "amount", me.lovelace.loveclans.util.CoinFormat.format(member.contribution()),
                "since", me.lovelace.loveclans.util.DurationFormat.format(Math.max(0L, System.currentTimeMillis() - member.joinedAt())),
                "status", plugin.getMessages().raw(offline.isOnline() ? "gui.members.item.status-online" : "gui.members.item.status-offline"));
        ItemBuilder head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(plugin.getMessages().component(isSelfView(member, player) ? "gui.member-detail.name-self" : "gui.member-detail.name", headValues, player))
                .lore(plugin.getMessages().components("gui.member-detail.lore", headValues, player));
        head.mutate(meta -> {
            if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(offline);
        });
        inventory.setItem(0, head.build());

        boolean isLeader = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER)
                .orElse(false);
        boolean isSelf = member.playerId().equals(player.getUniqueId());
        // Viewing a profile is open to everyone; changing someone's rank or removing them needs the KICK right.
        boolean canManage = clan.hasPermission(player.getUniqueId(), ClanPermission.KICK);

        // Слоты 10, 12, 14, 16 (четыре кнопки по центру рабочей строки, GuiFrames.workRowSlots(4)) — передача лидерства,
        // повышение, понижение, кик. Кнопки отображаются всегда (не пропадают), но становятся
        // неактивными (серый череп + причина в lore), если действие сейчас недоступно — так
        // игрок видит весь набор возможностей, а не гадает, почему кнопки нет.
        boolean canTransfer = isLeader && !isSelf;
        inventory.setItem(10, canTransfer
                ? item(ItemBuilder.HEAD_EXPAND, "gui.member-detail.transfer.name", "gui.member-detail.transfer.lore", player, targetId)
                : inactiveItem("gui.member-detail.transfer.name",
                        isSelf ? "gui.member-detail.transfer.disabled-self" : "gui.member-detail.transfer.disabled-not-leader", player));

        boolean canPromote = canManage && !isSelf && member.rank().nextRank() != null && member.rank().nextRank() != ClanRank.LEADER;
        inventory.setItem(12, canPromote
                ? item(ItemBuilder.HEAD_INFO, "gui.member-detail.promote.name", "gui.member-detail.promote.lore", player, targetId)
                : inactiveItem("gui.member-detail.promote.name", !canManage ? "gui.member-detail.disabled-no-permission" : isSelf ? "gui.member-detail.promote.disabled-self" : "gui.member-detail.promote.disabled-max-rank", player));

        boolean canDemote = canManage && !isSelf && member.rank().previousRank() != null;
        inventory.setItem(14, canDemote
                ? item(ItemBuilder.HEAD_BACK, "gui.member-detail.demote.name", "gui.member-detail.demote.lore", player, targetId)
                : inactiveItem("gui.member-detail.demote.name", !canManage ? "gui.member-detail.disabled-no-permission" : isSelf ? "gui.member-detail.demote.disabled-self" : "gui.member-detail.demote.disabled-min-rank", player));

        boolean canKick = canManage && !isSelf && member.rank() != ClanRank.LEADER;
        inventory.setItem(16, canKick
                ? item(ItemBuilder.HEAD_BARRIER, "gui.member-detail.kick.name", "gui.member-detail.kick.lore", player, targetId)
                : inactiveItem("gui.member-detail.kick.name",
                        !canManage ? "gui.member-detail.disabled-no-permission" : isSelf ? "gui.member-detail.kick.disabled-self" : "gui.member-detail.kick.disabled-leader", player));

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private static boolean isSelfView(ClanMember member, Player viewer) {
        return member.playerId().equals(viewer.getUniqueId());
    }

    private org.bukkit.inventory.ItemStack item(String headTexture, String nameKey, String loreKey, Player player, UUID targetId) {
        String base = nameKey.substring(0, nameKey.length() - ".name".length());
        ItemBuilder builder = ItemBuilder.head(headTexture)
                .name(plugin.getMessages().component(nameKey, player))
                .lore(plugin.getMessages().components(loreKey, player))
                .lore(LoreBlocks.clicks(plugin.getMessages(), player,
                        List.of(LoreBlocks.hint(plugin.getMessages(), LoreBlocks.Click.LEFT, base + ".action"))));
        builder.mutate(meta -> meta.getPersistentDataContainer()
                .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, targetId.toString()));
        return builder.build();
    }

    // Неактивная кнопка: серая голова, без PDC-тэга — клик по ней не обрабатывается
    // (handleInventoryClick игнорирует слоты без распознанного targetId/действия).
    private org.bukkit.inventory.ItemStack inactiveItem(String nameKey, String reasonKey, Player player) {
        String base = nameKey.substring(0, nameKey.length() - ".name".length());
        return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                .name(LoreBlocks.disabledName(plugin.getMessages(), player, nameKey))
                .lore(plugin.getMessages().components(base + ".lore", player))
                .lore(Component.empty())
                .lore(LoreBlocks.blockedReason(plugin.getMessages(), player, reasonKey, Map.of(),
                        LoreBlocks.hint(plugin.getMessages(), LoreBlocks.Click.LEFT, base + ".action")))
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
            case 10 -> {
                String targetName = nameOf(targetId);
                plugin.getGuiManager().openConfirm(player, clan,
                        plugin.getMessages().component("gui.confirm.transfer.title", Map.of("player", targetName), player),
                        plugin.getGuiManager().confirmEffects(player, "transfer", Map.of("player", targetName)),
                        () -> plugin.getClanManager().transferLeadershipAsync(clan, player.getUniqueId(), targetId)
                                .thenRun(() -> plugin.runSync(() -> {
                                    plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
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
            }
            case 12 -> {
                ClanRank newRank = targetRank.nextRank();
                if (newRank != null && newRank != ClanRank.LEADER) {
                    plugin.getClanManager().setRankAsync(clan, player.getUniqueId(), targetId, newRank)
                            .thenAccept(updated -> plugin.runSync(() -> {
                                plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                                open(player, updated, targetId);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                }
            }
            case 14 -> {
                ClanRank newRank = targetRank.previousRank();
                if (newRank != null) {
                    plugin.getClanManager().setRankAsync(clan, player.getUniqueId(), targetId, newRank)
                            .thenAccept(updated -> plugin.runSync(() -> {
                                plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                                open(player, updated, targetId);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                }
            }
            case 16 -> {
                String targetName = nameOf(targetId);
                plugin.getGuiManager().openConfirm(player, clan,
                        plugin.getMessages().component("gui.confirm.kick.title", Map.of("player", targetName), player),
                        plugin.getGuiManager().confirmEffects(player, "kick", Map.of("player", targetName)),
                        () -> plugin.getClanManager().removeMemberAsync(clan, player.getUniqueId(), targetId, true)
                                .thenRun(() -> plugin.runSync(() -> {
                                    plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                                    plugin.getMessages().send(player, "clan.kicked", Map.of("player", targetName));
                                    plugin.getGuiManager().openMembers(player, clan);
                                }))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                        () -> plugin.runSync(() -> open(player, clan, targetId))
                );
            }
            default -> {}
        }
    }

    private String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }
}
