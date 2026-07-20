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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClanMembersMenu {
    private final LoveClansPlugin plugin;

    public ClanMembersMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        List<ClanMember> sortedMembers = clan.members().values().stream()
                .sorted(Comparator.comparingInt((ClanMember member) -> member.rank().weight()).reversed())
                .toList();

        int numMembers = sortedMembers.size();
        // Calculate required rows for members, each row holds 9 members. Add 2 for top/bottom control rows.
        int requiredRows = (int) Math.ceil(numMembers / 9.0) + 2;
        // Ensure minimum 3 rows (27 slots) and maximum 6 rows (54 slots)
        int inventorySize = Math.max(27, Math.min(54, requiredRows * 9));

        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.MEMBERS, clan.id()), inventorySize,
                plugin.getMessages().component("gui.members-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        // Стеклом закрываем только верхний и нижний ряд (служебные), а не зону с головами игроков —
        // там пустые слоты должны оставаться пустыми, без стеклянных панелей вокруг голов.
        fillGlassExcludingContent(inventory, 9, inventorySize - 9);

        boolean canInvite = clan.member(player.getUniqueId())
                .map(m -> m.rank().atLeast(ClanRank.GUARDIAN))
                .orElse(false);
                
        boolean isGuildmaster = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER)
                .orElse(false);

        // Place member heads starting from the second row (slot 9)
        for (int i = 0; i < numMembers; i++) {
            ClanMember member = sortedMembers.get(i);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerId());
            String name = offline.getName() != null ? offline.getName() : member.playerId().toString().substring(0, 8);
            String status = offline.isOnline() ? "<green>В сети</green>" : "<red>Оффлайн</red>";

            ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(plugin.getMessages().component("gui.members.item.name", Map.of("player", name), player))
                    .lore(plugin.getMessages().component("gui.members.item.rank",
                            Map.of("rank", member.rank().displayName()), player))
                    .lore(plugin.getMessages().component("gui.members.item.status",
                            Map.of("status", status), player))
                    .lore(plugin.getMessages().component("gui.members.item.contribution",
                            Map.of("amount", String.valueOf(member.contribution())), player));

            // Show click hint for guildmaster (not for themselves or other GMs)
            if (isGuildmaster && member.rank() != ClanRank.LEADER && !member.playerId().equals(player.getUniqueId())) {
                builder.lore(plugin.getMessages().component("gui.members.item.hint", player));
            }

            builder.mutate(meta -> {
                if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(offline);
                meta.getPersistentDataContainer().set(
                        plugin.getGuiManager().memberKey(), PersistentDataType.STRING,
                        member.playerId().toString());
            });

            inventory.setItem(9 + i, builder.build()); // Start placing from slot 9
        }

        // Footer — standard positions: Back = size-2, Close = size-1, extra action (Invite) = size-3
        inventory.setItem(inventorySize - 2, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(inventorySize - 1, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        if (canInvite) {
            boolean isFull = plugin.getClanManager().isClanFull(clan);
            ItemBuilder inviteBuilder = isFull ? ItemBuilder.head(ItemBuilder.HEAD_INACTIVE) : ItemBuilder.head(ItemBuilder.HEAD_INVITE);
            inviteBuilder.name(plugin.getMessages().component("gui.members.invite.name", player))
                    .lore(plugin.getMessages().component(isFull ? "gui.members.invite.lore-full" : "gui.members.invite.lore", player));
            inventory.setItem(inventorySize - 3, inviteBuilder.build()); // Invite button
        }

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        int inventorySize = player.getOpenInventory().getTopInventory().getSize();
        int backButtonSlot = inventorySize - 2;
        int closeButtonSlot = inventorySize - 1;
        int inviteButtonSlot = inventorySize - 3;

        if (slot == closeButtonSlot) {
            player.closeInventory();
            return;
        }

        if (slot == backButtonSlot) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }

        if (slot == inviteButtonSlot) {
            boolean canInvite = clan.hasPermission(player.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.INVITE);
            if (canInvite) {
                if (plugin.getClanManager().isClanFull(clan)) {
                    plugin.getMessages().send(player, "clan.member-limit-reached");
                    return;
                }
                player.closeInventory();
                plugin.getMessages().send(player, "gui.members.invite.prompt");
                plugin.expectChatInput(player.getUniqueId(), (inputName, isCancelled) -> {
                    if (isCancelled) {
                        plugin.runSync(() -> open(player, clan));
                        return;
                    }
                    plugin.getServer().dispatchCommand(player, "clan invite " + inputName);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getClanManager().getPlayerClan(player.getUniqueId())
                                .ifPresent(refreshed -> open(player, refreshed));
                    }, 10L);
                });
            } else {
                plugin.getMessages().send(player, "general.no-permission");
            }
            return;
        }

        org.bukkit.inventory.ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        String playerId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (playerId == null) return;
        try {
            java.util.UUID targetId = java.util.UUID.fromString(playerId);
            if (targetId.equals(player.getUniqueId())) return;
            // Открываем детальное меню участника только тем, у кого есть право KICK —
            // именно оно определяет доступ к кику/повышению/понижению в системе прав клана.
            // Игроки без этого права видят список голов, но не могут их открыть.
            if (!clan.hasPermission(player.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.KICK)) {
                plugin.getMessages().send(player, "general.no-permission");
                return;
            }
            plugin.getGuiManager().openMemberDetail(player, clan, targetId);
        } catch (IllegalArgumentException ignored) {}
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            // Only fill empty slots, do not overwrite existing items
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build());
            }
        }
    }

    /**
     * Заполняет инвентарь стеклянными панелями, но пропускает диапазон [contentStart, contentEnd) —
     * там, где будут размещены головы игроков. Так стекло остаётся только в служебных
     * верхнем и нижнем рядах, а слоты вокруг голов остаются пустыми (AIR).
     */
    private void fillGlassExcludingContent(Inventory inventory, int contentStart, int contentEnd) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot >= contentStart && slot < contentEnd) {
                continue;
            }
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build());
            }
        }
    }
}