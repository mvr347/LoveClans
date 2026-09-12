package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
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

        long cost = plugin.getConfig().getLong("clans.banner.cost", 1000L);
        Optional<LoveEconomy> economy = LoveCore.service(LoveEconomy.class);

        if (cost > 0) {
            if (economy.isEmpty()) {
                plugin.getMessages().send(player, "clan.creation-economy-unavailable");
                return;
            }
            if (!economy.get().has(player, cost)) {
                plugin.getMessages().send(player, "clan.banner.insufficient-funds", Map.of("cost", String.valueOf(cost)));
                return;
            }
            economy.get().charge(player, cost);
        }

        ItemStack banner = plugin.getClanManager().getClanItemFactory().createClanCreationBanner();
        player.getInventory().addItem(banner);
        plugin.getMessages().send(player, "clan.banner.purchase-success", Map.of("cost", String.valueOf(cost)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
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
