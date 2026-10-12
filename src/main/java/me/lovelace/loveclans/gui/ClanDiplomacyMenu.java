package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.diplomacy.ClanRelationRecord;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import me.lovelace.loveclans.model.diplomacy.PactType;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Diplomacy screen with one clan (54 slots). There is no status switch and no reset button: the relation is a score
 * shared by the pair that only moves through actions and events (gifts, trade, pacts, sanctions, wars, kills).
 * What can be done depends on the tier, via {@link DiplomacyActions}: the same matrix decides whether a button is
 * active and whether the server accepts the click, so a button description never promises more than the code does.
 * <p>
 * Layout: slot 4 shows the relation, row 2 warms it (gift, trade, gesture), row 3 holds the pacts, row 4 is pressure
 * (insult, embargo, blockade, conflict).
 */
public final class ClanDiplomacyMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_STATE = 4;

    private static final int SLOT_GIFT = 20;
    private static final int SLOT_TRADE = 22;
    private static final int SLOT_IMPROVE = 24;

    private static final int SLOT_NAP = 29;
    private static final int SLOT_TRADE_PACT = 31;
    private static final int SLOT_ALLIANCE = 33;

    private static final int SLOT_INSULT = 37;
    private static final int SLOT_EMBARGO = 39;
    private static final int SLOT_BLOCKADE = 41;
    private static final int SLOT_CONFLICT = 43;

    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int BAR_CELLS = 20;

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

        GuiFrames.fillFrame54(inventory);

        DiplomacyTier tier = sourceClan.tierTo(targetClan.id());
        String recBadgeKey = targetClan.isRecognized() ? "gui.diplomacy.unrecognized.recognized-badge" : "gui.diplomacy.unrecognized.other";
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                .name(plugin.getMessages().component("gui.diplomacy.info.name",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "name", targetClan.name()), player))
                .lore(plugin.getMessages().components("gui.diplomacy.info.lore", Map.of(
                        "level", String.valueOf(targetClan.level()),
                        "influence", String.valueOf(targetClan.influence()),
                        "members", String.valueOf(targetClan.members().size()),
                        "relation", tierTitle(tier)
                ), player))
                .lore(plugin.getMessages().component(recBadgeKey, player))
                .build());

        inventory.setItem(SLOT_STATE, buildStateItem(player, sourceClan, targetClan, tier));

        inventory.setItem(SLOT_GIFT, buildGiftItem(player, sourceClan, targetClan));
        inventory.setItem(SLOT_TRADE, buildTradeItem(player, sourceClan, targetClan));
        inventory.setItem(SLOT_IMPROVE, buildImproveItem(player, sourceClan, targetClan));

        inventory.setItem(SLOT_NAP, buildPactItem(player, sourceClan, targetClan, PactType.NAP, "nap", ItemBuilder.HEAD_ABILITY_SANCTUARY));
        inventory.setItem(SLOT_TRADE_PACT, buildPactItem(player, sourceClan, targetClan, PactType.TRADE, "trade-pact", ItemBuilder.HEAD_CHEST_MONEY));
        inventory.setItem(SLOT_ALLIANCE, buildPactItem(player, sourceClan, targetClan, PactType.ALLIANCE, "alliance", ItemBuilder.HEAD_RELATION_ALLIED));

        inventory.setItem(SLOT_INSULT, buildInsultItem(player, sourceClan, targetClan));
        inventory.setItem(SLOT_EMBARGO, buildEmbargoItem(player, sourceClan, targetClan));
        inventory.setItem(SLOT_BLOCKADE, buildBlockadeItem(player, sourceClan, targetClan));
        inventory.setItem(SLOT_CONFLICT, buildConflictItem(player, sourceClan, targetClan));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.diplomacy.select-other.name", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private String tierTitle(DiplomacyTier tier) {
        return plugin.getMessages().raw("diplomacy.tier." + tier.name().toLowerCase(java.util.Locale.ROOT));
    }

    // --- Relation indicator (slot 4) ---

    private ItemStack buildStateItem(Player player, Clan sourceClan, Clan targetClan, DiplomacyTier tier) {
        ClanRelationRecord record = sourceClan.getRelation(targetClan.id());
        int score = record.score();
        int max = ClanRelationRecord.MAX_SCORE;
        Map<String, String> ph = new HashMap<>();
        ph.put("tier", tierTitle(tier));
        ph.put("score", String.valueOf(score));
        ph.put("max", String.valueOf(max));
        ph.put("trend", record.trendUnicode());
        ph.put("diff", (record.trend() > 0 ? "+" : "") + record.trend());
        ph.put("bar", progressBar(score, tier));

        List<String> agreements = new ArrayList<>();
        for (PactType type : PactType.values()) {
            if (plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), type)) {
                agreements.add(type.displayName());
            }
        }
        if (plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id())) {
            agreements.add(plugin.getMessages().raw("gui.diplomacy.state.embargo"));
        }
        if (plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetClan.id())
                || plugin.getDiplomacyManager().isBlockading(targetClan.id(), sourceClan.id())) {
            agreements.add(plugin.getMessages().raw("gui.diplomacy.state.blockade"));
        }
        ph.put("agreements", agreements.isEmpty() ? plugin.getMessages().raw("gui.diplomacy.state.none") : String.join(", ", agreements));

        ItemBuilder builder = ItemBuilder.head(ItemBuilder.headForTier(tier))
                .name(plugin.getMessages().component("gui.diplomacy.state.name", ph, player))
                .lore(plugin.getMessages().components("gui.diplomacy.state.lore", ph, player));

        DiplomacyTier[] tiers = DiplomacyTier.values();
        if (tier.ordinal() + 1 < tiers.length) {
            DiplomacyTier next = tiers[tier.ordinal() + 1];
            ph.put("next", tierTitle(next));
            ph.put("need", String.valueOf(Math.max(0, next.minScore() - score)));
            builder.lore(plugin.getMessages().component(next == DiplomacyTier.ALLIED
                    ? "gui.diplomacy.state.next-allied" : "gui.diplomacy.state.next", ph, player));
        }
        if (tier.ordinal() > 0) {
            ph.put("prev", tierTitle(tiers[tier.ordinal() - 1]));
            ph.put("left", String.valueOf(Math.max(1, score - tier.minScore() + 1)));
            builder.lore(plugin.getMessages().component("gui.diplomacy.state.prev", ph, player));
        }
        builder.lore(Component.empty());
        builder.lore(plugin.getMessages().components("gui.diplomacy.state.tier-" + tier.name().toLowerCase(java.util.Locale.ROOT), ph, player));
        return builder.glow(tier == DiplomacyTier.ALLIED || tier == DiplomacyTier.HOSTILE).build();
    }

    /** Twenty cells from -100 to +100 in the color of the current tier. */
    private String progressBar(int score, DiplomacyTier tier) {
        int span = ClanRelationRecord.MAX_SCORE - ClanRelationRecord.MIN_SCORE;
        int filled = Math.max(0, Math.min(BAR_CELLS, (int) Math.round((score - ClanRelationRecord.MIN_SCORE) * (double) BAR_CELLS / span)));
        String color = switch (tier) {
            case HOSTILE -> "red";
            case UNFRIENDLY -> "gold";
            case NEUTRAL -> "gray";
            case CORDIAL -> "green";
            case FRIENDLY -> "dark_green";
            case ALLIED -> "aqua";
        };
        return "<" + color + ">" + "█".repeat(filled) + "</" + color + "><dark_gray>" + "█".repeat(BAR_CELLS - filled) + "</dark_gray>";
    }

    // --- Shared button plumbing ---

    private DenyResult commonDeny(Player player, Clan sourceClan, Clan targetClan, ClanPermission permission) {
        if (!sourceClan.isRecognized() || !targetClan.isRecognized()) {
            return DenyResult.of("deny.diplomacy.need-recognized");
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), permission)) {
            return DenyResult.of("deny.no-permission");
        }
        return null;
    }

    private DenyResult matrixDeny(DiplomacyActions.Action action, Clan sourceClan, Clan targetClan) {
        PactType pact = switch (action) {
            case NAP -> PactType.NAP;
            case TRADE_PACT -> PactType.TRADE;
            case ALLIANCE -> PactType.ALLIANCE;
            default -> null;
        };
        int min = pact == null ? 0 : plugin.getDiplomacyManager().minScore(pact);
        String key = DiplomacyActions.denyKey(action, plugin.getDiplomacyManager().contextOf(sourceClan, targetClan), min);
        return key == null ? null : DenyResult.of(key, Map.of("min", String.valueOf(min)));
    }

    private DenyResult cooldownDeny(Clan sourceClan, Clan targetClan, String action) {
        long remaining = plugin.getDiplomacyManager().getActionCooldownRemaining(sourceClan.id(), targetClan.id(), action);
        return remaining > 0
                ? DenyResult.of("diplomacy.deny.cooldown", Map.of("time", me.lovelace.loveclans.util.DurationFormat.format(remaining)))
                : null;
    }

    private DenyResult conflictDeny(Clan sourceClan, Clan targetClan) {
        return plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id())
                ? DenyResult.of("diplomacy.conflict-in-progress") : null;
    }

    private DenyResult firstDeny(DenyResult... candidates) {
        for (DenyResult candidate : candidates) {
            if (candidate != null) return candidate;
        }
        return null;
    }

    /** Active button, or a grey one that keeps the description and states why it is unavailable. */
    private ItemStack actionItem(Player player, String head, String base, Map<String, String> ph, DenyResult deny, boolean glow) {
        ItemBuilder builder = ItemBuilder.head(deny == null ? head : ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component(base + ".name", ph, player))
                .lore(plugin.getMessages().components(base + ".lore", ph, player));
        if (deny != null) {
            Map<String, String> merged = new HashMap<>(ph);
            merged.putAll(deny.placeholders());
            builder.lore(Component.empty());
            builder.lore(plugin.getMessages().component("gui.diplomacy.unavailable", player));
            builder.lore(plugin.getMessages().denyLore(deny.key(), merged, player));
        } else if (glow) {
            builder.glow(true);
        }
        return builder.build();
    }

    private double cfgDouble(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    private int cfgInt(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    // --- Warming actions (row 2) ---

    private ItemStack buildGiftItem(Player player, Clan sourceClan, Clan targetClan) {
        DenyResult deny;
        if (!sourceClan.isRecognized()) {
            deny = DenyResult.of("deny.gift.sender-not-recognized");
        } else if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            deny = DenyResult.of("deny.no-permission");
        } else if (!sourceClan.hasCapital() || !targetClan.hasCapital()) {
            deny = DenyResult.of("clan.no-territory");
        } else if (plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id())) {
            deny = DenyResult.of("gift.blocked-by-war");
        } else {
            deny = matrixDeny(DiplomacyActions.Action.GIFT, sourceClan, targetClan);
        }
        return actionItem(player, ItemBuilder.HEAD_CHEST_MONEY, "gui.diplomacy.act.gift", Map.of(), deny, false);
    }

    private ItemStack buildTradeItem(Player player, Clan sourceClan, Clan targetClan) {
        DenyResult deny = commonDeny(player, sourceClan, targetClan, ClanPermission.TRADE);
        if (deny == null) deny = matrixDeny(DiplomacyActions.Action.TRADE, sourceClan, targetClan);
        if (deny == null && plugin.getClanTradeManager().tradeBlocked(sourceClan.id(), targetClan.id())) {
            deny = DenyResult.of("trade.blocked");
        }
        boolean pact = plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), PactType.TRADE);
        Map<String, String> ph = Map.of(
                "multiplier", String.valueOf(cfgDouble("diplomacy.pacts.trade-cooldown-multiplier", 0.5)),
                "score", String.valueOf(cfgInt("diplomacy.events.trade-completed", 3)),
                "cap", String.valueOf(cfgInt("diplomacy.events.trade-daily-cap", 9)));
        return actionItem(player, ItemBuilder.HEAD_TRADE, pact ? "gui.diplomacy.act.trade-with-pact" : "gui.diplomacy.act.trade", ph, deny, pact);
    }

    private ItemStack buildImproveItem(Player player, Clan sourceClan, Clan targetClan) {
        long cost = plugin.getConfig().getLong("diplomacy.actions.improve-cost", 500L);
        DenyResult deny = firstDeny(
                commonDeny(player, sourceClan, targetClan, ClanPermission.DIPLOMACY),
                matrixDeny(DiplomacyActions.Action.IMPROVE, sourceClan, targetClan),
                cooldownDeny(sourceClan, targetClan, "improve"),
                sourceClan.chestMoney() < cost ? DenyResult.of("diplomacy.improve.not-enough-money") : null);
        Map<String, String> ph = Map.of(
                "cost", String.valueOf(cost),
                "score", String.valueOf(cfgInt("diplomacy.actions.improve-score", 5)),
                "hours", String.valueOf(plugin.getConfig().getLong("diplomacy.actions.improve-cooldown-hours", 6L)));
        return actionItem(player, ItemBuilder.HEAD_RELATION_CORDIAL, "gui.diplomacy.act.improve", ph, deny, false);
    }

    // --- Pacts (row 3) ---

    private ItemStack buildPactItem(Player player, Clan sourceClan, Clan targetClan, PactType type, String key, String head) {
        var manager = plugin.getDiplomacyManager();
        Map<String, String> ph = new HashMap<>();
        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        ph.put("min", String.valueOf(manager.minScore(type)));
        ph.put("bonus", String.valueOf(cfgInt("diplomacy.events.pact-signed." + typeKey, switch (type) {
            case NAP -> 5;
            case TRADE -> 8;
            case ALLIANCE -> 10;
        })));
        ph.put("penalty", String.valueOf(Math.abs(cfgInt("diplomacy.pacts." + typeKey + "-break-penalty", switch (type) {
            case NAP -> -15;
            case TRADE -> -10;
            case ALLIANCE -> -40;
        }))));
        ph.put("margin", String.valueOf(manager.minScore(type) - cfgInt("diplomacy.pacts.auto-break-margin", 20)));
        ph.put("multiplier", String.valueOf(cfgDouble("diplomacy.pacts.trade-cooldown-multiplier", 0.5)));

        String base = "gui.diplomacy.act." + key;
        DenyResult perm = commonDeny(player, sourceClan, targetClan, ClanPermission.DIPLOMACY);
        if (manager.hasPact(sourceClan.id(), targetClan.id(), type)) {
            return actionItem(player, head, base + "-active", ph, perm, true);
        }
        if (manager.hasPendingPactOffer(targetClan.id(), sourceClan.id(), type)) {
            return actionItem(player, head, base + "-incoming", ph, firstDeny(perm, matrixDeny(pactAction(type), sourceClan, targetClan)), true);
        }
        if (manager.hasPendingPactOffer(sourceClan.id(), targetClan.id(), type)) {
            return actionItem(player, head, base + "-pending", ph, perm, true);
        }
        return actionItem(player, head, base, ph,
                firstDeny(perm, conflictDeny(sourceClan, targetClan), matrixDeny(pactAction(type), sourceClan, targetClan)), false);
    }

    private static DiplomacyActions.Action pactAction(PactType type) {
        return switch (type) {
            case NAP -> DiplomacyActions.Action.NAP;
            case TRADE -> DiplomacyActions.Action.TRADE_PACT;
            case ALLIANCE -> DiplomacyActions.Action.ALLIANCE;
        };
    }

    // --- Pressure (row 4) ---

    private ItemStack buildInsultItem(Player player, Clan sourceClan, Clan targetClan) {
        DenyResult deny = firstDeny(
                commonDeny(player, sourceClan, targetClan, ClanPermission.DIPLOMACY),
                matrixDeny(DiplomacyActions.Action.INSULT, sourceClan, targetClan),
                cooldownDeny(sourceClan, targetClan, "insult"));
        Map<String, String> ph = Map.of(
                "score", String.valueOf(Math.abs(cfgInt("diplomacy.actions.insult-score", -12))),
                "hours", String.valueOf(plugin.getConfig().getLong("diplomacy.actions.insult-cooldown-hours", 6L)));
        return actionItem(player, ItemBuilder.HEAD_MSG, "gui.diplomacy.act.insult", ph, deny, false);
    }

    private ItemStack buildEmbargoItem(Player player, Clan sourceClan, Clan targetClan) {
        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id());
        DenyResult perm = commonDeny(player, sourceClan, targetClan, ClanPermission.DIPLOMACY);
        Map<String, String> ph = Map.of(
                "score", String.valueOf(Math.abs(cfgInt("diplomacy.events.embargo-declared", -10))),
                "back", String.valueOf(cfgInt("diplomacy.events.sanction-lifted", 5)));
        if (embargoed) {
            return actionItem(player, ItemBuilder.HEAD_EMBARGO, "gui.diplomacy.act.embargo-active", ph, perm, true);
        }
        DenyResult deny = firstDeny(perm, conflictDeny(sourceClan, targetClan),
                matrixDeny(DiplomacyActions.Action.EMBARGO, sourceClan, targetClan));
        return actionItem(player, ItemBuilder.HEAD_EMBARGO, "gui.diplomacy.act.embargo", ph, deny, false);
    }

    private ItemStack buildBlockadeItem(Player player, Clan sourceClan, Clan targetClan) {
        var manager = plugin.getDiplomacyManager();
        Map<String, String> ph = Map.of(
                "score", String.valueOf(Math.abs(cfgInt("diplomacy.events.blockade-declared", -20))),
                "back", String.valueOf(cfgInt("diplomacy.events.sanction-lifted", 5)),
                "multiplier", String.valueOf(cfgDouble("influence.blockade-multiplier", 1.5)));
        if (manager.isBlockading(targetClan.id(), sourceClan.id())) {
            return actionItem(player, ItemBuilder.HEAD_BLOCKADE, "gui.diplomacy.act.blockade-blocked", ph, null, false);
        }
        boolean leader = sourceClan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.LEADER).orElse(false);
        DenyResult perm = firstDeny(commonDeny(player, sourceClan, targetClan, ClanPermission.DIPLOMACY),
                leader ? null : DenyResult.of("diplomacy.blockade.leader-only"));
        if (manager.isBlockading(sourceClan.id(), targetClan.id())) {
            return actionItem(player, ItemBuilder.HEAD_BLOCKADE, "gui.diplomacy.act.blockade-active", ph, perm, true);
        }
        DenyResult deny = firstDeny(perm, conflictDeny(sourceClan, targetClan),
                matrixDeny(DiplomacyActions.Action.BLOCKADE, sourceClan, targetClan),
                plugin.getModifierManager().hasGuildShield(targetClan.id()) ? DenyResult.of("deny.guild-shield.active") : null,
                manager.canBlockade(sourceClan, targetClan) ? null : DenyResult.of("diplomacy.blockade.insufficient-influence"));
        return actionItem(player, ItemBuilder.HEAD_BLOCKADE, "gui.diplomacy.act.blockade", ph, deny, false);
    }

    private ItemStack buildConflictItem(Player player, Clan sourceClan, Clan targetClan) {
        if (plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id())) {
            String type = "Конфликт";
            if (plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id())) {
                type = "Война";
            } else if (plugin.getSiegeManager().isInSiege(sourceClan.id()) && plugin.getSiegeManager().isInSiege(targetClan.id())) {
                type = "Осада";
            } else if (plugin.getRaidManager().isInRaid(sourceClan.id()) && plugin.getRaidManager().isInRaid(targetClan.id())) {
                type = "Рейд";
            }
            return actionItem(player, ItemBuilder.HEAD_ACTIVE_WAR, "gui.diplomacy.act.conflict-active", Map.of("type", type), null, true);
        }
        DenyResult deny = matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan);
        return actionItem(player, ItemBuilder.HEAD_ACTIVE_WAR, "gui.diplomacy.act.conflict", Map.of(), deny, false);
    }

    // --- Clicks ---

    public void handleInventoryClick(Player player, Clan targetClan, int slot) {
        handleInventoryClick(player, targetClan, slot, false);
    }

    public void handleInventoryClick(Player player, Clan targetClan, int slot, boolean rightClick) {
        Optional<Clan> sourceClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (sourceClanOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Clan sourceClan = sourceClanOpt.get();

        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_BACK -> plugin.getGuiManager().openDiplomacySelect(player, sourceClan);
            case SLOT_GIFT -> handleGiftClick(player, sourceClan, targetClan);
            case SLOT_TRADE -> handleTradeClick(player, sourceClan, targetClan);
            case SLOT_IMPROVE -> refresh(player, sourceClan, targetClan,
                    plugin.getDiplomacyManager().improveRelationsAsync(player, sourceClan, targetClan));
            case SLOT_NAP -> handlePactClick(player, sourceClan, targetClan, PactType.NAP);
            case SLOT_TRADE_PACT -> handlePactClick(player, sourceClan, targetClan, PactType.TRADE);
            case SLOT_ALLIANCE -> handlePactClick(player, sourceClan, targetClan, PactType.ALLIANCE);
            case SLOT_INSULT -> refresh(player, sourceClan, targetClan,
                    plugin.getDiplomacyManager().insultClanAsync(player, sourceClan, targetClan));
            case SLOT_EMBARGO -> handleEmbargoToggle(player, sourceClan, targetClan);
            case SLOT_BLOCKADE -> handleBlockadeToggle(player, sourceClan, targetClan);
            case SLOT_CONFLICT -> handleConflictClick(player, sourceClan, targetClan);
            default -> { }
        }
    }

    /** Re-opens the screen after the action finished (so every button reflects the new score) or reports the failure. */
    private void refresh(Player player, Clan sourceClan, Clan targetClan, java.util.concurrent.CompletableFuture<Void> action) {
        action.thenRun(() -> plugin.runSync(() -> open(player, sourceClan, targetClan)))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void reject(Player player, DenyResult deny) {
        plugin.getMessages().send(player, deny.key(), deny.placeholders());
    }

    private void handleTradeClick(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.isRecognized() || !targetClan.isRecognized()) {
            plugin.getMessages().send(player, "deny.diplomacy.need-recognized");
            return;
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.TRADE)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        DenyResult deny = matrixDeny(DiplomacyActions.Action.TRADE, sourceClan, targetClan);
        if (deny == null && plugin.getClanTradeManager().tradeBlocked(sourceClan.id(), targetClan.id())) {
            deny = DenyResult.of("trade.blocked");
        }
        if (deny != null) {
            reject(player, deny);
            return;
        }
        player.closeInventory();
        plugin.getClanTradeManager().proposeTradeAsync(sourceClan, player.getUniqueId(), targetClan)
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void handleGiftClick(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.isRecognized()) {
            plugin.getMessages().send(player, "deny.gift.sender-not-recognized");
            return;
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        if (!sourceClan.hasCapital() || !targetClan.hasCapital()) {
            plugin.getMessages().send(player, "clan.no-territory");
            return;
        }
        DenyResult deny = matrixDeny(DiplomacyActions.Action.GIFT, sourceClan, targetClan);
        if (deny != null) {
            reject(player, deny);
            return;
        }
        plugin.getGuiManager().openGiftMenu(player, sourceClan, targetClan);
    }

    private void handleConflictClick(Player player, Clan sourceClan, Clan targetClan) {
        if (plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id())) {
            handlePeace(player, sourceClan, targetClan);
            return;
        }
        DenyResult deny = matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan);
        if (deny != null) {
            reject(player, deny);
            return;
        }
        plugin.getGuiManager().openConflict(player, sourceClan, targetClan, this);
    }

    /** Offer / accept / withdraw happen in one click; breaking an active pact costs relations, so it is confirmed first. */
    private void handlePactClick(Player player, Clan sourceClan, Clan targetClan, PactType type) {
        var manager = plugin.getDiplomacyManager();
        if (!manager.hasPact(sourceClan.id(), targetClan.id(), type)) {
            refresh(player, sourceClan, targetClan, manager.proposeOrAcceptPactAsync(player, sourceClan, targetClan, type));
            return;
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        int penalty = Math.abs(cfgInt("diplomacy.pacts." + typeKey + "-break-penalty", switch (type) {
            case NAP -> -15;
            case TRADE -> -10;
            case ALLIANCE -> -40;
        }));
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.break-pact.title", Map.of("type", type.displayName(),
                        "tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                plugin.getMessages().component("gui.confirm.break-pact.lore", Map.of("penalty", String.valueOf(penalty)), player),
                () -> refresh(player, sourceClan, targetClan, manager.cancelOrBreakPactAsync(player, sourceClan, targetClan, type)),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private void handleEmbargoToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id());
        refresh(player, sourceClan, targetClan, embargoed
                ? plugin.getDiplomacyManager().cancelEmbargoAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareEmbargoAsync(sourceClan, player.getUniqueId(), targetClan));
    }

    private void handleBlockadeToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean blockading = plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetClan.id());
        refresh(player, sourceClan, targetClan, blockading
                ? plugin.getDiplomacyManager().cancelBlockadeAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareBlockadeAsync(sourceClan, player.getUniqueId(), targetClan));
    }

    private void handlePeace(Player player, Clan sourceClan, Clan targetClan) {
        plugin.getPeaceService().proposeOrAccept(player, sourceClan, targetClan);
    }

    public record DenyResult(String key, Map<String, String> placeholders) {
        public static DenyResult of(String key) {
            return new DenyResult(key, Map.of());
        }
        public static DenyResult of(String key, Map<String, String> placeholders) {
            return new DenyResult(key, placeholders);
        }
    }

    private int countOnline(Clan clan) {
        int count = 0;
        for (UUID memberId : clan.members().keySet()) {
            if (Bukkit.getPlayer(memberId) != null) count++;
        }
        return count;
    }

    public Optional<DenyResult> checkWarDeny(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            return Optional.of(DenyResult.of("deny.no-permission"));
        }
        if (sourceClan.id().equals(targetClan.id())) {
            return Optional.of(DenyResult.of("war.cannot-target-self"));
        }
        if (sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY) {
            return Optional.of(DenyResult.of("deny.war.cannot-declare-ally"));
        }
        if (plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), PactType.NAP)) {
            return Optional.of(DenyResult.of("deny.nap.active"));
        }
        DenyResult tierDeny = matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan);
        if (tierDeny != null) {
            return Optional.of(tierDeny);
        }
        if (plugin.getModifierManager().hasGuildShield(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.guild-shield.active"));
        }
        if (plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id())) {
            return Optional.of(DenyResult.of("deny.war.already-at-war"));
        }
        if (plugin.getWarManager().isAtWar(sourceClan.id()) || plugin.getWarManager().isAtWar(targetClan.id())
                || plugin.getSiegeManager().isInSiege(sourceClan.id()) || plugin.getSiegeManager().isInSiege(targetClan.id())
                || plugin.getRaidManager().isInRaid(sourceClan.id()) || plugin.getRaidManager().isInRaid(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.war.already-in-conflict"));
        }
        if (plugin.getWarManager().activeWarsCount() >= plugin.getConfig().getInt("war.max-concurrent", 3)) {
            return Optional.of(DenyResult.of("deny.war.max-wars"));
        }
        if (!sourceClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.war.attacker-no-capital"));
        }
        if (!targetClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.war.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        }
        if (resolveContestedTerritory(player, targetClan).isEmpty()) {
            return Optional.of(DenyResult.of("deny.war.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.war", true);
        if (cbRequired && !plugin.getClanManager().getClanItemFactory().hasCasusBelli(player, targetClan.id(), "WAR")) {
            return Optional.of(DenyResult.of("deny.war.no-casus"));
        }
        int minOnline = plugin.getConfig().getInt("war.min-online", 3);
        int atkOnline = countOnline(sourceClan);
        int defOnline = countOnline(targetClan);
        if (atkOnline < minOnline || defOnline < minOnline) {
            return Optional.of(DenyResult.of("deny.war.not-enough-online",
                    Map.of("min", String.valueOf(minOnline), "current", String.valueOf(atkOnline < minOnline ? atkOnline : defOnline))));
        }
        boolean defLeaderOrGuardian = targetClan.members().values().stream()
                .filter(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .anyMatch(m -> Bukkit.getPlayer(m.playerId()) != null);
        if (!defLeaderOrGuardian) {
            return Optional.of(DenyResult.of("deny.war.defender-no-leader"));
        }
        long warCooldown = plugin.getWarManager().getCooldownRemaining(sourceClan.id(), targetClan.id());
        if (warCooldown > 0) {
            return Optional.of(DenyResult.of("deny.war.cooldown",
                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "time", me.lovelace.loveclans.util.TimeUtil.formatDuration(warCooldown * 1000L))));
        }
        return Optional.empty();
    }

    public Optional<DenyResult> checkSiegeDeny(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            return Optional.of(DenyResult.of("deny.no-permission"));
        }
        if (sourceClan.id().equals(targetClan.id())) {
            return Optional.of(DenyResult.of("war.cannot-target-self"));
        }
        if (sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY) {
            return Optional.of(DenyResult.of("deny.war.cannot-declare-ally"));
        }
        if (plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), PactType.NAP)) {
            return Optional.of(DenyResult.of("deny.nap.active"));
        }
        DenyResult tierDeny = matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan);
        if (tierDeny != null) {
            return Optional.of(tierDeny);
        }
        if (plugin.getModifierManager().hasGuildShield(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.guild-shield.active"));
        }
        if (plugin.getSiegeManager().isInSiege(sourceClan.id()) || plugin.getSiegeManager().isInSiege(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.siege.already-in-siege"));
        }
        if (plugin.getWarManager().isAtWar(sourceClan.id()) || plugin.getWarManager().isAtWar(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.siege.war-in-progress"));
        }
        if (plugin.getRaidManager().isInRaid(sourceClan.id()) || plugin.getRaidManager().isInRaid(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.raid.already-in-raid"));
        }
        if (plugin.getSiegeManager().activeSiegesCount() >= plugin.getConfig().getInt("siege.max-concurrent", 3)) {
            return Optional.of(DenyResult.of("deny.siege.already-in-siege"));
        }
        if (!sourceClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.siege.attacker-no-capital"));
        }
        if (!targetClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.siege.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        }
        if (resolveContestedTerritory(player, targetClan).isEmpty()) {
            return Optional.of(DenyResult.of("deny.siege.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        }
        int minAtkLevel = plugin.getConfig().getInt("siege.min-attacker-clan-level", 5);
        int minDefLevel = plugin.getConfig().getInt("siege.min-defender-clan-level", 4);
        int maxGap = plugin.getConfig().getInt("siege.level-gap-max", 8);
        if (sourceClan.level() < minAtkLevel) {
            return Optional.of(DenyResult.of("deny.siege.attacker-level-too-low",
                    Map.of("min", String.valueOf(minAtkLevel), "level", String.valueOf(sourceClan.level()))));
        }
        if (targetClan.level() < minDefLevel) {
            return Optional.of(DenyResult.of("deny.siege.defender-level-too-low",
                    Map.of("min", String.valueOf(minDefLevel), "level", String.valueOf(targetClan.level()))));
        }
        if (sourceClan.level() - targetClan.level() > maxGap) {
            return Optional.of(DenyResult.of("deny.siege.level-gap-too-large", Map.of("max", String.valueOf(maxGap))));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.siege", true);
        if (cbRequired && !plugin.getClanManager().getClanItemFactory().hasCasusBelli(player, targetClan.id(), "SIEGE")) {
            return Optional.of(DenyResult.of("deny.siege.no-casus"));
        }
        int afk = plugin.getConfig().getInt("siege.afk-ignore-minutes", 15);
        int minAtkOnline = plugin.getConfig().getInt("siege.min-attacker-online", 2);
        int minDefOnline = plugin.getConfig().getInt("siege.min-defender-online", 3);
        int atkOnline = plugin.getSiegeManager().countActiveOnline(sourceClan, afk);
        int defOnline = plugin.getSiegeManager().countActiveOnline(targetClan, afk);
        if (atkOnline < minAtkOnline) {
            return Optional.of(DenyResult.of("deny.siege.not-enough-attackers",
                    Map.of("min", String.valueOf(minAtkOnline), "current", String.valueOf(atkOnline))));
        }
        if (defOnline < minDefOnline) {
            return Optional.of(DenyResult.of("deny.siege.not-enough-defenders",
                    Map.of("min", String.valueOf(minDefOnline), "current", String.valueOf(defOnline))));
        }
        long siegeCooldown = plugin.getSiegeManager().getCooldownRemaining(sourceClan.id(), targetClan.id());
        if (siegeCooldown > 0) {
            return Optional.of(DenyResult.of("deny.war.cooldown",
                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "time", me.lovelace.loveclans.util.TimeUtil.formatDuration(siegeCooldown * 1000L))));
        }
        long declareFee = plugin.getConfig().getLong("siege.declare-treasury-fee", 500L);
        if (declareFee > 0 && sourceClan.chestMoney() < declareFee) {
            return Optional.of(DenyResult.of("deny.siege.not-enough-treasury", Map.of("cost", String.valueOf(declareFee))));
        }
        return Optional.empty();
    }

    public Optional<DenyResult> checkRaidDeny(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            return Optional.of(DenyResult.of("deny.no-permission"));
        }
        if (sourceClan.id().equals(targetClan.id())) {
            return Optional.of(DenyResult.of("war.cannot-target-self"));
        }
        if (sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY) {
            return Optional.of(DenyResult.of("deny.war.cannot-declare-ally"));
        }
        if (plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), PactType.NAP)) {
            return Optional.of(DenyResult.of("deny.nap.active"));
        }
        DenyResult tierDeny = matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan);
        if (tierDeny != null) {
            return Optional.of(tierDeny);
        }
        if (plugin.getModifierManager().hasGuildShield(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.guild-shield.active"));
        }
        if (plugin.getRaidManager().isInRaid(sourceClan.id()) || plugin.getRaidManager().isInRaid(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.raid.already-in-raid"));
        }
        if (plugin.getClanManager().inAnyConflict(sourceClan.id()) || plugin.getClanManager().inAnyConflict(targetClan.id())) {
            return Optional.of(DenyResult.of("deny.raid.conflict-in-progress"));
        }
        if (!sourceClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.raid.attacker-no-capital"));
        }
        if (!targetClan.hasCapital()) {
            return Optional.of(DenyResult.of("deny.raid.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        }
        if (plugin.getModifierManager().hasPostRaidShield(targetClan.id())) {
            long shieldSec = plugin.getModifierManager().getPostRaidShieldRemaining(targetClan.id());
            return Optional.of(DenyResult.of("deny.raid.shield-active",
                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "time", me.lovelace.loveclans.util.TimeUtil.formatDuration(shieldSec * 1000L))));
        }
        long afk = plugin.getConfig().getLong("raid.afk-ignore-minutes", 15L);
        int minAtkOnline = plugin.getConfig().getInt("raid.min-attacker-online", 2);
        int maxDefOnline = plugin.getConfig().getInt("raid.max-defender-online", 2);
        int atkOnline = plugin.getRaidManager().countOnlineNonAfk(sourceClan, afk);
        int defOnline = plugin.getRaidManager().countOnlineNonAfk(targetClan, afk);
        if (atkOnline < minAtkOnline) {
            return Optional.of(DenyResult.of("deny.raid.not-enough-attackers",
                    Map.of("min", String.valueOf(minAtkOnline), "current", String.valueOf(atkOnline))));
        }
        if (defOnline > maxDefOnline) {
            return Optional.of(DenyResult.of("deny.raid.too-many-defenders",
                    Map.of("max", String.valueOf(maxDefOnline), "current", String.valueOf(defOnline))));
        }
        int maxAtkPerDay = plugin.getConfig().getInt("raid.max-raids-per-clan-per-day", 5);
        int maxDefPerDay = plugin.getConfig().getInt("raid.max-times-raided-per-day", 1);
        if (plugin.getRaidManager().countRaidsAsAttackerToday(sourceClan.id()) >= maxAtkPerDay) {
            return Optional.of(DenyResult.of("deny.raid.daily-attacker-limit"));
        }
        if (plugin.getRaidManager().countRaidsAsDefenderToday(targetClan.id()) >= maxDefPerDay) {
            return Optional.of(DenyResult.of("deny.raid.daily-defender-limit"));
        }
        long raidCooldown = plugin.getRaidManager().getCooldownRemaining(sourceClan.id(), targetClan.id());
        if (raidCooldown > 0) {
            return Optional.of(DenyResult.of("deny.raid.cooldown",
                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "time", me.lovelace.loveclans.util.TimeUtil.formatDuration(raidCooldown * 1000L))));
        }
        return Optional.empty();
    }

    public void handleWarDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<DenyResult> deny = checkWarDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            plugin.getMessages().sendDeny(player, deny.get().key(), deny.get().placeholders());
            return;
        }
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.getMessages().sendDeny(player, "deny.war.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
            return;
        }

        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.war", true);
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.war.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                cbRequired ? Component.text("§7Будет израсходован Casus Belli: Война") : Component.empty(),
                () -> {
                    if (cbRequired && !plugin.getClanManager().getClanItemFactory().hasCasusBelli(player, targetClan.id(), "WAR")) {
                        plugin.getMessages().sendDeny(player, "deny.war.no-casus", Map.of());
                        return;
                    }
                    me.lovelace.loveclans.util.CasusDeclaration.declare(plugin, player, cbRequired, targetClan.id(), "WAR",
                            () -> plugin.getWarManager().startWarAsync(sourceClan, targetClan, territory.get()));
                },
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    public void handleSiegeDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<DenyResult> deny = checkSiegeDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            plugin.getMessages().sendDeny(player, deny.get().key(), deny.get().placeholders());
            return;
        }
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.getMessages().sendDeny(player, "deny.siege.defender-no-capital", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
            return;
        }

        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.siege", true);
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.siege.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                cbRequired ? Component.text("§7Будет израсходован Casus Belli: Осада") : Component.empty(),
                () -> {
                    if (cbRequired && !plugin.getClanManager().getClanItemFactory().hasCasusBelli(player, targetClan.id(), "SIEGE")) {
                        plugin.getMessages().sendDeny(player, "deny.siege.no-casus", Map.of());
                        return;
                    }
                    me.lovelace.loveclans.util.CasusDeclaration.declare(plugin, player, cbRequired, targetClan.id(), "SIEGE",
                            () -> plugin.getSiegeManager().startSiegeAsync(sourceClan, targetClan, territory.get()));
                },
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    public void handleRaidDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<DenyResult> deny = checkRaidDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            plugin.getMessages().sendDeny(player, deny.get().key(), deny.get().placeholders());
            return;
        }
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.raid.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> plugin.getRaidManager().startRaidAsync(sourceClan, targetClan)
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private Optional<TerritoryKey> resolveContestedTerritory(Player player, Clan defender) {
        Optional<TerritoryKey> standing = defender.territories().stream()
                .filter(t -> plugin.getAdvancedClaimsHook().contains(t, player.getLocation()))
                .findFirst()
                .map(ClanTerritory::key);
        if (standing.isPresent()) {
            return standing;
        }
        return defender.getCapitalTerritory().map(ClanTerritory::key);
    }
}
