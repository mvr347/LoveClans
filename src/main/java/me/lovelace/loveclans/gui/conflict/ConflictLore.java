package me.lovelace.loveclans.gui.conflict;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.LoreBlocks;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
import me.lovelace.loveclans.util.TrendFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the items of the military-actions screen: one button per conflict type with what it is, how long it lasts, what
 * is at stake and a ✔/✘ checklist of requirements, plus the clan comparison header and the one-line summary button
 * shown in the diplomacy menu. All numbers are read from config so the text can never drift from the rules.
 */
public final class ConflictLore {
    private static final long AFK_MINUTES = 15L;

    private final LoveClansPlugin plugin;

    public ConflictLore(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private String headFor(ConflictType type) {
        return switch (type) {
            case WAR -> ItemBuilder.HEAD_ACTIVE_WAR;
            case SIEGE -> ItemBuilder.HEAD_CAPITAL;
            case RAID -> ItemBuilder.HEAD_CHEST;
        };
    }

    /** Button for one conflict type. The icon stays recognisable when blocked; only the name turns gray. */
    public ItemStack typeButton(Player player, ConflictType type, ConflictReadiness readiness, Clan targetClan) {
        var messages = plugin.getMessages();
        String base = "gui.conflict." + type.key();
        Map<String, String> ph = stakesPlaceholders(type);
        boolean ready = readiness.ready();

        ItemBuilder builder = ItemBuilder.head(headFor(type))
                .name(messages.component(base + (ready ? ".name" : ".name-blocked"), ph, player))
                .lore(messages.components(base + ".desc", ph, player))
                .lore(Component.empty())
                .lore(messages.component(base + ".timing", ph, player))
                .lore(Component.empty())
                .lore(messages.components(base + ".stakes", ph, player))
                .lore(Component.empty());

        if (readiness.hardBlock() == null) {
            builder.lore(messages.component("gui.conflict.req-title", player));
            for (ConflictReadiness.Requirement requirement : readiness.requirements()) {
                String key = "gui.conflict.req." + requirement.id() + (requirement.ok() ? ".ok" : ".fail");
                builder.lore(messages.component(key, requirement.labelPlaceholders(), player));
            }
            builder.lore(Component.empty());
        }
        LoreBlocks.Hint start = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, base + ".action", ph);
        if (ready) {
            builder.lore(LoreBlocks.clicks(messages, player, List.of(start)));
            builder.glow(true);
        } else {
            ConflictReadiness.Deny deny = readiness.firstDeny().orElseThrow();
            builder.lore(LoreBlocks.blocked(messages, player, "gui.style.status.unavailable", deny.key(), deny.placeholders(), start));
        }
        return builder.build();
    }

    private Map<String, String> stakesPlaceholders(ConflictType type) {
        var cfg = plugin.getConfig();
        long warExp = cfg.getLong("leveling.war-win-exp", 1200L);
        Map<String, String> ph = new HashMap<>();
        switch (type) {
            case WAR -> {
                ph.put("pre", String.valueOf(cfg.getInt("war.pre-start-minutes", 15)));
                ph.put("duration", String.valueOf(cfg.getInt("war.duration-minutes", 45)));
                ph.put("exp", String.valueOf(warExp));
                ph.put("daily", String.valueOf(cfg.getLong("war.reparations.daily-amount", 500L)));
                ph.put("days", String.valueOf(cfg.getInt("war.reparations.duration-days", 5)));
            }
            case SIEGE -> {
                ph.put("pre", String.valueOf(cfg.getInt("siege.pre-start-minutes", 15)));
                ph.put("duration", String.valueOf(cfg.getInt("siege.duration-minutes", 22)));
                ph.put("hold", String.valueOf(cfg.getInt("siege.camp-hold-minutes", 10)));
                ph.put("exp", String.valueOf(Math.round(warExp * cfg.getDouble("siege.win-exp-multiplier", 0.75))));
                ph.put("loot", trimmed(cfg.getDouble("siege.instant-loot.money-percent-base", 20.0)));
                ph.put("loot_cap", trimmed(cfg.getDouble("siege.instant-loot.money-percent-cap", 35.0)));
                ph.put("daily", String.valueOf(cfg.getLong("siege.reparations.daily-amount", 300L)));
                ph.put("fee", me.lovelace.loveclans.util.CoinFormat.format(cfg.getLong("siege.declare-treasury-fee", 500L)));
            }
            case RAID -> {
                ph.put("pre", String.valueOf(cfg.getInt("raid.pre-start-minutes", 5)));
                ph.put("duration", String.valueOf(cfg.getInt("raid.duration-minutes", 12)));
                ph.put("money", trimmed(cfg.getDouble("raid.loot-money-percent", 40)));
                ph.put("items", trimmed(cfg.getDouble("raid.loot-item-slot-percent", 40)));
                ph.put("shield", String.valueOf(cfg.getInt("raid.post-raid-shield-hours", 12)));
            }
        }
        return ph;
    }

