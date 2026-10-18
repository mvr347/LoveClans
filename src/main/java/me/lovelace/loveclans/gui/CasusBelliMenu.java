package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.service.MessageService;
import me.lovelace.loveclans.util.CasusPrices;
import me.lovelace.loveclans.util.CoinFormat;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Меню оформления повода к войне / осаде (Casus Belli) у Гильдмастера.
 * Стандарт gui-gen-6 (54 слота). Весь текст живёт в lang.yml, ветка {@code gui.casus-belli}.
 */
public final class CasusBelliMenu {
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_TAB_WAR = 3;
    private static final int SLOT_TAB_SIEGE = 5;
    private static final int SLOT_PREV_PAGE = 36;
    private static final int SLOT_NEXT_PAGE = 44;
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;

    private static final int[] TARGET_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public enum Stage {
        TARGET_SELECT,
        REASON_SELECT
    }

    public record ReasonEntry(String reasonId, String title, int cost, boolean isJust, boolean available, String unavailableReason) {}

    public static final class Holder extends ClanMenuHolder {
        private final Stage stage;
        private final String conflictType;
        private final UUID targetClanId;
        private final int page;
        private final Map<Integer, UUID> clanSlots = new HashMap<>();
        private final Map<Integer, ReasonEntry> reasonSlots = new HashMap<>();

        public Holder(UUID clanId, Stage stage, String conflictType, UUID targetClanId, int page) {
            super(ClanMenuType.CASUS_BELLI, clanId);
            this.stage = stage;
            this.conflictType = conflictType;
            this.targetClanId = targetClanId;
            this.page = page;
        }

        public Stage stage() { return stage; }
        public String conflictType() { return conflictType; }
        public UUID targetClanId() { return targetClanId; }
        public int page() { return page; }
    }

    private final LoveClansPlugin plugin;

    public CasusBelliMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    private MessageService messages() {
        return plugin.getMessages();
    }

    private String typeName(String conflictType) {
        return messages().raw("gui.casus-belli.type." + (conflictType.equalsIgnoreCase("WAR") ? "war" : "siege"));
    }

    /** Type switch in the header: the selected one is marked in its name, the other one carries the click hint. */
    private ItemStack tabItem(Material material, boolean war, boolean selected, Player player) {
        String base = "gui.casus-belli.tab." + (war ? "war" : "siege");
        ItemBuilder builder = ItemBuilder.of(material)
                .name(messages().component(base + (selected ? ".name-selected" : ".name"), player))
                .lore(messages().components(base + ".lore", Map.of("level",
                        String.valueOf(plugin.getConfig().getInt("siege.min-attacker-clan-level", 5))), player));
        if (!selected) {
            builder.lore(LoreBlocks.clicks(messages(), player,
                    List.of(LoreBlocks.hint(messages(), LoreBlocks.Click.LEFT, base + ".action"))));
        }
        return builder.build();
    }

    public void open(Player player, Clan clan) {
        openTargetSelect(player, clan, "WAR", 0);
    }

