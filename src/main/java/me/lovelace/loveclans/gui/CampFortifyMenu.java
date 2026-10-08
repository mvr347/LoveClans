package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.siege.ClanSiege;
import me.lovelace.loveclans.model.siege.SiegeCamp;
import me.lovelace.loveclans.model.siege.SiegeState;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Optional;
import java.util.UUID;

/**
 * Меню укрепления осадного лагеря (27 слотов).
 * Соответствует стандарту gui-gen-5:
 * Header: 0-8 (стекло, 0 - иконка лагеря)
 * Working zone: 9-17 (без стекла, стенки 9 и 17 пусты, центр 13 - кнопка улучшения)
 * Footer: 18-26 (стекло, 26 - кнопка закрытия)
 */
public final class CampFortifyMenu {
    private static final int INVENTORY_SIZE = 27;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_UPGRADE = 13;
    private static final int SLOT_CLOSE = 26;

    public static final class Holder extends ClanMenuHolder {
        private final UUID siegeId;
        private final int campIndex;

        public Holder(UUID clanId, UUID siegeId, int campIndex) {
            super(ClanMenuType.CAMP_FORTIFY, clanId);
            this.siegeId = siegeId;
            this.campIndex = campIndex;
        }

        public UUID siegeId() { return siegeId; }
        public int campIndex() { return campIndex; }
    }

    private final LoveClansPlugin plugin;

