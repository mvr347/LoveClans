package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.SpiritManager;
import me.lovelace.loveclans.manager.SpiritSettings;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanSpirit;
import me.lovelace.loveclans.model.spirit.SpiritBonus;
import me.lovelace.loveclans.model.spirit.SpiritBonusFormula;
import me.lovelace.loveclans.model.spirit.SpiritBonusFormula.Side;
import me.lovelace.loveclans.model.spirit.SpiritDecayPolicy;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Clan Spirit (gui_gen v2.1, 27 slots): header with the idea, then one work row with the three things that matter -
 * Power (level, progress, how fast an idle spirit fades), Mastery (the offence/defence axis) and the bonuses the two
 * produce together. No buttons live in the work zone besides these info items; Back and Close are in the footer.
 */
public final class ClanSpiritMenu implements InventoryHolder {
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    private static final int MAX_POWER = 10;
    private static final int AXIS_CELLS = 21;

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private Inventory inventory;

    public ClanSpiritMenu(LoveClansPlugin plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;
    }

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.spirit.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        GuiFrames.fillFrame27(inventory);

        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT)
                .name(plugin.getMessages().component("gui.spirit.header.name",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.spirit.header.lore", Map.of(), player))
                .build());

        int[] slots = GuiFrames.workRowSlots(3);
        inventory.setItem(slots[0], buildPowerItem(player));
        inventory.setItem(slots[1], buildMasteryItem(player));
        inventory.setItem(slots[2], buildBonusItem(player));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player)).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player)).build());
        player.openInventory(inventory);
    }

    private SpiritManager manager() {
        return plugin.getSpiritManager();
    }

    private ItemStack buildPowerItem(Player player) {
        var messages = plugin.getMessages();
        ClanSpirit spirit = clan.spirit();
        int level = spirit.level();
        Map<String, String> ph = new HashMap<>();
        ph.put("level", String.valueOf(level));
        ph.put("max", String.valueOf(MAX_POWER));

        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_SPIRIT_STAR)
                .name(messages.component(level >= MAX_POWER ? "gui.spirit.power.name-max" : "gui.spirit.power.name", ph, player));
        builder.lore(messages.components("gui.spirit.power.head", player));
        if (level >= MAX_POWER) {
            builder.lore(messages.components("gui.spirit.power.maximum", player));
        } else {
            long next = manager().getExpForNextLevel(level);
            ph.put("energy", String.valueOf(spirit.energy()));
            ph.put("next", String.valueOf(next));
            ph.put("bar", me.lovelace.loveclans.util.LoreBar.bar(spirit.energy(), next));
            builder.lore(messages.components("gui.spirit.power.progress", ph, player));
        }
        builder.lore(Component.empty());

        SpiritDecayPolicy.Tier tier = manager().activityTier(clan);
        SpiritSettings settings = manager().settings();
        SpiritDecayPolicy.TierConfig tierCfg = settings.decay().of(tier);
        Map<String, String> decay = Map.of(
                "tier", messages.raw("gui.spirit.power.tier." + tier.name().toLowerCase(Locale.ROOT)),
                "energy", trim(tierCfg.energyPercentPerDay()),
                "loss", trim(tierCfg.powerLossPerDay()));
        builder.lore(messages.component("gui.spirit.power.activity", decay, player));
        builder.lore(messages.component("gui.spirit.power.decay." + tier.name().toLowerCase(Locale.ROOT), decay, player));
        builder.lore(Component.empty());
        builder.lore(messages.components("gui.spirit.power.sources", Map.of(), player));
        return builder.build();
    }

    private ItemStack buildMasteryItem(Player player) {
        var messages = plugin.getMessages();
        ClanSpirit spirit = clan.spirit();
        SpiritSettings settings = manager().settings();
        Side side = manager().side(clan);
        Map<String, String> ph = new HashMap<>();
        ph.put("side", messages.raw("gui.spirit.side." + side.name().toLowerCase(Locale.ROOT)));
        ph.put("value", signed(spirit.mastery()));
        ph.put("axis", axis(spirit.mastery()));
        long today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        boolean sameDay = spirit.masteryDay() == today;
        ph.put("off", String.valueOf(sameDay ? spirit.masteryOffenseToday() : 0));
        ph.put("def", String.valueOf(sameDay ? spirit.masteryDefenseToday() : 0));
        ph.put("off_cap", String.valueOf(settings.masteryCaps().offense()));
        ph.put("def_cap", String.valueOf(settings.masteryCaps().defense()));

        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_RELATION_HOSTILE)
                .name(messages.component("gui.spirit.mastery.name", ph, player))
                .lore(messages.components("gui.spirit.mastery.lore", ph, player));
        if (manager().overloaded(clan)) {
            Map<String, String> over = Map.of(
                    "incoming", trim(settings.overloadIncomingPercent()),
                    "outgoing", trim(settings.overloadOutgoingPercent()));
            builder.lore(Component.empty());
            builder.lore(messages.component(side == Side.OFFENSE ? "gui.spirit.mastery.overload-offense"
                    : "gui.spirit.mastery.overload-defense", over, player));
        }
        builder.lore(Component.empty());
        builder.lore(messages.components("gui.spirit.mastery.sources", Map.of(), player));
        return builder.glow(manager().overloaded(clan)).build();
    }

    private ItemStack buildBonusItem(Player player) {
        var messages = plugin.getMessages();
        SpiritSettings settings = manager().settings();
        Side side = manager().side(clan);
        int step = manager().bonusStep(clan);
        int powerCap = (clan.spirit().level() + 1) / 2;
        int masteryCap = Math.abs(clan.spirit().mastery()) / 20;
        Map<String, String> ph = Map.of(
                "step", String.valueOf(step), "max", String.valueOf(SpiritBonusFormula.MAX_STEP),
                "power_cap", String.valueOf(Math.min(SpiritBonusFormula.MAX_STEP, powerCap)),
                "mastery_cap", String.valueOf(Math.min(SpiritBonusFormula.MAX_STEP, masteryCap)));

        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_EXPERIENCE)
                .name(messages.component("gui.spirit.bonuses.name", ph, player))
                .lore(messages.components("gui.spirit.bonuses.lore", ph, player));
        if (side == Side.NONE) {
            builder.lore(Component.empty());
            builder.lore(messages.component("gui.spirit.bonuses.neutral", player));
        }
        for (Side shown : side == Side.NONE ? new Side[]{Side.OFFENSE, Side.DEFENSE} : new Side[]{side}) {
            builder.lore(Component.empty());
            builder.lore(messages.component("gui.spirit.side-title." + shown.name().toLowerCase(Locale.ROOT), player));
            for (SpiritBonus bonus : SpiritBonus.of(shown)) {
                int useStep = shown == side ? step : 0;
                double now = settings.value(bonus, useStep);
                double next = settings.value(bonus, Math.min(SpiritBonusFormula.MAX_STEP, useStep + 1));
                String label = messages.raw("gui.spirit.bonus." + bonus.id() + ".label");
                String effect = messages.raw("gui.spirit.bonus." + bonus.id() + ".effect");
                if (now > 0) {
                    Map<String, String> line = new HashMap<>();
                    line.put("label", label);
                    line.put("effect", effect.replace("<value>", trim(now)));
                    line.put("next", next > now ? trim(next) : "");
                    builder.lore(messages.component(next > now ? "gui.spirit.bonuses.active-next" : "gui.spirit.bonuses.active", line, player));
                } else {
                    builder.lore(messages.component("gui.spirit.bonuses.locked",
                            Map.of("label", label, "step", String.valueOf(settings.unlockStep(bonus))), player));
                }
            }
        }
        if (manager().overloaded(clan)) {
            builder.lore(Component.empty());
            builder.lore(messages.component("gui.spirit.bonuses.overload", player));
        }
        builder.lore(Component.empty());
        builder.lore(messages.component("gui.spirit.bonuses.scope", player));
        return builder.build();
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    /** -100 (defence, aqua) on the left, +100 (offence, red) on the right, a white cell where the clan stands. */
    static String axis(int mastery) {
        int marker = (int) Math.round((mastery + 100) / 200.0 * (AXIS_CELLS - 1));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < AXIS_CELLS; i++) {
            String color = i == marker ? "white" : i < AXIS_CELLS / 2 ? "aqua" : i > AXIS_CELLS / 2 ? "red" : "gray";
            boolean lit = i == marker || (mastery < 0 ? i >= marker && i <= AXIS_CELLS / 2 : mastery > 0 && i <= marker && i >= AXIS_CELLS / 2);
            sb.append('<').append(lit ? color : "dark_gray").append('>').append(i == marker ? "█" : "▌")
                    .append("</").append(lit ? color : "dark_gray").append('>');
        }
        return sb.toString();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
