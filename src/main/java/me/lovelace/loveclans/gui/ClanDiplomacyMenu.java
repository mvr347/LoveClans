package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;

/**
 * Per-clan relations menu (§6.2). Grew from a 27-slot embargo/blockade/letters-only menu to a
 * 45-slot layout that also exposes war/siege/raid/peace and a trade shortcut - the war/siege/raid
 * mechanics (§3) predate this menu and were command-only until now. Slot numbers are adapted from
 * §6.2's mockup rather than matched pixel-for-pixel (matches how the rest of this menu already
 * diverged from spec - embargo/blockade/letters kept their existing slots to avoid needless
 * churn).
 */
public final class ClanDiplomacyMenu {
    private static final int SLOT_ALLY = 10;
    private static final int SLOT_NEUTRAL = 13;
    private static final int SLOT_ENEMY = 16;
    private static final int SLOT_EMBARGO = 19;
    private static final int SLOT_BLOCKADE = 20;
    private static final int SLOT_LETTERS = 22;
    private static final int SLOT_TRADE = 28;
    private static final int SLOT_WAR = 30;
    private static final int SLOT_SIEGE = 32;
    private static final int SLOT_RAID = 34;
    private static final int SLOT_PEACE = 38;
    private static final int SLOT_INFO = 40;
    private static final int SLOT_BACK = 43;
    private static final int SLOT_CLOSE = 44;
    private static final int INVENTORY_SIZE = 45;

    private final LoveClansPlugin plugin;

