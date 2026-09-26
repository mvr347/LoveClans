package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.BannerPurchaseConfirmMenu;
import me.lovelace.loveclans.gui.ClanBannerCreationMenu;
import me.lovelace.loveclans.integration.CitizensIntegration;
import me.lovelace.loveclans.model.Clan;
import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class ClanBannerListener implements Listener {

    private final LoveClansPlugin plugin;
    private final CitizensIntegration citizens;

    public ClanBannerListener(LoveClansPlugin plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!plugin.getClanManager().getClanItemFactory().isClanCreationBanner(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Если игрок уже состоит в клане или владеет им — установка знамени заблокирована
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }

        Location location = event.getBlockPlaced().getLocation();

        // Проверяем возможность заприватить территорию
        if (plugin.getAdvancedClaimsHook().isClaimed(location)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "territory.already-claimed");
            return;
        }

        // Отменяем ванильную установку блока, открываем меню создания
        event.setCancelled(true);
        new ClanBannerCreationMenu(plugin, player, location).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        int boundNpcId = plugin.getConfig().getInt("clans.banner.npc-id", -1);
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }

        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Если игрок уже в клане или владеет кланом — купить нельзя
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }

        long cost = plugin.getConfig().getLong("clans.banner.cost", 3000L);
        if (cost <= 0) {
            giveBanner(player, cost);
            return;
        }

        // 2026-09-26: раньше деньги списывались сразу этим же кликом, без подтверждения - см.
        // finalizePurchase() ниже, куда теперь ведёт confirm-кнопка GUI. Здесь только
        // предварительная проверка средств, чтобы не открывать экран покупки тому, кто её
        // заведомо не потянет; настоящее списание и повторная проверка - в finalizePurchase.
        Optional<LoveEconomy> economy = LoveCore.service(LoveEconomy.class);
        if (economy.isEmpty()) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        if (!economy.get().has(player, cost)) {
            plugin.getMessages().send(player, "clan.banner.cannot-afford", Map.of("cost", String.valueOf(cost)));
            return;
        }

        BannerPurchaseConfirmMenu.open(player, plugin, cost);
    }

    private void giveBanner(Player player, long cost) {
        ItemStack banner = plugin.getClanManager().getClanItemFactory().createClanCreationBanner();
        player.getInventory().addItem(banner);
        plugin.getMessages().send(player, "clan.banner.bought", Map.of("cost", String.valueOf(cost)));
    }

    /** Confirm-кнопка {@link BannerPurchaseConfirmMenu} ведёт сюда - деньги списываются здесь, а не при открытии GUI. */
    private void finalizePurchase(Player player, long cost) {
        // Состояние могло измениться, пока меню было открыто (вступил в другой клан, потратил
        // деньги в другом месте) - перепроверяем оба условия заново, а не доверяем проверке на
        // открытии GUI.
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }
        Optional<LoveEconomy> economy = LoveCore.service(LoveEconomy.class);
        if (economy.isEmpty()) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        if (!economy.get().has(player, cost)) {
            plugin.getMessages().send(player, "clan.banner.cannot-afford", Map.of("cost", String.valueOf(cost)));
            return;
        }
        economy.get().charge(player, cost);
        giveBanner(player, cost);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof BannerPurchaseConfirmMenu.Holder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
                return;
            }
            int slot = event.getRawSlot();
            if (BannerPurchaseConfirmMenu.isConfirmSlot(slot)) {
                player.closeInventory();
                finalizePurchase(player, holder.cost());
            } else if (BannerPurchaseConfirmMenu.isCancelSlot(slot)) {
                player.closeInventory();
            }
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && plugin.getClanManager().getClanItemFactory().isClanCreationBanner(current)) {
            // Если игрок пытается купить / взять из магазина знамя, уже будучи в клане
            if (event.getView().getTitle().toLowerCase().contains("магазин") || event.getView().getTitle().toLowerCase().contains("shop")) {
                if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
                    event.setCancelled(true);
                    plugin.getMessages().send(player, "clan.banner.already-in-clan");
                }
            }
        }
    }
}
