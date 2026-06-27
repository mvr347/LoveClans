package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanCreateEvent;
import me.lovelace.loveclans.api.events.ClanMemberJoinEvent;
import me.lovelace.loveclans.api.events.ClanMemberLeaveEvent;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Автоматически окрашивает щиты (Material.SHIELD) в цвет тега клана игрока:
 * при создании клана, вступлении в клан, при появлении щита в руке/инвентаре,
 * и сбрасывает окраску при выходе из клана.
 */
public final class ShieldColorListener implements Listener {
    private final LoveClansPlugin plugin;

    public ShieldColorListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClanCreate(ClanCreateEvent event) {
        Player founder = org.bukkit.Bukkit.getPlayer(event.creatorId());
        if (founder != null) {
            plugin.getShieldColorManager().applyToPlayer(founder, event.clan());
        }
    }

    @EventHandler
    public void onMemberJoin(ClanMemberJoinEvent event) {
        Player player = org.bukkit.Bukkit.getPlayer(event.member().playerId());
        if (player != null) {
            plugin.getShieldColorManager().applyToPlayer(player, event.clan());
        }
    }

    @EventHandler
    public void onMemberLeave(ClanMemberLeaveEvent event) {
        Player player = org.bukkit.Bukkit.getPlayer(event.playerId());
        if (player != null) {
            plugin.getShieldColorManager().resetForPlayer(player);
        }
    }

    // Игрок переключился на щит в хотбаре — перекрашиваем, если он состоит в клане.
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null || newItem.getType() != Material.SHIELD) return;
        applyIfInClan(player);
    }

    // Игрок поменял предметы местами через клавишу F (основная/доп. рука) — щит может оказаться в офф-хенде.
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        ItemStack offHandAfter = event.getOffHandItem();
        ItemStack mainHandAfter = event.getMainHandItem();
        boolean involvesShield = (offHandAfter != null && offHandAfter.getType() == Material.SHIELD)
                || (mainHandAfter != null && mainHandAfter.getType() == Material.SHIELD);
        if (!involvesShield) return;
        applyIfInClan(event.getPlayer());
    }

    // Игрок переложил щит в инвентаре/надел через клик (например, из сундука в офф-хенд).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean involvesShield = (current != null && current.getType() == Material.SHIELD)
                || (cursor != null && cursor.getType() == Material.SHIELD);
        if (!involvesShield) return;
        applyIfInClan(player);
    }

    // На входе в сервер — на случай, если игрок уже состоит в клане и держит/носит непокрашенный щит.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyIfInClan(event.getPlayer());
    }

    private void applyIfInClan(Player player) {
        Optional<Clan> clanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (clanOpt.isEmpty()) return;
        // Откладываем на следующий тик: на момент события (особенно InventoryClickEvent)
        // итоговое содержимое слота/курсора ещё не зафиксировано в инвентаре.
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getShieldColorManager().applyToPlayer(player, clanOpt.get()));
    }
}
