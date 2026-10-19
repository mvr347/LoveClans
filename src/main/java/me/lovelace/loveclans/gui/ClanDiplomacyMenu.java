package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.conflict.ConflictLore;
import me.lovelace.loveclans.gui.conflict.ConflictReadiness;
import me.lovelace.loveclans.gui.conflict.ConflictType;
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
 * Layout: slot 4 shows the relation; below it the groups warm-up (gift, trade, gesture), pacts and pressure (insult,
 * embargo, blockade, conflict). Only buttons that make sense at the current tier are drawn, centered in their row; the
 * rows of an empty group collapse. Grey buttons are for temporary reasons only (rights, cooldown, money).
 */
public final class ClanDiplomacyMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_STATE = 4;

    /** First slots of the three content rows (header ends at 17, footer starts at 45). */
    private static final int[] ROW_STARTS = {18, 27, 36};

    /** Buttons by group: warming actions, pacts, pressure. Hidden ones leave no gap, see {@link #layout}. */
    private enum Button { GIFT, TRADE, IMPROVE, NAP, TRADE_PACT, ALLIANCE, INSULT, EMBARGO, BLOCKADE, CONFLICT }

    private static final Button[][] GROUPS = {
            {Button.GIFT, Button.TRADE, Button.IMPROVE},
            {Button.NAP, Button.TRADE_PACT, Button.ALLIANCE},
            {Button.INSULT, Button.EMBARGO, Button.BLOCKADE, Button.CONFLICT}
    };

    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;

    private final LoveClansPlugin plugin;
    /** Slot -> button of the screen each viewer has open, so a click is resolved against what was drawn. */
    private final Map<UUID, Map<Integer, Button>> openLayouts = new java.util.concurrent.ConcurrentHashMap<>();

    /** Drops the remembered layout of a player who closed the menu or left; it was never removed before. */
    public void forget(UUID playerId) {
        openLayouts.remove(playerId);
    }

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
                .lore(Component.empty())
                .lore(plugin.getMessages().component(recBadgeKey, player))
                .lore(publicEffects(player, sourceClan, targetClan))
                .build());

        inventory.setItem(SLOT_STATE, buildStateItem(player, sourceClan, targetClan, tier));

        Map<Integer, Button> layout = layout(player, sourceClan, targetClan);
        layout.forEach((slot, button) -> inventory.setItem(slot, buildItem(button, player, sourceClan, targetClan)));
        openLayouts.put(player.getUniqueId(), layout);

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.diplomacy.select-other.name", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    /** Shields, sanctions and running conflicts of the target that the viewer should know before acting. */
    private List<Component> publicEffects(Player player, Clan sourceClan, Clan targetClan) {
        var mods = plugin.getModifierManager();
        var diplomacy = plugin.getDiplomacyManager();
        var entries = me.lovelace.loveclans.manager.diplomacy.PublicModifiers.of(
                new me.lovelace.loveclans.manager.diplomacy.PublicModifiers.Snapshot(
                        plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id()),
                        plugin.getSiegeManager().areInSiege(sourceClan.id(), targetClan.id()),
                        plugin.getRaidManager().areInRaid(sourceClan.id(), targetClan.id()),
                        mods.hasGuildShield(targetClan.id()) ? mods.getGuildShieldRemaining(targetClan.id()) / 1000L : 0L,
                        mods.hasPostRaidShield(targetClan.id()) ? mods.getPostRaidShieldRemaining(targetClan.id()) / 1000L : 0L,
                        diplomacy.isEmbargoed(sourceClan.id(), targetClan.id()),
                        diplomacy.isEmbargoed(targetClan.id(), sourceClan.id()),
                        diplomacy.isBlockading(sourceClan.id(), targetClan.id()),
                        diplomacy.isBlockading(targetClan.id(), sourceClan.id()),
                        true));
        List<Component> lines = new ArrayList<>();
        if (!entries.isEmpty()) {
            lines.add(Component.empty());
            lines.add(plugin.getMessages().component("gui.diplomacy.public.title", player));
            for (var entry : entries) {
                lines.add(plugin.getMessages().component(entry.kind().langKey(),
                        Map.of("time", me.lovelace.loveclans.util.TimeUtil.formatDuration(entry.seconds() * 1000L)), player));
            }
        }
        return lines;
    }

    private String tierTitle(DiplomacyTier tier) {
        return plugin.getMessages().raw("diplomacy.tier." + tier.name().toLowerCase(java.util.Locale.ROOT));
    }

    // --- Layout: only buttons that make sense for the relation, centered in their row ---

    /**
     * Groups stay on their own rows; inside a row the visible buttons are centered, and a group with nothing to show
     * disappears so the rows below move up. Side walls stay empty (gui_gen v2.1, item 8).
     */
    private Map<Integer, Button> layout(Player player, Clan sourceClan, Clan targetClan) {
        Map<Integer, Button> result = new java.util.LinkedHashMap<>();
        int row = 0;
        for (Button[] group : GROUPS) {
            List<Button> visible = new ArrayList<>();
            for (Button button : group) {
                if (isVisible(button, sourceClan, targetClan)) visible.add(button);
            }
            if (visible.isEmpty()) continue;
            int[] slots = GuiFrames.contentRowSlots(ROW_STARTS[row++], visible.size());
            for (int i = 0; i < slots.length; i++) {
                result.put(slots[i], visible.get(i));
            }
        }
        return result;
    }

    /**
     * Tier and pact rules decide relevance ({@link DiplomacyActions#relevant}); whatever is already running (a pact or
     * an offer, a sanction, a conflict) always stays visible so it can be accepted, withdrawn or lifted.
     */
    private boolean isVisible(Button button, Clan sourceClan, Clan targetClan) {
        var manager = plugin.getDiplomacyManager();
        DiplomacyActions.Context ctx = manager.contextOf(sourceClan, targetClan);
        int margin = cfgInt("diplomacy.ui.pact-hint-margin", 10);
        return switch (button) {
            case GIFT -> DiplomacyActions.relevant(DiplomacyActions.Action.GIFT, ctx, 0, margin);
            case TRADE -> DiplomacyActions.relevant(DiplomacyActions.Action.TRADE, ctx, 0, margin);
            case IMPROVE -> DiplomacyActions.relevant(DiplomacyActions.Action.IMPROVE, ctx, 0, margin);
            case INSULT -> DiplomacyActions.relevant(DiplomacyActions.Action.INSULT, ctx, 0, margin);
            case NAP -> pactVisible(manager, sourceClan, targetClan, PactType.NAP, ctx, margin);
            case TRADE_PACT -> pactVisible(manager, sourceClan, targetClan, PactType.TRADE, ctx, margin);
            case ALLIANCE -> pactVisible(manager, sourceClan, targetClan, PactType.ALLIANCE, ctx, margin);
            case EMBARGO -> manager.isEmbargoed(sourceClan.id(), targetClan.id())
                    || DiplomacyActions.relevant(DiplomacyActions.Action.EMBARGO, ctx, 0, margin);
            case BLOCKADE -> manager.isBlockading(sourceClan.id(), targetClan.id())
                    || manager.isBlockading(targetClan.id(), sourceClan.id())
                    || DiplomacyActions.relevant(DiplomacyActions.Action.BLOCKADE, ctx, 0, margin);
            case CONFLICT -> plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id())
                    || DiplomacyActions.relevant(DiplomacyActions.Action.CONFLICT, ctx, 0, margin);
        };
    }

    private boolean pactVisible(me.lovelace.loveclans.manager.DiplomacyManager manager, Clan sourceClan, Clan targetClan,
                                PactType type, DiplomacyActions.Context ctx, int margin) {
        return manager.hasPact(sourceClan.id(), targetClan.id(), type)
                || manager.hasPendingPactOffer(targetClan.id(), sourceClan.id(), type)
                || manager.hasPendingPactOffer(sourceClan.id(), targetClan.id(), type)
                || DiplomacyActions.relevant(pactAction(type), ctx, manager.minScore(type), margin);
    }

    private ItemStack buildItem(Button button, Player player, Clan sourceClan, Clan targetClan) {
        return switch (button) {
            case GIFT -> buildGiftItem(player, sourceClan, targetClan);
            case TRADE -> buildTradeItem(player, sourceClan, targetClan);
            case IMPROVE -> buildImproveItem(player, sourceClan, targetClan);
            case NAP -> buildPactItem(player, sourceClan, targetClan, PactType.NAP, "nap", ItemBuilder.HEAD_ABILITY_SANCTUARY);
            case TRADE_PACT -> buildPactItem(player, sourceClan, targetClan, PactType.TRADE, "trade-pact", ItemBuilder.HEAD_CHEST_MONEY);
            case ALLIANCE -> buildPactItem(player, sourceClan, targetClan, PactType.ALLIANCE, "alliance", ItemBuilder.HEAD_RELATION_ALLIED);
            case INSULT -> buildInsultItem(player, sourceClan, targetClan);
            case EMBARGO -> buildEmbargoItem(player, sourceClan, targetClan);
            case BLOCKADE -> buildBlockadeItem(player, sourceClan, targetClan);
            case CONFLICT -> buildConflictItem(player, sourceClan, targetClan);
        };
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
        ph.put("trend", me.lovelace.loveclans.util.TrendFormat.delta(record.trend()));
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

    /** Standard 16-cell bar from -100 to +100 in the color of the current tier. */
    private String progressBar(int score, DiplomacyTier tier) {
        int span = ClanRelationRecord.MAX_SCORE - ClanRelationRecord.MIN_SCORE;
        String color = switch (tier) {
            case HOSTILE -> "red";
            case UNFRIENDLY -> "gold";
            case NEUTRAL -> "gray";
            case CORDIAL -> "green";
            case FRIENDLY -> "dark_green";
            case ALLIED -> "aqua";
        };
        return me.lovelace.loveclans.util.LoreBar.bar(score - ClanRelationRecord.MIN_SCORE, span, color);
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

    /**
     * Active button, or a grey one that keeps the description and states why it is unavailable. Buttons that have
     * an {@code .action} text get the standard click block (or its blocked variant); informational ones do not.
     */
    private ItemStack actionItem(Player player, String head, String base, Map<String, String> ph, DenyResult deny, boolean glow) {
        var messages = plugin.getMessages();
        ItemBuilder builder = ItemBuilder.head(deny == null ? head : ItemBuilder.HEAD_INACTIVE)
                .name(deny == null ? messages.component(base + ".name", ph, player)
                        : LoreBlocks.disabledName(messages, player, base + ".name", ph))
                .lore(messages.components(base + ".lore", ph, player));
        boolean clickable = messages.has(base + ".action");
        LoreBlocks.Hint hint = clickable ? LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, base + ".action", ph) : null;
        if (deny != null) {
            Map<String, String> merged = new HashMap<>(ph);
            merged.putAll(deny.placeholders());
            builder.lore(Component.empty());
            builder.lore(LoreBlocks.blocked(messages, player, "gui.style.status.unavailable", deny.key(), merged, hint));
        } else {
            if (clickable) {
                builder.lore(Component.empty());
                builder.lore(LoreBlocks.clicks(messages, player, List.of(hint)));
            }
            if (glow) builder.glow(true);
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
                "cost", me.lovelace.loveclans.util.CoinFormat.format(cost),
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
                blockadedByOther(sourceClan, targetClan) ? DenyResult.of("deny.blockade.already-blockaded-by-other") : null,
                manager.canBlockade(sourceClan, targetClan) ? null : DenyResult.of("diplomacy.blockade.insufficient-influence"));
        return actionItem(player, ItemBuilder.HEAD_BLOCKADE, "gui.diplomacy.act.blockade", ph, deny, false);
    }

    /** Mirrors the server-side exclusivity check, so the button is not drawn active just to be refused on click. */
    private boolean blockadedByOther(Clan sourceClan, Clan targetClan) {
        if (!plugin.getConfig().getBoolean("diplomacy.blockade-exclusive", true)) return false;
        Optional<UUID> current = plugin.getDiplomacyManager().blockaderOf(targetClan.id());
        return current.isPresent() && !current.get().equals(sourceClan.id());
    }

    private ItemStack buildConflictItem(Player player, Clan sourceClan, Clan targetClan) {
        ConflictLore lore = new ConflictLore(plugin);
        if (plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id())) {
            return lore.activeButton(player, sourceClan, targetClan);
        }
        Map<ConflictType, ConflictReadiness> readiness = new java.util.EnumMap<>(ConflictType.class);
        readiness.put(ConflictType.WAR, warReadiness(player, sourceClan, targetClan));
        readiness.put(ConflictType.SIEGE, siegeReadiness(player, sourceClan, targetClan));
        readiness.put(ConflictType.RAID, raidReadiness(player, sourceClan, targetClan));
        return lore.summaryButton(player, readiness, targetClan);
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

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openDiplomacySelect(player, sourceClan);
            return;
        }
        Map<Integer, Button> layout = openLayouts.get(player.getUniqueId());
        Button button = layout == null ? null : layout.get(slot);
        if (button == null) return;
        switch (button) {
            case GIFT -> handleGiftClick(player, sourceClan, targetClan);
            case TRADE -> handleTradeClick(player, sourceClan, targetClan);
            case IMPROVE -> refresh(player, sourceClan, targetClan,
                    plugin.getDiplomacyManager().improveRelationsAsync(player, sourceClan, targetClan));
            case NAP -> handlePactClick(player, sourceClan, targetClan, PactType.NAP);
            case TRADE_PACT -> handlePactClick(player, sourceClan, targetClan, PactType.TRADE);
            case ALLIANCE -> handlePactClick(player, sourceClan, targetClan, PactType.ALLIANCE);
            case INSULT -> refresh(player, sourceClan, targetClan,
                    plugin.getDiplomacyManager().insultClanAsync(player, sourceClan, targetClan));
            case EMBARGO -> handleEmbargoToggle(player, sourceClan, targetClan);
            case BLOCKADE -> handleBlockadeToggle(player, sourceClan, targetClan);
            case CONFLICT -> handleConflictClick(player, sourceClan, targetClan);
        }
    }

    /** Re-opens the screen after the action finished (so every button reflects the new score) or reports the failure. */
    private void refresh(Player player, Clan sourceClan, Clan targetClan, java.util.concurrent.CompletableFuture<Void> action) {
        refresh(player, sourceClan, targetClan, action, GuiSounds.Kind.SUCCESS);
    }

    /** {@code doneSound} is what the player hears once the action went through (a switch clicks, anything else rings). */
    private void refresh(Player player, Clan sourceClan, Clan targetClan, java.util.concurrent.CompletableFuture<Void> action,
                         GuiSounds.Kind doneSound) {
        action.thenRun(() -> plugin.runSync(() -> {
                    plugin.getSoundService().playGui(player, doneSound);
                    open(player, sourceClan, targetClan);
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void reject(Player player, DenyResult deny) {
        plugin.getMessages().sendDeny(player, deny.key(), deny.placeholders());
    }

    private void handleTradeClick(Player player, Clan sourceClan, Clan targetClan) {
        if (!sourceClan.isRecognized() || !targetClan.isRecognized()) {
            plugin.getMessages().sendDeny(player, "deny.diplomacy.need-recognized", Map.of());
            return;
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.TRADE)) {
            plugin.getMessages().sendDeny(player, "general.no-permission", Map.of());
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
            plugin.getMessages().sendDeny(player, "deny.gift.sender-not-recognized", Map.of());
            return;
        }
        if (!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY)) {
            plugin.getMessages().sendDeny(player, "general.no-permission", Map.of());
            return;
        }
        if (!sourceClan.hasCapital() || !targetClan.hasCapital()) {
            plugin.getMessages().sendDeny(player, "clan.no-territory", Map.of());
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
            plugin.getMessages().sendDeny(player, "general.no-permission", Map.of());
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
                plugin.getGuiManager().confirmEffects(player, "break-pact", Map.of("penalty", String.valueOf(penalty))),
                () -> refresh(player, sourceClan, targetClan, manager.cancelOrBreakPactAsync(player, sourceClan, targetClan, type)),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    private void handleEmbargoToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetClan.id());
        refresh(player, sourceClan, targetClan, embargoed
                ? plugin.getDiplomacyManager().cancelEmbargoAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareEmbargoAsync(sourceClan, player.getUniqueId(), targetClan),
                GuiSounds.Kind.TOGGLE);
    }

    private void handleBlockadeToggle(Player player, Clan sourceClan, Clan targetClan) {
        boolean blockading = plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetClan.id());
        refresh(player, sourceClan, targetClan, blockading
                ? plugin.getDiplomacyManager().cancelBlockadeAsync(sourceClan, player.getUniqueId(), targetClan)
                : plugin.getDiplomacyManager().declareBlockadeAsync(sourceClan, player.getUniqueId(), targetClan),
                GuiSounds.Kind.TOGGLE);
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

    // --- Military actions: one readiness model per type, shared by the lore and the server-side declaration ---

    private Map<String, String> targetPh(Clan targetClan) {
        return Map.of("tag", targetClan.tag(), "color", targetClan.tagColor());
    }

    private void blockMatrix(ConflictReadiness.Builder b, DenyResult deny) {
        if (deny != null) b.blockIf(true, deny.key(), deny.placeholders());
    }

    /** Shared leading blocks of war, siege and raid, in the order the server has always applied them. */
    private ConflictReadiness.Builder commonConflictBlocks(Player player, Clan sourceClan, Clan targetClan) {
        ConflictReadiness.Builder b = ConflictReadiness.builder();
        b.blockIf(!sourceClan.hasPermission(player.getUniqueId(), ClanPermission.DIPLOMACY), "deny.no-permission");
        b.blockIf(sourceClan.id().equals(targetClan.id()), "war.cannot-target-self");
        b.blockIf(sourceClan.relationTo(targetClan.id()) == DiplomacyRelation.ALLY, "deny.war.cannot-declare-ally");
        b.blockIf(plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetClan.id(), PactType.NAP), "deny.nap.active");
        blockMatrix(b, matrixDeny(DiplomacyActions.Action.CONFLICT, sourceClan, targetClan));
        b.blockIf(plugin.getModifierManager().hasGuildShield(targetClan.id()), "deny.guild-shield.active");
        return b;
    }

    private void cooldownRequirement(ConflictReadiness.Builder b, String denyKey, long remainingSeconds, Clan targetClan) {
        String time = me.lovelace.loveclans.util.TimeUtil.formatDuration(remainingSeconds * 1000L);
        Map<String, String> ph = new HashMap<>(targetPh(targetClan));
        ph.put("time", time);
        b.require("cooldown", remainingSeconds <= 0, ph, denyKey, ph);
    }

    private void casusRequirement(ConflictReadiness.Builder b, Player player, Clan targetClan, String type, String denyKey) {
        boolean required = plugin.getConfig().getBoolean("casus-belli.required-for." + type.toLowerCase(java.util.Locale.ROOT), true);
        boolean ok = !required || plugin.getClanManager().getClanItemFactory().hasCasusBelli(player, targetClan.id(), type);
        b.require("casus", ok, Map.of("type", plugin.getMessages().raw("gui.conflict." + type.toLowerCase(java.util.Locale.ROOT) + ".short")), denyKey, Map.of());
    }

    public ConflictReadiness warReadiness(Player player, Clan sourceClan, Clan targetClan) {
        var war = plugin.getWarManager();
        ConflictReadiness.Builder b = commonConflictBlocks(player, sourceClan, targetClan);
        b.blockIf(war.areAtWar(sourceClan.id(), targetClan.id()), "deny.war.already-at-war");
        b.blockIf(war.isAtWar(sourceClan.id()) || war.isAtWar(targetClan.id())
                || plugin.getSiegeManager().isInSiege(sourceClan.id()) || plugin.getSiegeManager().isInSiege(targetClan.id())
                || plugin.getRaidManager().isInRaid(sourceClan.id()) || plugin.getRaidManager().isInRaid(targetClan.id()),
                "deny.war.already-in-conflict");
        b.blockIf(war.activeWarsCount() >= plugin.getConfig().getInt("war.max-concurrent", 3), "deny.war.max-wars");

        Map<String, String> tp = targetPh(targetClan);
        b.require("cap-self", sourceClan.hasCapital(), "deny.war.attacker-no-capital");
        b.require("cap-target", targetClan.hasCapital() && resolveContestedTerritory(player, targetClan).isPresent(),
                tp, "deny.war.defender-no-capital", tp);
        casusRequirement(b, player, targetClan, "WAR", "deny.war.no-casus");

        int minOnline = plugin.getConfig().getInt("war.min-online", 3);
        int atkOnline = countOnline(sourceClan);
        int defOnline = countOnline(targetClan);
        Map<String, String> atkPh = Map.of("min", String.valueOf(minOnline), "current", String.valueOf(atkOnline));
        Map<String, String> defPh = Map.of("min", String.valueOf(minOnline), "current", String.valueOf(defOnline));
        b.require("online-self", atkOnline >= minOnline, atkPh, "deny.war.not-enough-online", atkPh);
        b.require("online-target", defOnline >= minOnline, defPh, "deny.war.not-enough-online", defPh);
        boolean defLeaderOrGuardian = targetClan.members().values().stream()
                .filter(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .anyMatch(m -> Bukkit.getPlayer(m.playerId()) != null);
        b.require("leader-target", defLeaderOrGuardian, "deny.war.defender-no-leader");
        cooldownRequirement(b, "deny.war.cooldown", war.getCooldownRemaining(sourceClan.id(), targetClan.id()), targetClan);
        return b.build();
    }

    public ConflictReadiness siegeReadiness(Player player, Clan sourceClan, Clan targetClan) {
        var siege = plugin.getSiegeManager();
        ConflictReadiness.Builder b = commonConflictBlocks(player, sourceClan, targetClan);
        b.blockIf(siege.isInSiege(sourceClan.id()) || siege.isInSiege(targetClan.id()), "deny.siege.already-in-siege");
        b.blockIf(plugin.getWarManager().isAtWar(sourceClan.id()) || plugin.getWarManager().isAtWar(targetClan.id()), "deny.siege.war-in-progress");
        b.blockIf(plugin.getRaidManager().isInRaid(sourceClan.id()) || plugin.getRaidManager().isInRaid(targetClan.id()), "deny.raid.already-in-raid");
        b.blockIf(siege.activeSiegesCount() >= plugin.getConfig().getInt("siege.max-concurrent", 3), "deny.siege.already-in-siege");

        Map<String, String> tp = targetPh(targetClan);
        b.require("cap-self", sourceClan.hasCapital(), "deny.siege.attacker-no-capital");
        b.require("cap-target", targetClan.hasCapital() && resolveContestedTerritory(player, targetClan).isPresent(),
                tp, "deny.siege.defender-no-capital", tp);
        int minAtkLevel = plugin.getConfig().getInt("siege.min-attacker-clan-level", 5);
        int minDefLevel = plugin.getConfig().getInt("siege.min-defender-clan-level", 4);
        int maxGap = plugin.getConfig().getInt("siege.level-gap-max", 8);
        Map<String, String> atkLevel = Map.of("min", String.valueOf(minAtkLevel), "level", String.valueOf(sourceClan.level()));
        Map<String, String> defLevel = Map.of("min", String.valueOf(minDefLevel), "level", String.valueOf(targetClan.level()));
        Map<String, String> gap = Map.of("max", String.valueOf(maxGap), "gap", String.valueOf(sourceClan.level() - targetClan.level()));
        b.require("level-self", sourceClan.level() >= minAtkLevel, atkLevel, "deny.siege.attacker-level-too-low", atkLevel);
        b.require("level-target", targetClan.level() >= minDefLevel, defLevel, "deny.siege.defender-level-too-low", defLevel);
        b.require("level-gap", sourceClan.level() - targetClan.level() <= maxGap, gap, "deny.siege.level-gap-too-large", gap);
        casusRequirement(b, player, targetClan, "SIEGE", "deny.siege.no-casus");

        int afk = plugin.getConfig().getInt("siege.afk-ignore-minutes", 15);
        int minAtkOnline = plugin.getConfig().getInt("siege.min-attacker-online", 2);
        int minDefOnline = plugin.getConfig().getInt("siege.min-defender-online", 3);
        int atkOnline = siege.countActiveOnline(sourceClan, afk);
        int defOnline = siege.countActiveOnline(targetClan, afk);
        Map<String, String> atkPh = Map.of("min", String.valueOf(minAtkOnline), "current", String.valueOf(atkOnline));
        Map<String, String> defPh = Map.of("min", String.valueOf(minDefOnline), "current", String.valueOf(defOnline));
        b.require("online-self", atkOnline >= minAtkOnline, atkPh, "deny.siege.not-enough-attackers", atkPh);
        b.require("online-target", defOnline >= minDefOnline, defPh, "deny.siege.not-enough-defenders", defPh);
        cooldownRequirement(b, "deny.war.cooldown", siege.getCooldownRemaining(sourceClan.id(), targetClan.id()), targetClan);
        long declareFee = plugin.getConfig().getLong("siege.declare-treasury-fee", 500L);
        Map<String, String> feePh = Map.of("cost", me.lovelace.loveclans.util.CoinFormat.format(declareFee), "have", me.lovelace.loveclans.util.CoinFormat.format(sourceClan.chestMoney()));
        b.require("fee", declareFee <= 0 || sourceClan.chestMoney() >= declareFee, feePh, "deny.siege.not-enough-treasury", feePh);
        return b.build();
    }

    public ConflictReadiness raidReadiness(Player player, Clan sourceClan, Clan targetClan) {
        var raid = plugin.getRaidManager();
        ConflictReadiness.Builder b = commonConflictBlocks(player, sourceClan, targetClan);
        b.blockIf(raid.isInRaid(sourceClan.id()) || raid.isInRaid(targetClan.id()), "deny.raid.already-in-raid");
        b.blockIf(plugin.getClanManager().inAnyConflict(sourceClan.id()) || plugin.getClanManager().inAnyConflict(targetClan.id()),
                "deny.raid.conflict-in-progress");

        Map<String, String> tp = targetPh(targetClan);
        b.require("cap-self", sourceClan.hasCapital(), "deny.raid.attacker-no-capital");
        b.require("cap-target", targetClan.hasCapital(), tp, "deny.raid.defender-no-capital", tp);
        boolean shielded = plugin.getModifierManager().hasPostRaidShield(targetClan.id());
        Map<String, String> shieldPh = new HashMap<>(tp);
        shieldPh.put("time", me.lovelace.loveclans.util.TimeUtil.formatDuration(
                shielded ? plugin.getModifierManager().getPostRaidShieldRemaining(targetClan.id()) : 0L));
        b.require("shield", !shielded, shieldPh, "deny.raid.shield-active", shieldPh);

        long afk = plugin.getConfig().getLong("raid.afk-ignore-minutes", 15L);
        int minAtkOnline = plugin.getConfig().getInt("raid.min-attacker-online", 2);
        int maxDefOnline = plugin.getConfig().getInt("raid.max-defender-online", 2);
        int atkOnline = raid.countOnlineNonAfk(sourceClan, afk);
        int defOnline = raid.countOnlineNonAfk(targetClan, afk);
        Map<String, String> atkPh = Map.of("min", String.valueOf(minAtkOnline), "current", String.valueOf(atkOnline));
        Map<String, String> defPh = Map.of("max", String.valueOf(maxDefOnline), "current", String.valueOf(defOnline));
        b.require("online-self", atkOnline >= minAtkOnline, atkPh, "deny.raid.not-enough-attackers", atkPh);
        b.require("online-target-max", defOnline <= maxDefOnline, defPh, "deny.raid.too-many-defenders", defPh);
        int maxAtkPerDay = plugin.getConfig().getInt("raid.max-raids-per-clan-per-day", 5);
        int maxDefPerDay = plugin.getConfig().getInt("raid.max-times-raided-per-day", 1);
        int atkToday = raid.countRaidsAsAttackerToday(sourceClan.id());
        int defToday = raid.countRaidsAsDefenderToday(targetClan.id());
        Map<String, String> atkDay = Map.of("used", String.valueOf(atkToday), "max", String.valueOf(maxAtkPerDay));
        Map<String, String> defDay = Map.of("used", String.valueOf(defToday), "max", String.valueOf(maxDefPerDay));
        b.require("daily-self", atkToday < maxAtkPerDay, atkDay, "deny.raid.daily-attacker-limit", atkDay);
        b.require("daily-target", defToday < maxDefPerDay, defDay, "deny.raid.daily-defender-limit", defDay);
        cooldownRequirement(b, "deny.raid.cooldown", raid.getCooldownRemaining(sourceClan.id(), targetClan.id()), targetClan);
        return b.build();
    }

    private Optional<DenyResult> toDeny(ConflictReadiness readiness) {
        return readiness.firstDeny().map(d -> DenyResult.of(d.key(), d.placeholders()));
    }

    public Optional<DenyResult> checkWarDeny(Player player, Clan sourceClan, Clan targetClan) {
        return toDeny(warReadiness(player, sourceClan, targetClan));
    }

    public Optional<DenyResult> checkSiegeDeny(Player player, Clan sourceClan, Clan targetClan) {
        return toDeny(siegeReadiness(player, sourceClan, targetClan));
    }

    public Optional<DenyResult> checkRaidDeny(Player player, Clan sourceClan, Clan targetClan) {
        return toDeny(raidReadiness(player, sourceClan, targetClan));
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
                conflictEffects(player, "war", targetClan, cbRequired, "gui.conflict.war.short"),
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
                conflictEffects(player, "siege", targetClan, cbRequired, "gui.conflict.siege.short"),
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
                plugin.getGuiManager().confirmEffects(player, "raid", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())),
                () -> plugin.getRaidManager().startRaidAsync(sourceClan, targetClan)
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, sourceClan, targetClan)));
    }

    /** What a war/siege declaration will do: the declaration itself, plus the spent casus belli when one is required. */
    private List<Component> conflictEffects(Player player, String kind, Clan targetClan, boolean casusRequired, String shortNameKey) {
        List<Component> effects = new java.util.ArrayList<>(plugin.getGuiManager().confirmEffects(player, kind,
                Map.of("tag", targetClan.tag(), "color", targetClan.tagColor())));
        if (casusRequired) {
            effects.add(plugin.getMessages().component("gui.conflict.confirm-casus",
                    Map.of("type", plugin.getMessages().raw(shortNameKey)), player));
        }
        return effects;
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