    private static String trimmed(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Header: our clan against theirs, with ▲/▼ showing who is ahead. */
    public ItemStack compareItem(Player player, Clan sourceClan, Clan targetClan) {
        var messages = plugin.getMessages();
        Map<String, String> ph = new HashMap<>();
        ph.put("tag", targetClan.tag());
        ph.put("color", targetClan.tagColor());
        long ourPower = sourceClan.influence();
        long theirPower = targetClan.influence();
        ph.put("our_influence", String.valueOf(ourPower));
        ph.put("their_influence", String.valueOf(theirPower));
        ph.put("arrow_influence", TrendFormat.compare(ourPower, theirPower));
        ph.put("ratio_influence", TrendFormat.ratio(ourPower, theirPower));
        ph.put("our_level", String.valueOf(sourceClan.level()));
        ph.put("their_level", String.valueOf(targetClan.level()));
        ph.put("arrow_level", TrendFormat.compare(sourceClan.level(), targetClan.level()));
        int ourOnline = plugin.getRaidManager().countOnlineNonAfk(sourceClan, AFK_MINUTES);
        int theirOnline = plugin.getRaidManager().countOnlineNonAfk(targetClan, AFK_MINUTES);
        ph.put("our_online", String.valueOf(ourOnline));
        ph.put("their_online", String.valueOf(theirOnline));
        ph.put("arrow_online", TrendFormat.compare(ourOnline, theirOnline));
        ph.put("our_spirit", String.valueOf(sourceClan.spirit().level()));
        ph.put("their_spirit", String.valueOf(targetClan.spirit().level()));
        ph.put("arrow_spirit", TrendFormat.compare(sourceClan.spirit().level(), targetClan.spirit().level()));
        long ourMoney = sourceClan.chestMoney();
        long theirMoney = targetClan.chestMoney();
        ph.put("arrow_money", TrendFormat.compare(ourMoney, theirMoney));
        ph.put("money_verdict", messages.raw("gui.conflict.compare.verdict."
                + (ourMoney > theirMoney ? "richer" : ourMoney < theirMoney ? "poorer" : "equal")));
        return ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                .name(messages.component("gui.conflict.compare.name", ph, player))
                .lore(messages.components("gui.conflict.compare.lore", ph, player))
                .build();
    }

    /** One-line-per-type summary for the diplomacy menu. Identical hard blocks collapse into a single reason. */
    public ItemStack summaryButton(Player player, Map<ConflictType, ConflictReadiness> readiness, Clan targetClan) {
        var messages = plugin.getMessages();
        Map<String, String> ph = Map.of("tag", targetClan.tag(), "color", targetClan.tagColor());
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_ACTIVE_WAR)
                .name(messages.component("gui.conflict.summary.name", ph, player))
                .lore(messages.components("gui.conflict.summary.lore", ph, player));

        List<ConflictReadiness.Deny> denies = new ArrayList<>();
        for (ConflictType type : ConflictType.values()) {
            readiness.get(type).firstDeny().ifPresent(denies::add);
        }
        boolean sharedBlock = denies.size() == ConflictType.values().length
                && denies.stream().allMatch(d -> d.key().equals(denies.get(0).key()));
        LoreBlocks.Hint choose = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.conflict.summary.action");
        if (sharedBlock) {
            builder.lore(LoreBlocks.blocked(messages, player, "gui.style.status.unavailable",
                    denies.get(0).key(), denies.get(0).placeholders(), choose));
        } else {
            for (ConflictType type : ConflictType.values()) {
                Map<String, String> typePh = Map.of("type", messages.raw("gui.conflict." + type.key() + ".short"));
                Optional<ConflictReadiness.Deny> deny = readiness.get(type).firstDeny();
                if (deny.isEmpty()) {
                    builder.lore(messages.component("gui.conflict.summary.line-ok", typePh, player));
                    continue;
                }
                builder.lore(messages.component("gui.conflict.summary.line-fail", typePh, player));
                List<Component> reason = messages.denyLore(deny.get().key(), deny.get().placeholders(), player);
                if (!reason.isEmpty()) {
                    builder.lore(Component.text("   ").append(reason.get(0)));
                }
            }
        }
        if (!sharedBlock) {
            builder.lore(Component.empty());
            builder.lore(LoreBlocks.clicks(messages, player, List.of(choose)));
        }
        return builder.build();
    }