    public CampFortifyMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, UUID siegeId, int campIndex) {
        Optional<ClanSiege> siegeOpt = plugin.getSiegeManager().findSiegeById(siegeId);
        if (siegeOpt.isEmpty() || siegeOpt.get().state() != SiegeState.ACTIVE) {
            player.sendMessage(Component.text("§cОсада не активна."));
            return;
        }
        ClanSiege siege = siegeOpt.get();
        Optional<Clan> clanOpt = plugin.getClanManager().getClanById(siege.attackerClanId());
        if (clanOpt.isEmpty()) {
            return;
        }
        Clan clan = clanOpt.get();

        if (campIndex < 0 || campIndex >= siege.camps().size()) {
            return;
        }
        SiegeCamp camp = siege.camps().get(campIndex);
        if (camp.broken()) {
            player.sendMessage(Component.text("§cРазрушенный лагерь нельзя укрепить до его восстановления!"));
            return;
        }

        Holder holder = new Holder(clan.id(), siegeId, campIndex);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("§8Укрепление лагеря §6#" + (campIndex + 1)));
        holder.setInventory(inventory);

        // Стандарт gui-gen-5: заливка рамки 27 слотов (1-8 и 18-24 стекло)
        GuiFrames.fillFrame27(inventory);
        // No Back button here (the menu opens from a camp, not from another menu): the slot keeps its glass
        inventory.setItem(25, GuiFrames.glassPane());

        int currentLevel = plugin.getSiegeManager().fortificationLevel(siegeId, campIndex);
        int maxLevel = Math.max(1, plugin.getConfig().getInt("siege.fortification.max-level", 3));
        int hitsRequired = plugin.getSiegeManager().hitsRequired(siegeId, campIndex);
        int hitsPerLevel = Math.max(1, plugin.getConfig().getInt("siege.fortification.hits-per-level", 1));

        // Слот 0: Инфо о лагере
        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_INFO)
                .name(Component.text("§6Осадный лагерь #" + (campIndex + 1)))
                .lore(Component.text("§7Текущее укрепление: §e" + currentLevel + " / " + maxLevel))
                .lore(Component.text("§7Ударов для сноса: §c" + hitsRequired))
                .build());

        // Слот 13: Кнопка апгрейда
        if (currentLevel < maxLevel) {
            if (!plugin.getSiegeManager().canFortify(siegeId, campIndex)) {
                plugin.getMessages().send(player, "siege.fortify.unavailable");
                return;
            }
            long cost = plugin.getSiegeManager().fortifyCost(siegeId, campIndex);
            int nextHits = 1 + (currentLevel + 1) * hitsPerLevel;

            inventory.setItem(SLOT_UPGRADE, ItemBuilder.of(Material.ANVIL)
                    .name(Component.text("§aУкрепить лагерь (Ур. " + (currentLevel + 1) + " / " + maxLevel + ")"))
                    .lore(Component.text("§7Увеличивает прочность лагеря на §a+" + hitsPerLevel + " §7удар."))
                    .lore(Component.text("§7Ударов для сноса станет: §a" + nextHits))
                    .lore(Component.text("§7Стоимость: §6" + cost + "⛃"))
                    .lore(Component.empty())
                    .lore(Component.text("§eНажмите для улучшения"))
                    .build());
        } else {
            inventory.setItem(SLOT_UPGRADE, ItemBuilder.of(Material.BARRIER)
                    .name(Component.text("§cМаксимальный уровень"))
                    .lore(Component.text("§7Лагерь максимально укреплён (" + maxLevel + "/" + maxLevel + ")."))
                    .build());
        }

        // Слот 26: Закрыть
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(Component.text("§cЗакрыть"))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_UPGRADE) {
            UUID siegeId = holder.siegeId();
            int campIndex = holder.campIndex();

            Optional<ClanSiege> siegeOpt = plugin.getSiegeManager().findSiegeById(siegeId);
            if (siegeOpt.isEmpty() || siegeOpt.get().state() != SiegeState.ACTIVE) {
                player.sendMessage(Component.text("§cОсада завершена или не активна."));
                player.closeInventory();
                return;
            }

            Optional<Clan> clanOpt = plugin.getClanManager().getClanById(holder.clanId());
            if (clanOpt.isEmpty()) {
                return;
            }
            Clan clan = clanOpt.get();

            if (!clan.hasMember(player.getUniqueId())) {
                player.sendMessage(Component.text("§cВы не состоите в клане атакующих!"));
                player.closeInventory();
                return;
            }

            int currentLevel = plugin.getSiegeManager().fortificationLevel(siegeId, campIndex);
            int maxLevel = Math.max(1, plugin.getConfig().getInt("siege.fortification.max-level", 3));
            if (currentLevel >= maxLevel) {
                player.sendMessage(Component.text("§cЛагерь уже максимально укреплён!"));
                return;
            }

            long baseCost = plugin.getConfig().getLong("siege.fortification.cost-per-level", 200L);
            long cost = baseCost * (currentLevel + 1);

            boolean paid = false;
            // Списываем сначала из казны клана, если есть права BANK
            if (clan.hasPermission(player.getUniqueId(), ClanPermission.BANK) && clan.chestMoney() >= cost) {
                clan.addChestMoney(-cost);
                plugin.getStorage().updateClanChestMoney(clan.id(), clan.chestMoney());
                paid = true;
            } else {
                // Иначе проверяем карман игрока
                Optional<LoveEconomy> econ = economy();
                if (econ.isPresent() && econ.get().has(player, cost)) {
                    paid = econ.get().charge(player, cost);
                }
            }

            if (!paid) {
                player.sendMessage(Component.text("§cНедостаточно средств для улучшения! Требуется: §e" + cost + "⛃"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            if (plugin.getSiegeManager().fortifyCamp(siegeId, campIndex)) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                int newLevel = plugin.getSiegeManager().fortificationLevel(siegeId, campIndex);
                int hitsReq = plugin.getSiegeManager().hitsRequired(siegeId, campIndex);
                player.sendMessage(Component.text("§aЛагерь #" + (campIndex + 1) + " успешно укреплён до уровня " + newLevel + "! Теперь требуется " + hitsReq + " ударов."));
                open(player, siegeId, campIndex);
            } else {
                // The siege ended or the camp fell between the check and now: give the money back
                econRefund(player, clan, cost);
                plugin.getMessages().send(player, "siege.fortify.unavailable");
            }
        }
    }

    private void econRefund(Player player, Clan clan, long cost) {
        if (clan.hasPermission(player.getUniqueId(), ClanPermission.BANK)) {
            clan.addChestMoney(cost);
            plugin.getStorage().updateClanChestMoney(clan.id(), clan.chestMoney());
        } else {
            economy().ifPresent(e -> e.give(player, cost));
        }
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