    public ClanDiplomacyMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan sourceClan, Clan targetClan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.DIPLOMACY, targetClan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.diplomacy.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));
        holder.setInventory(inventory);

        // Rule 8: only the unused header row (0-8) is pure frame here — every other row hosts
        // content, so it must never be blanket-glassed (glass in the content rows was exactly
        // the "странное расположение" bug: stray glass panes sitting between action buttons).
        for (int slot = 0; slot <= 8; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        DiplomacyRelation current = sourceClan.relationTo(targetClan.id());

        // ALLY = дружеские, NEUTRAL = нейтральные, ENEMY = враждебные отношения.
        // Активное отношение подсвечиваем свечением (glow), а не сменой текстуры головы.
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

        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id());
        ItemBuilder embargoItem = ItemBuilder.head(ItemBuilder.HEAD_EMBARGO)
                .name(plugin.getMessages().component(embargoed ? "gui.diplomacy.embargo.cancel-name" : "gui.diplomacy.embargo.declare-name", player))
                .lore(plugin.getMessages().component(embargoed ? "gui.diplomacy.embargo.cancel-lore" : "gui.diplomacy.embargo.declare-lore", player));
        if (embargoed) embargoItem.glow(true);
        inventory.setItem(SLOT_EMBARGO, embargoItem.build());

        boolean blockading = plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetClan.id());
        boolean blockadedByTarget = plugin.getDiplomacyManager().isBlockading(targetClan.id(), sourceClan.id());
        ItemBuilder blockadeItem;
        if (blockadedByTarget) {
            blockadeItem = ItemBuilder.head(ItemBuilder.HEAD_BLOCKADE)
                    .name(plugin.getMessages().component("gui.diplomacy.blockade.blocked-name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.blockade.blocked-lore", player));
        } else if (blockading) {
            blockadeItem = ItemBuilder.head(ItemBuilder.HEAD_BLOCKADE)
                    .name(plugin.getMessages().component("gui.diplomacy.blockade.cancel-name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.blockade.cancel-lore", player))
                    .glow(true);
        } else {
            blockadeItem = ItemBuilder.head(ItemBuilder.HEAD_BLOCKADE)
                    .name(plugin.getMessages().component("gui.diplomacy.blockade.declare-name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.blockade.declare-lore", player));
        }
        inventory.setItem(SLOT_BLOCKADE, blockadeItem.build());

        inventory.setItem(SLOT_LETTERS, ItemBuilder.head(ItemBuilder.HEAD_LETTERS)
                .name(plugin.getMessages().component("gui.diplomacy.letters.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.letters.lore", player))
                .build());

        inventory.setItem(SLOT_TRADE, buildTradeItem(sourceClan, targetClan, player).build());

        boolean inConflict = plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id());
        inventory.setItem(SLOT_WAR, buildWarItem(player, sourceClan, targetClan, inConflict).build());
        inventory.setItem(SLOT_SIEGE, buildSiegeItem(player, sourceClan, targetClan, inConflict).build());
        inventory.setItem(SLOT_RAID, buildRaidItem(player, targetClan, inConflict).build());

        if (inConflict) {
            inventory.setItem(SLOT_PEACE, ItemBuilder.of(Material.WHITE_BANNER)
                    .name(plugin.getMessages().component("gui.diplomacy.peace.name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.peace.lore", player))
                    .glow(true)
                    .build());
        } else {
            inventory.setItem(SLOT_PEACE, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.peace.unavailable-name", player))
                    .build());
        }

        inventory.setItem(SLOT_INFO, ItemBuilder.of(targetClan.emblem().name().endsWith("_BANNER") ? targetClan.emblem() : Material.WHITE_BANNER)
                .name(plugin.getMessages().component("gui.diplomacy.info.name",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.diplomacy.info.lore", Map.of(
                        "level", String.valueOf(targetClan.level()),
                        "influence", String.valueOf(targetClan.influence()),
                        "members", String.valueOf(targetClan.members().size()),
                        "relation", current.name()
                ), player))
                .build());

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.diplomacy.select-other.name", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemBuilder buildTradeItem(Clan sourceClan, Clan targetClan, Player player) {
        boolean blocked = plugin.getClanTradeManager().tradeBlocked(sourceClan.id(), targetClan.id());
        boolean canTrade = sourceClan.hasPermission(player.getUniqueId(), ClanPermission.TRADE);
        if (blocked) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.trade.unavailable-name", player));
        }
        if (!canTrade) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.trade.name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.trade.no-permission-lore", player));
        }
        return ItemBuilder.head(ItemBuilder.HEAD_TRADE)
                .name(plugin.getMessages().component("gui.diplomacy.trade.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.trade.lore", player));
    }

    private ItemBuilder buildWarItem(Player player, Clan sourceClan, Clan targetClan, boolean inConflict) {
        boolean inTargetTerritory = resolveContestedTerritory(player, targetClan).isPresent();
        if (inConflict || !inTargetTerritory) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                    .lore(plugin.getMessages().component(inConflict
                            ? "gui.diplomacy.war.unavailable-conflict" : "gui.diplomacy.war.unavailable-location", player));
        }
        return ItemBuilder.of(Material.NETHERITE_SWORD)
                .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.war.lore", player));
    }

    private ItemBuilder buildSiegeItem(Player player, Clan sourceClan, Clan targetClan, boolean inConflict) {
        boolean inTargetTerritory = resolveContestedTerritory(player, targetClan).isPresent();
        if (inConflict || !inTargetTerritory) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                    .lore(plugin.getMessages().component(inConflict
                            ? "gui.diplomacy.siege.unavailable-conflict" : "gui.diplomacy.siege.unavailable-location", player));
        }
        return ItemBuilder.of(Material.SPYGLASS)
                .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.siege.lore", player));
    }

    private ItemBuilder buildRaidItem(Player player, Clan targetClan, boolean inConflict) {
        if (inConflict) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.raid.name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.raid.unavailable-conflict", player));
        }
        return ItemBuilder.of(Material.LEATHER_BOOTS)
                .name(plugin.getMessages().component("gui.diplomacy.raid.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.raid.lore", player));
    }

    /** Same "standing inside the defender's territory" resolution as /clan war and /clan siege. */
    private Optional<TerritoryKey> resolveContestedTerritory(Player player, Clan defender) {
        boolean withinDefenderTerritory = plugin.getClanManager().getClanAt(player.getLocation())
                .map(owner -> owner.id().equals(defender.id()))
                .orElse(false);
        if (!withinDefenderTerritory) {
            return Optional.empty();
        }
        return defender.territories().stream()
                .filter(t -> t.boundingBox().contains(player.getLocation().toVector()))
                .findFirst()
                .map(ClanTerritory::key);
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
            plugin.getGuiManager().openDiplomacySelect(player, sourceClan);
            return;
        }
        if (slot == SLOT_EMBARGO) {
            handleEmbargoToggle(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_BLOCKADE) {
            handleBlockadeToggle(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_LETTERS) {
            plugin.getGuiManager().openLetters(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_TRADE) {
            if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.TRADE)) {
                plugin.getMessages().send(player, "general.no-permission");
                return;
            }
            if (plugin.getClanTradeManager().tradeBlocked(sourceClan.id(), targetClan.id())) {
                plugin.getMessages().send(player, "trade.blocked");
                return;
            }
            player.closeInventory();
            plugin.getClanTradeManager().proposeTradeAsync(sourceClan, player.getUniqueId(), targetClan)
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (slot == SLOT_WAR) {
            handleWarDeclare(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_SIEGE) {
            handleSiegeDeclare(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_RAID) {
            handleRaidDeclare(player, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_PEACE) {
            handlePeace(player, sourceClan, targetClan);
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

    private void handleEmbargoToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id());
        var future = embargoed
                ? plugin.getDiplomacyManager().cancelEmbargoAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareEmbargoAsync(sourceClan, player.getUniqueId(), targetClan);
        future.thenRun(() -> plugin.runSync(() -> open(player, sourceClan, targetClan)))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void handleBlockadeToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean blockading = plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetClan.id());
        var future = blockading
                ? plugin.getDiplomacyManager().cancelBlockadeAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareBlockadeAsync(sourceClan, player.getUniqueId(), targetClan);
        future.thenRun(() -> plugin.runSync(() -> open(player, sourceClan, targetClan)))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void handleWarDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.sendOperationError(player, new IllegalStateException("war.must-be-in-enemy-territory"));
            return;
        }
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.war.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> plugin.getWarManager().startWarAsync(sourceClan, targetClan, territory.get())
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private void handleSiegeDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.sendOperationError(player, new IllegalStateException("war.must-be-in-enemy-territory"));
            return;
        }
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.siege.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> plugin.getSiegeManager().startSiegeAsync(sourceClan, targetClan, territory.get())
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private void handleRaidDeclare(Player player, Clan sourceClan, Clan targetClan) {
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.raid.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> plugin.getRaidManager().startRaidAsync(sourceClan, targetClan)
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private void handlePeace(Player player, Clan sourceClan, Clan targetClan) {
        boolean atWar = plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id());
        boolean inSiege = !atWar && plugin.getSiegeManager().areInSiege(sourceClan.id(), targetClan.id());
        boolean inRaid = !atWar && !inSiege && plugin.getRaidManager().areInRaid(sourceClan.id(), targetClan.id());
        if (!atWar && !inSiege && !inRaid) {
            plugin.sendOperationError(player, new IllegalStateException("war.not-at-war"));
            return;
        }
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.peace.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> {
                    var future = atWar ? plugin.getWarManager().peaceAsync(sourceClan, targetClan)
                            : inSiege ? plugin.getSiegeManager().peaceAsync(sourceClan, targetClan)
                            : plugin.getRaidManager().peaceAsync(sourceClan, targetClan);
                    future.exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                },
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }
}
