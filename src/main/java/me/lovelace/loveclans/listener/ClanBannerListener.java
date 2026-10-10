package me.lovelace.loveclans.listener;

import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclans.util.CoinFormat;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclans.integration.AdvancedClaimsHook;
import me.lovelace.loveclans.manager.FoundationPhase;
import me.lovelace.loveclans.manager.FoundationSession;
import me.lovelace.loveclans.util.DurationFormat;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

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

        // 1. Проверка на членство в клане
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }

        // 2. Проверка cooldown создания клана (с человекочитаемым временем)
        Optional<Long> cdOpt = plugin.getClanManager().getCreationCooldownRemaining(player.getUniqueId());
        if (cdOpt.isPresent()) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "clan.creation-cooldown",
                    Map.of("time", DurationFormat.format(cdOpt.get())));
            return;
        }

        // 3. Защита от параллельного создания
        if (plugin.getClanManager().isCreating(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "clan.banner.creation-in-progress");
            return;
        }

        Location location = event.getBlockPlaced().getLocation();

        // Если игрок приседает (Shift) — сбрасываем сессию, чтобы можно было заново ввести имя и тег в GUI
        if (player.isSneaking() && plugin.getClanManager().hasFoundationSession(player.getUniqueId())) {
            plugin.getClanManager().clearFoundationSession(player.getUniqueId());
            plugin.getAdvancedClaimsHook().hideClaimBorder(player);
            event.setCancelled(true);
            new ClanBannerCreationMenu(plugin, player, location).open();
            return;
        }

        FoundationSession session = plugin.getClanManager().getFoundationSession(player.getUniqueId()).orElse(null);

        // ШАГ A: Данные ещё не введены (или сессия истекла) -> pre-check коробки территории + открытие GUI
        if (session == null || session.phase() == FoundationPhase.NONE) {
            event.setCancelled(true);
            TerritoryOverlapCheck check = checkOverlap(location);
            if (check.hasConflict()) {
                long ticks = plugin.getConfig().getLong("integration.advanced-claims.preview-display-ticks", 300L);
                showConflictBorders(player, check, ticks);
                int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 35);
                int conflictX = check.conflict().map(c -> c.getBoundingBox() != null ? (int) c.getBoundingBox().getCenterX() : location.getBlockX()).orElse(location.getBlockX());
                int conflictZ = check.conflict().map(c -> c.getBoundingBox() != null ? (int) c.getBoundingBox().getCenterZ() : location.getBlockZ()).orElse(location.getBlockZ());
                plugin.getMessages().send(player, "clan.banner.overlap-denied", Map.of(
                        "radius", String.valueOf(radius),
                        "x", String.valueOf(conflictX),
                        "z", String.valueOf(conflictZ),
                        "owner", check.ownerName(plugin.getAdvancedClaimsHook())
                ));
                return;
            }

            // Точка свободна — открываем окно ввода имени и тега
            new ClanBannerCreationMenu(plugin, player, location).open();
            return;
        }

        // ШАГ B: Имя и тег введены (DATA_READY) -> показ превью границ территории
        if (session.phase() == FoundationPhase.DATA_READY) {
            event.setCancelled(true);
            long previewTicks = plugin.getConfig().getLong("integration.advanced-claims.preview-display-ticks", 300L);
            TerritoryOverlapCheck check = checkOverlap(location);
            plugin.getAdvancedClaimsHook().showClaimBorder(player, check.box(), previewTicks);

            if (check.hasConflict()) {
                check.conflict().ifPresent(c -> plugin.getAdvancedClaimsHook().showClaimBorder(player, c.getBoundingBox(), previewTicks, c.getId()));
                plugin.getMessages().send(player, "clan.banner.preview-overlap", Map.of("owner", check.ownerName(plugin.getAdvancedClaimsHook())));
                return;
            }

            int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 35);
            plugin.getClanManager().updateFoundationSession(session.withPreview(location).withPhase(FoundationPhase.PREVIEWED));
            plugin.getMessages().send(player, "clan.banner.preview-ok", Map.of("radius", String.valueOf(radius)));
            return;
        }

        // ШАГ C: Превью согласовано (PREVIEWED) -> финальная установка столицы
        if (session.phase() == FoundationPhase.PREVIEWED) {
            event.setCancelled(true);

            // Если игрок кликнул существенно дальше от точки превью (> 2 блоков или в другом мире)
            if (session.previewLocation() == null
                    || !session.previewLocation().getWorld().equals(location.getWorld())
                    || session.previewLocation().distanceSquared(location) > 4.0) {
                long previewTicks = plugin.getConfig().getLong("integration.advanced-claims.preview-display-ticks", 300L);
                TerritoryOverlapCheck check = checkOverlap(location);
                plugin.getAdvancedClaimsHook().showClaimBorder(player, check.box(), previewTicks);

                if (check.hasConflict()) {
                    check.conflict().ifPresent(c -> plugin.getAdvancedClaimsHook().showClaimBorder(player, c.getBoundingBox(), previewTicks, c.getId()));
                    plugin.getMessages().send(player, "clan.banner.preview-overlap", Map.of("owner", check.ownerName(plugin.getAdvancedClaimsHook())));
                    plugin.getClanManager().updateFoundationSession(session.withPhase(FoundationPhase.DATA_READY));
                    return;
                }

                int radius = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 35);
                plugin.getClanManager().updateFoundationSession(session.withPreview(location).withPhase(FoundationPhase.PREVIEWED));
                plugin.getMessages().send(player, "clan.banner.preview-mismatch", Map.of("radius", String.valueOf(radius)));
                return;
            }

            Block target = location.getBlock();
            if (!target.isEmpty() && !target.isReplaceable()) {
                plugin.getMessages().send(player, "clan.banner.spot-occupied");
                return;
            }

            long cost = MoneyConfig.getScaled(plugin.getConfig(), "clans.creation-cost", 0L);
            if (cost > 0) {
                Optional<LoveEconomy> eco = LoveCore.service(LoveEconomy.class);
                if (eco.isEmpty()) {
                    plugin.getMessages().send(player, "clan.creation-economy-unavailable");
                    return;
                }
                if (!eco.get().has(player, cost)) {
                    plugin.getMessages().send(player, "clan.creation-insufficient-funds");
                    return;
                }
            }

            if (!plugin.getClanManager().tryBeginCreation(player.getUniqueId())) {
                plugin.getMessages().send(player, "clan.banner.creation-in-progress");
                return;
            }

            TerritoryOverlapCheck check = checkOverlap(location);
            if (check.conflict().isPresent()) {
                plugin.getClanManager().endCreation(player.getUniqueId());
                long previewTicks = plugin.getConfig().getLong("integration.advanced-claims.preview-display-ticks", 300L);
                showConflictBorders(player, check, previewTicks);
                plugin.getMessages().send(player, "clan.banner.preview-overlap", Map.of("owner", check.ownerName(plugin.getAdvancedClaimsHook())));
                return;
            }

            if (!plugin.getClanManager().takeCreationBanner(player)) {
                plugin.getClanManager().endCreation(player.getUniqueId());
                plugin.getMessages().send(player, "clan.banner.no-banner-in-hand");
                return;
            }

            plugin.getClanManager().commitFoundation(player, session, location);
        }
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
        // One right click fires the event for both hands; react only to the main one or the menu opens twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();

        // A clan that lost its territory and its banner: the leader buys a replacement for part of the price.
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            openReplacement(player);
            return;
        }

        long cost = MoneyConfig.getScaled(plugin.getConfig(), "clans.banner.cost", 10_000L);
        if (cost <= 0) {
            giveBanner(player, cost);
            return;
        }

        // 2026-09-26: раньше деньги списывались сразу этим же кликом, без подтверждения - см.
        // finalizePurchase() ниже, куда теперь ведёт confirm-кнопка GUI. Меню открывается и тому, кому
        // не хватает монет (иначе клик по NPC ничего не показывал): цена видна, но кнопки подтверждения
        // нет. Настоящее списание и повторная проверка средств - в finalizePurchase.
        Optional<LoveEconomy> economy = LoveCore.service(LoveEconomy.class);
        if (economy.isEmpty()) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        BannerPurchaseConfirmMenu.open(player, plugin, cost, economy.get().has(player, cost));
    }

    private void openReplacement(Player player) {
        Optional<Clan> clan = plugin.getBannerReplacementService().eligibleClan(player);
        if (clan.isEmpty()) {
            // Either not the leader or the clan still has its territory: nothing to replace.
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }
        if (plugin.getClanManager().getClanItemFactory().hasExistingBanner(player, "CAPITAL", clan.get().id())) {
            plugin.getMessages().send(player, "clan.banner.replacement-has-banner");
            return;
        }
        long cost = plugin.getBannerReplacementService().cost();
        Optional<LoveEconomy> economy = cost > 0 ? LoveCore.service(LoveEconomy.class) : Optional.empty();
        if (cost > 0 && economy.isEmpty()) {
            plugin.getMessages().send(player, "clan.creation-economy-unavailable");
            return;
        }
        BannerPurchaseConfirmMenu.open(player, plugin, cost, cost <= 0 || economy.get().has(player, cost), true);
    }

    private void giveBanner(Player player, long cost) {
        ItemStack banner = plugin.getClanManager().getClanItemFactory().createClanCreationBanner();
        player.getInventory().addItem(banner);
        plugin.getMessages().send(player, "clan.banner.bought", Map.of("cost", CoinFormat.format(cost)));
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
            plugin.getMessages().send(player, "clan.banner.cannot-afford", Map.of("cost", CoinFormat.format(cost)));
            return;
        }
        // charge() is the authority (has() above can be stale): no banner unless the coins were really taken
        if (!economy.get().charge(player, cost)) {
            plugin.getMessages().send(player, "clan.banner.cannot-afford", Map.of("cost", CoinFormat.format(cost)));
            return;
        }
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
                if (!holder.affordable()) {
                    return; // no confirm button was shown; ignore the (glass) slot
                }
                player.closeInventory();
                if (holder.replacement()) {
                    plugin.getBannerReplacementService().purchase(player, holder.cost());
                } else {
                    finalizePurchase(player, holder.cost());
                }
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

    private record TerritoryOverlapCheck(BoundingBox box, Optional<Claim> conflict, boolean hasConflict) {
        String ownerName(AdvancedClaimsHook hook) {
            return conflict.map(hook::formatClaimOwner).orElse("неизвестно");
        }
    }

    private TerritoryOverlapCheck checkOverlap(Location location) {
        World world = location.getWorld();
        BoundingBox box = AdvancedClaimsHook.computeTerritoryBounds(plugin, location.getBlockX(), location.getBlockY(), location.getBlockZ(), world);
        Optional<Claim> conflict = plugin.getAdvancedClaimsHook().findOverlappingClaim(world, box);
        boolean hasConflict = conflict.isPresent() || plugin.getAdvancedClaimsHook().isClaimed(location);
        return new TerritoryOverlapCheck(box, conflict, hasConflict);
    }

    private void showConflictBorders(Player player, TerritoryOverlapCheck check, long ticks) {
        plugin.getAdvancedClaimsHook().showClaimBorder(player, check.box(), ticks);
        check.conflict().ifPresent(c -> plugin.getAdvancedClaimsHook().showClaimBorder(player, c.getBoundingBox(), ticks, c.getId()));
    }
}