    /** Replaces the summary while a conflict with this clan is running: type, score and time left, click offers peace. */
    public ItemStack activeButton(Player player, Clan sourceClan, Clan targetClan) {
        var messages = plugin.getMessages();
        ConflictType type = activeType(sourceClan, targetClan);
        String typeName = type == null ? messages.raw("gui.conflict.summary.name")
                : messages.raw("gui.conflict." + type.key() + ".short");
        Map<String, String> ph = new HashMap<>();
        ph.put("type", typeName);
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_ACTIVE_WAR)
                .name(messages.component("gui.conflict.summary.active-name", ph, player))
                .lore(messages.components("gui.conflict.summary.active-lore", ph, player))
                .glow(true);
        if (type == ConflictType.WAR) {
            plugin.getWarManager().getActiveWar(sourceClan.id()).ifPresent(war -> {
                boolean attacker = war.attackerClanId().equals(sourceClan.id());
                int ours = attacker ? war.attackerScore() : war.defenderScore();
                int theirs = attacker ? war.defenderScore() : war.attackerScore();
                Map<String, String> scorePh = Map.of("our", String.valueOf(ours), "their", String.valueOf(theirs),
                        "trend", TrendFormat.delta(ours - theirs));
                builder.lore(Component.empty());
                builder.lore(messages.component("gui.conflict.summary.active-score", scorePh, player));
                long left = war.endsAt() - System.currentTimeMillis();
                if (left > 0) {
                    builder.lore(messages.component("gui.conflict.summary.active-time",
                            Map.of("time", TimeUtil.formatDuration(left)), player));
                }
            });
        }
        builder.lore(Component.empty());
        builder.lore(LoreBlocks.clicks(messages, player, List.of(
                LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.conflict.summary.active-action"))));
        return builder.build();
    }

    private ConflictType activeType(Clan sourceClan, Clan targetClan) {
        if (plugin.getWarManager().areAtWar(sourceClan.id(), targetClan.id())) return ConflictType.WAR;
        if (plugin.getSiegeManager().isInSiege(sourceClan.id()) && plugin.getSiegeManager().isInSiege(targetClan.id())) return ConflictType.SIEGE;
        if (plugin.getRaidManager().isInRaid(sourceClan.id()) && plugin.getRaidManager().isInRaid(targetClan.id())) return ConflictType.RAID;
        return null;
    }
}
