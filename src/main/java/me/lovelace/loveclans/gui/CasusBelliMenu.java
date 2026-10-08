package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.history.ConflictRecord;
import me.lovelace.loveclans.model.modifier.ClanModifier;
import me.lovelace.loveclans.util.CasusPrices;
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
 * Стандарт gui-gen-5 (54 слота).
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

    public void open(Player player, Clan clan) {
        openTargetSelect(player, clan, "WAR", 0);
    }

    public void openTargetSelect(Player player, Clan clan, String conflictType, int page) {
        Holder holder = new Holder(clan.id(), Stage.TARGET_SELECT, conflictType, null, page);
        String typeTitle = conflictType.equalsIgnoreCase("WAR") ? "Война" : "Осада";
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("§8Казус белли: §6Выбор цели (" + typeTitle + ")"));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        // Слот 0: Инфо
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(Component.text("§6Оформление повода"))
                .lore(Component.text("§7Выберите тип конфликта и клан-цель,"))
                .lore(Component.text("§7чтобы перейти к выбору поводов."))
                .build());

        // Header кнопки переключения типа: слоты 3 и 5 (динамическое центрирование по gui-gen-5)
        boolean isWar = conflictType.equalsIgnoreCase("WAR");
        inventory.setItem(SLOT_TAB_WAR, ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text(isWar ? "§a▶ Война (Выбрано)" : "§7Война"))
                .lore(Component.text("§7Открытый конфликт за честь и репарации."))
                .lore(Component.text("§eНажмите для переключения"))
                .build());

        inventory.setItem(SLOT_TAB_SIEGE, ItemBuilder.of(Material.CAMPFIRE)
                .name(Component.text(!isWar ? "§a▶ Осада (Выбрано)" : "§7Осада"))
                .lore(Component.text("§7Штурм периметра с лагерями и трофеями."))
                .lore(Component.text("§7Требует 5 ур. клана атакующего."))
                .lore(Component.text("§eНажмите для переключения"))
                .build());

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
                    .name(Component.text("§eКлан §f" + target.name() + " §7[" + target.tag() + "]"))
                    .lore(Component.text("§7Уровень клана: §b" + target.level()))
                    .lore(Component.text("§7Участников: §f" + target.members().size()))
                    .lore(Component.empty())
                    .lore(Component.text("§eНажмите, чтобы выбрать повод"))
                    .build());
        }

        // Пагинация в слотах 36 и 44
        if (safePage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(Component.text("§e← Предыдущая страница"))
                    .build());
        }
        if (safePage < maxPages - 1) {
            inventory.setItem(SLOT_NEXT_PAGE, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(Component.text("§eСледующая страница →"))
                    .build());
        }

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(Component.text("§7Назад к Гильдмастеру"))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(Component.text("§cЗакрыть"))
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
        String typeTitle = conflictType.equalsIgnoreCase("WAR") ? "Война" : "Осада";
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("§8Повод: §6" + targetClan.name() + " §8(" + typeTitle + ")"));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        // Слот 0: эмблема цели
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(Component.text("§6Цель: §f" + targetClan.name() + " §7[" + targetClan.tag() + "]"))
                .lore(Component.text("§7Тип: §e" + typeTitle))
                .lore(Component.text("§7Уровень атакующего: §b" + clan.level()))
                .lore(Component.text("§7Уровень цели: §b" + targetClan.level()))
                .build());

        // Header переключатели
        boolean isWar = conflictType.equalsIgnoreCase("WAR");
        inventory.setItem(SLOT_TAB_WAR, ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text(isWar ? "§a▶ Война" : "§7Переключить на Войну"))
                .build());
        inventory.setItem(SLOT_TAB_SIEGE, ItemBuilder.of(Material.CAMPFIRE)
                .name(Component.text(!isWar ? "§a▶ Осада" : "§7Переключить на Осаду"))
                .build());

        // Проверка уровней для осады
        String siegeError = null;
        if (!isWar) {
            int minAttacker = plugin.getConfig().getInt("siege.min-attacker-clan-level", 5);
            int minDefender = plugin.getConfig().getInt("siege.min-defender-clan-level", 4);
            int gapMax = plugin.getConfig().getInt("siege.level-gap-max", 8);
            if (clan.level() < minAttacker) {
                siegeError = "Клан слишком слаб для осады (требуется ур. " + minAttacker + ")";
            } else if (targetClan.level() < minDefender) {
                siegeError = "Цель ещё не готова к осаде (требуется ур. " + minDefender + ")";
            } else if (clan.level() - targetClan.level() > gapMax) {
                siegeError = "Нельзя осаждать слабейших (разница > " + gapMax + " уровней)";
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
            inventory.setItem(slot, renderReasonItem(entry));
        }

        // Раскладка Frivolous (справа: 23, 24, 32, 33, 41, 42)
        int[] frivSlots = {23, 24, 32, 33, 41, 42};
        for (int i = 0; i < frivolousReasons.size() && i < frivSlots.length; i++) {
            ReasonEntry entry = frivolousReasons.get(i);
            int slot = frivSlots[i];
            holder.reasonSlots.put(slot, entry);
            inventory.setItem(slot, renderReasonItem(entry));
        }

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(Component.text("§7Назад к выбору цели"))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(Component.text("§cЗакрыть"))
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
                    player.sendMessage(Component.text("§cПовод недоступен: " + entry.unavailableReason));
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
            player.sendMessage(Component.text("§cЦелевой клан не найден."));
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
            player.sendMessage(Component.text("§cНедостаточно средств для оформления! Требуется: §e" + cost + "⛃"));
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
        player.sendMessage(Component.text("§aКазус белли успешно оформлен! Предмет добавлен в ваш инвентарь."));
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
        list.add(new ReasonEntry("revenge_raid", "Месть за набег", price(true, "revenge_raid", isWar), true,
                siegeError == null && hasRaid,
                siegeError != null ? siegeError : "Требуется недавний набег цели на ваш клан"));

        // 2. revenge_war
        boolean hasWar = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "revenge_war");
        list.add(new ReasonEntry("revenge_war", "Месть за войну", price(true, "revenge_war", isWar), true,
                siegeError == null && hasWar,
                siegeError != null ? siegeError : "Требуется поражение в недавней войне от цели"));

        // 3. revenge_siege
        boolean hasSiege = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "revenge_siege");
        list.add(new ReasonEntry("revenge_siege", "Месть за осаду", price(true, "revenge_siege", isWar), true,
                siegeError == null && hasSiege,
                siegeError != null ? siegeError : "Требуется недавняя осада от цели"));

        // 4. unpaid_tribute
        boolean hasTribute = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "unpaid_tribute");
        list.add(new ReasonEntry("unpaid_tribute", "Неуплата дани", price(true, "unpaid_tribute", isWar), true,
                siegeError == null && hasTribute,
                siegeError != null ? siegeError : "Требуется задолженность по выплате дани цели вам"));

        // 5. broken_peace
        boolean hasBroken = plugin.getModifierManager().hasJustCasus(clan.id(), targetClan.id(), conflictType, "broken_peace");
        list.add(new ReasonEntry("broken_peace", "Нарушение мира", price(true, "broken_peace", isWar), true,
                siegeError == null && hasBroken,
                siegeError != null ? siegeError : "Требуется нарушение мирного договора или эмбарго"));

        return list;
    }

    private List<ReasonEntry> buildFrivolousReasons(Clan clan, Clan targetClan, String conflictType, String siegeError) {
        boolean isWar = conflictType.equalsIgnoreCase("WAR");

        List<ReasonEntry> list = new ArrayList<>();
        list.add(new ReasonEntry("insult", "Оскорбление чести", price(false, "insult", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("dislike", "Просто не нравятся", price(false, "dislike", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("looked_wrong", "Криво посмотрели на знамя", price(false, "looked_wrong", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("bad_fashion", "Уродские щиты", price(false, "bad_fashion", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("land_envy", "Зависть к землям", price(false, "land_envy", isWar), false, siegeError == null, siegeError));
        list.add(new ReasonEntry("drunk_dare", "Пьяный спор в таверне", price(false, "drunk_dare", isWar), false, siegeError == null, siegeError));

        return list;
    }

    private ItemStack renderReasonItem(ReasonEntry entry) {
        if (!entry.available) {
            return ItemBuilder.of(Material.GRAY_DYE)
                    .name(Component.text("§7" + entry.title))
                    .lore(Component.text("§cНедоступно"))
                    .lore(Component.text("§7Причина: §c" + entry.unavailableReason))
                    .lore(Component.empty())
                    .lore(Component.text("§8Цена оформления: §7" + entry.cost + "⛃"))
                    .build();
        }

        Material mat = entry.isJust ? Material.LIME_BANNER : Material.ORANGE_BANNER;
        return ItemBuilder.of(mat)
                .name(Component.text((entry.isJust ? "§a" : "§6") + entry.title))
                .lore(Component.text("§7Категория: " + (entry.isJust ? "§aСправедливый повод" : "§6Шуточный/ничтожный")))
                .lore(Component.text("§7Цена оформления: §e" + entry.cost + "⛃"))
                .lore(Component.empty())
                .lore(Component.text("§eНажмите для оформления"))
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
