package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-clan relations menu (§6.2). Grew from a 27-slot embargo/blockade/letters-only menu to a
 * 45-slot layout that also exposes war/siege/raid/peace and a trade shortcut - the war/siege/raid
 * mechanics (§3) predate this menu and were command-only until now. Slot numbers are adapted from
 * §6.2's mockup rather than matched pixel-for-pixel (matches how the rest of this menu already
 * diverged from spec - embargo/blockade/letters kept their existing slots to avoid needless
 * churn).
 */
public final class ClanDiplomacyMenu {
    // Раскладка gui_gen v1.4. Меню было на 45 слотов — размер вне стандарта, из-за чего
    // рамка, рабочая зона и футер не совпадали с остальными меню клана. Переведено на 54:
    // голова в слоте 0, переключатели отношений и разделы в шапке (2-7), необратимые
    // действия — в рабочей зоне, назад и закрытие — в футере (52, 53).
    private static final int SLOT_INFO = 0;
    // Шапка: только разделы. Отношения, эмбарго и блокада переехали в рабочую зону —
    // раньше они стояли наверху и мешались с разделами.

    // Рабочая зона, ряд 1 — состояние отношений.
    private static final int SLOT_RELATIONS = 20;
    private static final int SLOT_EMBARGO = 22;
    private static final int SLOT_BLOCKADE = 24;
    // Рабочая зона, ряд 2 — действия.
    private static final int SLOT_TRADE = 28;
    private static final int SLOT_WAR = 30;
    private static final int SLOT_SIEGE = 32;
    private static final int SLOT_RAID = 34;
    // Рабочая зона, ряд 3 — выход из конфликта.
    private static final int SLOT_PEACE = 40;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int INVENTORY_SIZE = 54;

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

        // Рамка: стекло в 1-8, 9-17 и 45-52. Рабочая зона (18-44) не трогается, поэтому
        // пустые слоты между кнопками остаются пустыми, а не забиваются стеклом (правило 8).
        GuiFrames.fillFrame54(inventory);

        DiplomacyRelation current = sourceClan.relationTo(targetClan.id());

        // Все три варианта отношений живут в отдельном подменю — здесь только их текущее
        // состояние и вход в выбор.
        ItemBuilder relationsItem = ItemBuilder.head(relationHead(current))
                .name(plugin.getMessages().component("gui.diplomacy.relations.name", player))
                .lore(plugin.getMessages().components("gui.diplomacy.relations.lore",
                        Map.of("relation", plugin.getMessages().relationName(current)), player));
        inventory.setItem(SLOT_RELATIONS, relationsItem.build());

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

        inventory.setItem(51, GuiFrames.glassPane());

        inventory.setItem(SLOT_TRADE, buildTradeItem(sourceClan, targetClan, player).build());

        boolean inConflict = plugin.getClanManager().inConflictWith(sourceClan.id(), targetClan.id());
        inventory.setItem(SLOT_WAR, buildWarItem(player, sourceClan, targetClan, inConflict).build());
        inventory.setItem(SLOT_SIEGE, buildSiegeItem(player, sourceClan, targetClan, inConflict).build());
        inventory.setItem(SLOT_RAID, buildRaidItem(player, sourceClan, targetClan, inConflict).build());

        if (inConflict) {
            inventory.setItem(SLOT_PEACE, ItemBuilder.head(ItemBuilder.HEAD_RELATION_FRIENDLY)
                    .name(plugin.getMessages().component("gui.diplomacy.peace.name", player))
                    .lore(plugin.getMessages().component("gui.diplomacy.peace.lore", player))
                    .glow(true)
                    .build());
        } else {
            inventory.setItem(SLOT_PEACE, ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.peace.unavailable-name", player))
                    .build());
        }

        // Слот 0 — голова темы меню: чей это клан. Эмблема-баннер сюда не годится,
        // стандарт держит в контенте только головы.
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_DIPLOMACY)
                .name(plugin.getMessages().component("gui.diplomacy.info.name",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.diplomacy.info.lore", Map.of(
                        "level", String.valueOf(targetClan.level()),
                        "influence", String.valueOf(targetClan.influence()),
                        "members", String.valueOf(targetClan.members().size()),
                        "relation", plugin.getMessages().relationName(current)
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

    /** Кнопка «Отношения» носит текстуру текущего состояния, чтобы читаться с одного взгляда. */
    private String relationHead(DiplomacyRelation relation) {
        return switch (relation) {
            case ALLY -> ItemBuilder.HEAD_RELATION_FRIENDLY;
            case ENEMY -> ItemBuilder.HEAD_RELATION_HOSTILE;
            case NEUTRAL -> ItemBuilder.HEAD_RELATION_NEUTRAL;
        };
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
            return Optional.of(DenyResult.of("deny.war.not-in-territory"));
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
            return Optional.of(DenyResult.of("deny.siege.not-in-territory"));
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

    private ItemBuilder buildWarItem(Player player, Clan sourceClan, Clan targetClan, boolean inConflict) {
        Optional<DenyResult> deny = checkWarDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.war", true);
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_RELATION_HOSTILE)
                .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.war.lore", player));
        if (cbRequired) {
            builder.lore(Component.text("§a✔ Casus Belli готов в инвентаре"));
        }
        return builder;
    }

    private ItemBuilder buildSiegeItem(Player player, Clan sourceClan, Clan targetClan, boolean inConflict) {
        Optional<DenyResult> deny = checkSiegeDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.siege", true);
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_BLOCKADE)
                .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.siege.lore", player));
        if (cbRequired) {
            builder.lore(Component.text("§a✔ Casus Belli готов в инвентаре"));
        }
        return builder;
    }

    private ItemBuilder buildRaidItem(Player player, Clan sourceClan, Clan targetClan, boolean inConflict) {
        Optional<DenyResult> deny = checkRaidDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.raid.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        return ItemBuilder.head(ItemBuilder.HEAD_ABILITY_BERSERKER)
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
                .filter(t -> plugin.getAdvancedClaimsHook().contains(t, player.getLocation()))
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
        if (slot == SLOT_RELATIONS) {
            plugin.getGuiManager().openRelations(player, sourceClan, targetClan);
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
        }
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
        Optional<DenyResult> deny = checkWarDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            plugin.getMessages().sendDeny(player, deny.get().key(), deny.get().placeholders());
            return;
        }
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.getMessages().sendDeny(player, "deny.war.not-in-territory", Map.of());
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

    private void handleSiegeDeclare(Player player, Clan sourceClan, Clan targetClan) {
        Optional<DenyResult> deny = checkSiegeDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            plugin.getMessages().sendDeny(player, deny.get().key(), deny.get().placeholders());
            return;
        }
        Optional<TerritoryKey> territory = resolveContestedTerritory(player, targetClan);
        if (territory.isEmpty()) {
            plugin.getMessages().sendDeny(player, "deny.siege.not-in-territory", Map.of());
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

    private void handleRaidDeclare(Player player, Clan sourceClan, Clan targetClan) {
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

    private void handlePeace(Player player, Clan sourceClan, Clan targetClan) {
        // A proposal, not a unilateral end: the other clan has to answer with its own peace request.
        plugin.getPeaceService().proposeOrAccept(player, sourceClan, targetClan);
    }
}
