package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

public final class ClanDiplomacyMenu {
    private final LoveClansPlugin plugin;

    public ClanDiplomacyMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan sourceClan, Clan targetClan) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.DIPLOMACY, targetClan.id()), 27,
                plugin.getMessages().component("gui.diplomacy.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        DiplomacyRelation current = sourceClan.relationTo(targetClan.id());

        // ALLY = дружеские, NEUTRAL = нейтральные, ENEMY = враждебные отношения.
        // Активное отношение подсвечиваем свечением (glow), а не сменой текстуры головы.
        ItemBuilder allyItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_FRIENDLY)
                .name(plugin.getMessages().component("gui.diplomacy.ally.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.ally.lore", player));
        if (current == DiplomacyRelation.ALLY) allyItem.glow(true);
        inventory.setItem(10, allyItem.build());

        ItemBuilder neutralItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_NEUTRAL)
                .name(plugin.getMessages().component("gui.diplomacy.neutral.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.neutral.lore", player));
        if (current == DiplomacyRelation.NEUTRAL) neutralItem.glow(true);
        inventory.setItem(13, neutralItem.build());

        ItemBuilder enemyItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_HOSTILE)
                .name(plugin.getMessages().component("gui.diplomacy.enemy.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.enemy.lore", player));
        if (current == DiplomacyRelation.ENEMY) enemyItem.glow(true);
        inventory.setItem(16, enemyItem.build());

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.diplomacy.select-other.name", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan targetClan, int slot) {
        Optional<Clan> sourceClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (sourceClanOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Clan sourceClan = sourceClanOpt.get();

        if (slot == 22) {
            plugin.getGuiManager().openDiplomacySelect(player, sourceClan);
            return;
        }

        DiplomacyRelation relation = switch (slot) {
            case 10 -> DiplomacyRelation.ALLY;
            case 13 -> DiplomacyRelation.NEUTRAL;
            case 16 -> DiplomacyRelation.ENEMY;
            default -> null;
        };
        if (relation == null) return;

        if (relation == DiplomacyRelation.ALLY) {
            if (sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY) {
                plugin.getClanManager().setDiplomacyAsync(sourceClan, targetClan, DiplomacyRelation.NEUTRAL, player.getUniqueId())
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "diplomacy.updated", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "relation", "NEUTRAL"));
                            open(player, updated, targetClan);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                return;
            }
            if (plugin.getClanManager().hasPendingAllianceFrom(targetClan.id(), sourceClan.id())) {
                plugin.getClanManager().acceptAllianceAsync(sourceClan, targetClan, player.getUniqueId())
                        .thenRun(() -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "diplomacy.alliance-accepted", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
                            plugin.getClanManager().getOnlineLeader(targetClan).ifPresent(leader ->
                                    plugin.getMessages().send(leader, "diplomacy.alliance-accepted-by", Map.of("tag", sourceClan.tag(), "color", sourceClan.tagColor())));
                            open(player, sourceClan, targetClan);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                return;
            }
            plugin.getClanManager().addAllianceRequest(sourceClan.id(), targetClan.id());
            plugin.getMessages().send(player, "diplomacy.alliance-sent", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
            plugin.getClanManager().getOnlineLeader(targetClan).ifPresent(leader ->
                    plugin.getMessages().sendClickableAlliance(leader, sourceClan.tag(), sourceClan.tagColor()));
            open(player, sourceClan, targetClan);
            return;
        }

        plugin.getClanManager().setDiplomacyAsync(sourceClan, targetClan, relation, player.getUniqueId())
                .thenAccept(updated -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "diplomacy.updated", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "relation", relation.name()));
                    open(player, updated, targetClan);
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }
}