    public void openTargetSelect(Player player, Clan clan, String conflictType, int page) {
        Holder holder = new Holder(clan.id(), Stage.TARGET_SELECT, conflictType, null, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                messages().component("gui.casus-belli.title-target", Map.of("type", typeName(conflictType)), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        // Слот 0: Инфо
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(messages().component("gui.casus-belli.info.name", player))
                .lore(messages().components("gui.casus-belli.info.lore", player))
                .build());

        // Header кнопки переключения типа: слоты 3 и 5 (динамическое центрирование по gui-gen-5)
        boolean isWar = conflictType.equalsIgnoreCase("WAR");
        inventory.setItem(SLOT_TAB_WAR, tabItem(Material.IRON_SWORD, true, isWar, player));
        inventory.setItem(SLOT_TAB_SIEGE, tabItem(Material.CAMPFIRE, false, !isWar, player));

        // Working zone: список доступных кланов
        List<Clan> targets = plugin.getClanManager().getAllClans().stream()
                .filter(c -> !c.id().equals(clan.id()))
                .sorted(Comparator.comparing(Clan::name))
                .toList();

        int pageSize = TARGET_SLOTS.length;
        int maxPages = Math.max(1, (int) Math.ceil((double) targets.size() / pageSize));
        int safePage = Math.min(page, maxPages - 1);

        int startIdx = safePage * pageSize;
        int endIdx = Math.min(startIdx + pageSize, targets.size());

        for (int i = startIdx; i < endIdx; i++) {
            Clan target = targets.get(i);
            int slot = TARGET_SLOTS[i - startIdx];
            holder.clanSlots.put(slot, target.id());

            Material emblem = target.emblem() != null && target.emblem().name().endsWith("_BANNER")
                    ? target.emblem() : Material.WHITE_BANNER;
            inventory.setItem(slot, ItemBuilder.of(emblem)
                    .name(messages().component("gui.casus-belli.target.name",
                            Map.of("name", target.name(), "tag", target.tag()), player))
                    .lore(messages().components("gui.casus-belli.target.lore", Map.of(
                            "level", String.valueOf(target.level()),
                            "members", String.valueOf(target.members().size())), player))
                    .lore(LoreBlocks.clicks(messages(), player, List.of(
                            LoreBlocks.hint(messages(), LoreBlocks.Click.LEFT, "gui.casus-belli.target.action"))))
                    .build());
        }

        // Пагинация в слотах 36 и 44
        if (safePage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(messages().component("gui.previous-page", player))
                    .build());
        }
        if (safePage < maxPages - 1) {
            inventory.setItem(SLOT_NEXT_PAGE, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(messages().component("gui.next-page", player))
                    .build());
        }

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(messages().component("gui.casus-belli.back-guildmaster", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(messages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void openReasonSelect(Player player, Clan clan, String conflictType, UUID targetClanId) {
        Clan targetClan = plugin.getClanManager().getClanById(targetClanId).orElse(null);
        if (targetClan == null) {
            openTargetSelect(player, clan, conflictType, 0);
            return;
        }

        Holder holder = new Holder(clan.id(), Stage.REASON_SELECT, conflictType, targetClanId, 0);
        String typeTitle = typeName(conflictType);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                messages().component("gui.casus-belli.title-reason", Map.of("target", targetClan.name(), "type", typeTitle), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        // Слот 0: эмблема цели
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(messages().component("gui.casus-belli.reason-info.name",
                        Map.of("name", targetClan.name(), "tag", targetClan.tag()), player))
                .lore(messages().components("gui.casus-belli.reason-info.lore", Map.of("type", typeTitle,
                        "attacker", String.valueOf(clan.level()), "target", String.valueOf(targetClan.level())), player))
                .build());

        // Header переключатели
        boolean isWar = conflictType.equalsIgnoreCase("WAR");
        inventory.setItem(SLOT_TAB_WAR, tabItem(Material.IRON_SWORD, true, isWar, player));
        inventory.setItem(SLOT_TAB_SIEGE, tabItem(Material.CAMPFIRE, false, !isWar, player));

        // Проверка уровней для осады
        String siegeError = null;
        if (!isWar) {
            int minAttacker = plugin.getConfig().getInt("siege.min-attacker-clan-level", 5);
            int minDefender = plugin.getConfig().getInt("siege.min-defender-clan-level", 4);
            int gapMax = plugin.getConfig().getInt("siege.level-gap-max", 8);
            if (clan.level() < minAttacker) {
                siegeError = messages().raw("gui.casus-belli.siege-error.attacker-level").replace("<min>", String.valueOf(minAttacker));
            } else if (targetClan.level() < minDefender) {
                siegeError = messages().raw("gui.casus-belli.siege-error.defender-level").replace("<min>", String.valueOf(minDefender));
            } else if (clan.level() - targetClan.level() > gapMax) {
                siegeError = messages().raw("gui.casus-belli.siege-error.level-gap").replace("<max>", String.valueOf(gapMax));
            }
        }

        // Подготовка поводов
        List<ReasonEntry> justReasons = buildJustReasons(clan, targetClan, conflictType, siegeError);
        List<ReasonEntry> frivolousReasons = buildFrivolousReasons(clan, targetClan, conflictType, siegeError);

        // Раскладка Just (слева: 20, 21, 29, 30, 38)
        int[] justSlots = {20, 21, 29, 30, 38};
        for (int i = 0; i < justReasons.size() && i < justSlots.length; i++) {
            ReasonEntry entry = justReasons.get(i);
            int slot = justSlots[i];
            holder.reasonSlots.put(slot, entry);
            inventory.setItem(slot, renderReasonItem(entry, player));
        }

        // Раскладка Frivolous (справа: 23, 24, 32, 33, 41, 42)
        int[] frivSlots = {23, 24, 32, 33, 41, 42};
        for (int i = 0; i < frivolousReasons.size() && i < frivSlots.length; i++) {
            ReasonEntry entry = frivolousReasons.get(i);
            int slot = frivSlots[i];
            holder.reasonSlots.put(slot, entry);
            inventory.setItem(slot, renderReasonItem(entry, player));
        }

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(messages().component("gui.casus-belli.back-target", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(messages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (holder.stage == Stage.TARGET_SELECT) {
            if (slot == SLOT_TAB_WAR) {
                openTargetSelect(player, clan, "WAR", holder.page);
                return;
            }
            if (slot == SLOT_TAB_SIEGE) {
                openTargetSelect(player, clan, "SIEGE", holder.page);
                return;
            }
            if (slot == SLOT_PREV_PAGE && holder.page > 0) {
                openTargetSelect(player, clan, holder.conflictType, holder.page - 1);
                return;
            }
            if (slot == SLOT_NEXT_PAGE) {
                openTargetSelect(player, clan, holder.conflictType, holder.page + 1);
                return;
            }
            if (slot == SLOT_BACK) {
                plugin.getGuiManager().openGuildmaster(player, clan);
                return;
            }

            UUID targetClanId = holder.clanSlots.get(slot);
            if (targetClanId != null) {
                openReasonSelect(player, clan, holder.conflictType, targetClanId);
            }
            return;
        }

        if (holder.stage == Stage.REASON_SELECT) {
            if (slot == SLOT_TAB_WAR) {
                openReasonSelect(player, clan, "WAR", holder.targetClanId);
                return;
            }
            if (slot == SLOT_TAB_SIEGE) {
                openReasonSelect(player, clan, "SIEGE", holder.targetClanId);
                return;
            }
            if (slot == SLOT_BACK) {
                openTargetSelect(player, clan, holder.conflictType, 0);
                return;
            }

            ReasonEntry entry = holder.reasonSlots.get(slot);
            if (entry != null) {
                if (!entry.available) {
                    messages().send(player, "gui.casus-belli.message.unavailable", Map.of("reason", entry.unavailableReason()));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                purchaseCasusBelli(player, clan, holder.targetClanId, holder.conflictType, entry);
            }
        }
    }

    private void purchaseCasusBelli(Player player, Clan clan, UUID targetClanId, String conflictType, ReasonEntry entry) {
        Clan targetClan = plugin.getClanManager().getClanById(targetClanId).orElse(null);
        if (targetClan == null) {
            messages().send(player, "gui.casus-belli.message.target-missing");
            return;
        }

        long cost = entry.cost();
        boolean paid = false;

        Optional<LoveEconomy> econ = economy();
        if (econ.isPresent()) {
            if (econ.get().has(player, cost)) {
                paid = econ.get().charge(player, cost);
            }
        } else if (clan.chestMoney() >= cost) {
            clan.addChestMoney(-cost);
            plugin.getStorage().updateClanChestMoney(clan.id(), clan.chestMoney());
            paid = true;
        }

        if (!paid) {
            messages().send(player, "gui.casus-belli.message.not-enough-money", Map.of("cost", CoinFormat.format(cost)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        long ttlDays = CasusPrices.ttlDays(plugin.getConfig().getConfigurationSection("casus-belli"), entry.reasonId());
        long expiresAt = System.currentTimeMillis() + ttlDays * 24 * 3600_000L;
        ItemStack item = plugin.getClanManager().getClanItemFactory().createCasusBelliItem(
                targetClan.id(), targetClan.name(), conflictType, entry.reasonId(), entry.title(), expiresAt, entry.isJust()
        );
        // A full inventory must not eat a paid scroll: what does not fit falls at the player's feet
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));

        if (entry.isJust()) {
            plugin.getModifierManager().consumeJustCasus(clan.id(), targetClan.id(), conflictType, entry.reasonId());
        }

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        messages().send(player, "gui.casus-belli.message.issued");
        player.closeInventory();
    }

    private int price(boolean just, String reasonId, boolean war) {
        return (int) Math.min(Integer.MAX_VALUE, CasusPrices.cost(plugin.getConfig().getConfigurationSection("casus-belli"), just, reasonId, war));
    }

    private List<ReasonEntry> buildJustReasons(Clan clan, Clan targetClan, String conflictType, String siegeError) {
        boolean isWar = conflictType.equalsIgnoreCase("WAR");

        List<ReasonEntry> list = new ArrayList<>();

        // 1. revenge_raid
        boolean hasRaid = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "revenge_raid");
        list.add(new ReasonEntry("revenge_raid", reasonTitle("revenge_raid"), price(true, "revenge_raid", isWar), true,
                siegeError == null && hasRaid,
                siegeError != null ? siegeError : reasonHint("revenge_raid")));

        // 2. revenge_war
        boolean hasWar = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "revenge_war");
        list.add(new ReasonEntry("revenge_war", reasonTitle("revenge_war"), price(true, "revenge_war", isWar), true,
                siegeError == null && hasWar,
                siegeError != null ? siegeError : reasonHint("revenge_war")));

        // 3. revenge_siege
        boolean hasSiege = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "revenge_siege");
        list.add(new ReasonEntry("revenge_siege", reasonTitle("revenge_siege"), price(true, "revenge_siege", isWar), true,
                siegeError == null && hasSiege,
                siegeError != null ? siegeError : reasonHint("revenge_siege")));

        // 4. unpaid_tribute
        boolean hasTribute = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "unpaid_tribute");
        list.add(new ReasonEntry("unpaid_tribute", reasonTitle("unpaid_tribute"), price(true, "unpaid_tribute", isWar), true,
                siegeError == null && hasTribute,
                siegeError != null ? siegeError : reasonHint("unpaid_tribute")));

        // 5. broken_peace
        boolean hasBroken = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "broken_peace");
        list.add(new ReasonEntry("broken_peace", reasonTitle("broken_peace"), price(true, "broken_peace", isWar), true,
                siegeError == null && hasBroken,
                siegeError != null ? siegeError : reasonHint("broken_peace")));

        return list;
    }

    private List<ReasonEntry> buildFrivolousReasons(Clan clan, Clan targetClan, String conflictType, String siegeError) {
        boolean isWar = conflictType.equalsIgnoreCase("WAR");

        List<ReasonEntry> list = new ArrayList<>();
        list.add(new ReasonEntry("insult", reasonTitle("insult"), price(false, "insult", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("dislike", reasonTitle("dislike"), price(false, "dislike", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("looked_wrong", reasonTitle("looked_wrong"), price(false, "looked_wrong", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("bad_fashion", reasonTitle("bad_fashion"), price(false, "bad_fashion", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("land_envy", reasonTitle("land_envy"), price(false, "land_envy", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("drunk_dare", reasonTitle("drunk_dare"), price(false, "drunk_dare", isWar), false, siegeError == null, siegeError));

        return list;
    }

    private String reasonTitle(String reasonId) {
        return messages().raw("gui.casus-belli.reason." + reasonId + ".title");
    }

    private String reasonHint(String reasonId) {
        return messages().raw("gui.casus-belli.reason." + reasonId + ".unavailable");
    }

    private ItemStack renderReasonItem(ReasonEntry entry, Player player) {
        Map<String, String> ph = Map.of("title", entry.title(), "cost", CoinFormat.format(entry.cost()));
        LoreBlocks.Hint issue = LoreBlocks.hint(messages(), LoreBlocks.Click.LEFT, "gui.casus-belli.reason-item.action");
        if (!entry.available) {
            return ItemBuilder.of(Material.GRAY_DYE)
                    .name(messages().component("gui.casus-belli.reason-item.name-unavailable", ph, player))
                    .lore(messages().component("gui.casus-belli.reason-item.cost", ph, player))
                    .lore(Component.empty())
                    .lore(LoreBlocks.blockedLines(messages(), player, "gui.style.status.unavailable",
                            List.of(messages().component("gui.style.reason", Map.of("reason", String.valueOf(entry.unavailableReason())), player)),
                            issue))
                    .build();
        }

        Material mat = entry.isJust ? Material.LIME_BANNER : Material.ORANGE_BANNER;
        return ItemBuilder.of(mat)
                .name(messages().component(entry.isJust ? "gui.casus-belli.reason-item.name-just"
                        : "gui.casus-belli.reason-item.name-frivolous", ph, player))
                .lore(messages().component(entry.isJust ? "gui.casus-belli.reason-item.category-just"
                        : "gui.casus-belli.reason-item.category-frivolous", player))
                .lore(messages().component("gui.casus-belli.reason-item.cost", ph, player))
                .lore(LoreBlocks.clicks(messages(), player, List.of(issue)))
                .build();
    }

    private static Optional<LoveEconomy> economy() {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") == null) return Optional.empty();
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (NoClassDefFoundError | Exception ignored) {
            return Optional.empty();
        }
    }
}
