package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

public final class ServerTradeManager {
    public static final int MAX_WEEKLY_STACKS = 10;

    public record TradeOffer(Material material, int amount, long rewardMoney, String displayName) {}

    private final LoveClansPlugin plugin;

    // 4 ротируемых пула товаров по неделям
    private static final List<List<TradeOffer>> ROTATING_POOLS = List.of(
            List.of(
                    new TradeOffer(Material.WHEAT, 64, 250, "Пшеница (64 шт.)"),
                    new TradeOffer(Material.IRON_INGOT, 64, 600, "Железные слитки (64 шт.)"),
                    new TradeOffer(Material.OAK_LOG, 64, 200, "Дубовые брёвна (64 шт.)"),
                    new TradeOffer(Material.BAKED_POTATO, 64, 300, "Печёный картофель (64 шт.)")
            ),
            List.of(
                    new TradeOffer(Material.CARROT, 64, 250, "Морковь (64 шт.)"),
                    new TradeOffer(Material.GOLD_INGOT, 64, 750, "Золотые слитки (64 шт.)"),
                    new TradeOffer(Material.BIRCH_LOG, 64, 200, "Берёзовые брёвна (64 шт.)"),
                    new TradeOffer(Material.COOKED_BEEF, 64, 400, "Стейки (64 шт.)")
            ),
            List.of(
                    new TradeOffer(Material.POTATO, 64, 220, "Картофель (64 шт.)"),
                    new TradeOffer(Material.COPPER_INGOT, 64, 450, "Медные слитки (64 шт.)"),
                    new TradeOffer(Material.SPRUCE_LOG, 64, 200, "Еловые брёвна (64 шт.)"),
                    new TradeOffer(Material.COOKED_PORKCHOP, 64, 380, "Жареная свинина (64 шт.)")
            ),
            List.of(
                    new TradeOffer(Material.PUMPKIN, 64, 300, "Тыквы (64 шт.)"),
                    new TradeOffer(Material.COAL, 64, 350, "Уголь (64 шт.)"),
                    new TradeOffer(Material.DARK_OAK_LOG, 64, 220, "Тёмный дуб (64 шт.)"),
                    new TradeOffer(Material.BREAD, 64, 280, "Хлеб (64 шт.)")
            )
    );

    public ServerTradeManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public int currentWeek() {
        return Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
    }

    public void checkAndResetWeek(Clan clan) {
        int current = currentWeek();
        if (clan.getServerTradeWeek() != current) {
            clan.setServerTradeWeek(current);
            clan.setServerTradeWeeklyStacks(0);
            plugin.getStorage().updateClanServerTrade(clan.id(), 0, current);
        }
    }

    public List<TradeOffer> currentOffers() {
        int index = Math.abs(currentWeek()) % ROTATING_POOLS.size();
        return ROTATING_POOLS.get(index);
    }

    public boolean sellOffer(Player player, Clan clan, TradeOffer offer) {
        if (!clan.isRecognized()) {
            plugin.getMessages().send(player, "trade.server.not-recognized");
            return false;
        }

        checkAndResetWeek(clan);

        if (clan.getServerTradeWeeklyStacks() >= MAX_WEEKLY_STACKS) {
            plugin.getMessages().send(player, "trade.server.weekly-limit-reached",
                    Map.of("limit", String.valueOf(MAX_WEEKLY_STACKS)));
            return false;
        }

        // Проверяем наличие предметов у игрока
        if (!player.getInventory().containsAtLeast(new ItemStack(offer.material()), offer.amount())) {
            plugin.getMessages().send(player, "trade.server.not-enough-items",
                    Map.of("item", offer.displayName(), "amount", String.valueOf(offer.amount())));
            return false;
        }

        // Списываем предметы
        player.getInventory().removeItem(new ItemStack(offer.material(), offer.amount()));

        // Пополняем казну клана
        clan.addChestMoney(offer.rewardMoney());
        clan.incrementServerTradeWeeklyStacks(1);

        // Сохраняем в базу данных
        plugin.getStorage().updateClanChestMoney(clan.id(), clan.chestMoney());
        plugin.getStorage().updateClanServerTrade(clan.id(), clan.getServerTradeWeeklyStacks(), clan.getServerTradeWeek());

        // Уведомляем игрока
        plugin.getMessages().send(player, "trade.server.sell-success", Map.of(
                "item", offer.displayName(),
                "reward", String.valueOf(offer.rewardMoney()),
                "current", String.valueOf(clan.getServerTradeWeeklyStacks()),
                "max", String.valueOf(MAX_WEEKLY_STACKS)
        ));

        return true;
    }
}
