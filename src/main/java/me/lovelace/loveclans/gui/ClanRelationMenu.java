package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

/**
 * Выбор отношений с кланом: союз, нейтралитет, вражда. Раньше эти три кнопки жили в шапке
 * меню дипломатии и конкурировали там с разделами; теперь дипломатия открывает это подменю
 * одной кнопкой «Отношения» из рабочей зоны.
 *
 * <p>Раскладка gui_gen v1.4, 27 слотов: голова цели в слоте 0, три варианта отношений —
 * переключатели в шапке (3, 4, 5), строка 9-17 пустая, футер со стеклом, назад и закрытие.
 */
public final class ClanRelationMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_ALLY = 3;
    private static final int SLOT_NEUTRAL = 4;
    private static final int SLOT_ENEMY = 5;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    private static final int INVENTORY_SIZE = 27;

    private final LoveClansPlugin plugin;

    public ClanRelationMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan sourceClan, Clan targetClan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.RELATIONS, targetClan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.diplomacy.relations.title",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        DiplomacyRelation current = sourceClan.relationTo(targetClan.id());

        // Активное отношение подсвечиваем свечением, а не сменой текстуры головы.
        ItemBuilder allyItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_FRIENDLY)
                .name(plugin.getMessages().component("gui.diplomacy.ally.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.ally.lore", player));
        if (current == DiplomacyRelation.ALLY) allyItem.glow(true);
        inventory.setItem(SLOT_ALLY, allyItem.build());

        ItemBuilder neutralItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_NEUTRAL)
                .name(plugin.getMessages().component("gui.diplomacy.neutral.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.neutral.lore", player));
        if (current == DiplomacyRelation.NEUTRAL) neutralItem.glow(true);
        inventory.setItem(SLOT_NEUTRAL, neutralItem.build());

        ItemBuilder enemyItem = ItemBuilder.head(ItemBuilder.HEAD_RELATION_HOSTILE)
                .name(plugin.getMessages().component("gui.diplomacy.enemy.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.enemy.lore", player));
        if (current == DiplomacyRelation.ENEMY) enemyItem.glow(true);
        inventory.setItem(SLOT_ENEMY, enemyItem.build());

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                .name(plugin.getMessages().component("gui.diplomacy.info.name",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.diplomacy.relations.lore",
                        Map.of("relation", plugin.getMessages().relationName(current)), player))
                .build());

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
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

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openDiplomacy(player, sourceClan, targetClan);
            return;
        }

        DiplomacyRelation relation = switch (slot) {
            case SLOT_ALLY -> DiplomacyRelation.ALLY;
            case SLOT_NEUTRAL -> DiplomacyRelation.NEUTRAL;
            case SLOT_ENEMY -> DiplomacyRelation.ENEMY;
            default -> null;
        };
        if (relation == null) return;

        if (relation == DiplomacyRelation.ALLY) {
            handleAlly(player, sourceClan, targetClan);
            return;
        }

        plugin.getClanManager().setDiplomacyAsync(sourceClan, targetClan, relation, player.getUniqueId())
                .thenAccept(updated -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "diplomacy.updated", Map.of(
                            "tag", targetClan.tag(), "color", targetClan.tagColor(),
                            "relation", plugin.getMessages().relationName(relation)));
                    open(player, updated, targetClan);
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    /** Союз двусторонний: повторный клик разрывает его, встречный запрос — принимает. */
    private void handleAlly(Player player, Clan sourceClan, Clan targetClan) {
        if (sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY) {
            plugin.getClanManager().setDiplomacyAsync(sourceClan, targetClan, DiplomacyRelation.NEUTRAL, player.getUniqueId())
                    .thenAccept(updated -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "diplomacy.updated", Map.of(
                                "tag", targetClan.tag(), "color", targetClan.tagColor(),
                                "relation", plugin.getMessages().relationName(DiplomacyRelation.NEUTRAL)));
                        open(player, updated, targetClan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (plugin.getClanManager().hasPendingAllianceFrom(targetClan.id(), sourceClan.id())) {
            plugin.getClanManager().acceptAllianceAsync(sourceClan, targetClan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "diplomacy.alliance-accepted",
                                Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
                        plugin.getClanManager().getOnlineLeader(targetClan).ifPresent(leader ->
                                plugin.getMessages().send(leader, "diplomacy.alliance-accepted-by",
                                        Map.of("tag", sourceClan.tag(), "color", sourceClan.tagColor())));
                        open(player, sourceClan, targetClan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        plugin.getClanManager().addAllianceRequest(sourceClan.id(), targetClan.id());
        plugin.getMessages().send(player, "diplomacy.alliance-sent",
                Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
        plugin.getClanManager().getOnlineLeader(targetClan).ifPresent(leader ->
                plugin.getMessages().sendClickableAlliance(leader, sourceClan.tag(), sourceClan.tagColor()));
        open(player, sourceClan, targetClan);
    }
}
