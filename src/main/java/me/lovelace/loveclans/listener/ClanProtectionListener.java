package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.manager.WarManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.util.ClanItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent; // New import
import org.bukkit.event.player.PlayerQuitEvent; // New import
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ClanProtectionListener implements Listener {

    private final LoveClansPlugin plugin;
    private final ClanManager clanManager;
    private final WarManager warManager;

    public ClanProtectionListener(LoveClansPlugin plugin, ClanManager clanManager, WarManager warManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();
        Block placedBlock = event.getBlockPlaced();

        // Check if the player is trying to place a clan banner
        if (!itemInHand.hasItemMeta() || !itemInHand.getType().toString().endsWith("_BANNER")) {
            return; // Not a banner or no meta
        }

        ItemMeta meta = itemInHand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String bannerType = pdc.get(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING);
        String clanIdString = pdc.get(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);

        if (bannerType == null || clanIdString == null) {
            return; // Not a clan banner
        }

        UUID clanId = UUID.fromString(clanIdString);
        Optional<Clan> clanOpt = clanManager.getClanById(clanId);

        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(player, "territory.banner.invalid-clan");
            event.setCancelled(true);
            return;
        }

        Clan clan = clanOpt.get();

        // **ЗАЩИТА ОТ ДУРАЧКОВ**: Проверяем, что игрок является членом клана, которому принадлежит баннер
        if (!clan.hasMember(player.getUniqueId())) {
            plugin.getMessages().send(player, "territory.banner.not-your-clan");
            event.setCancelled(true);
            return;
        }

        // Check if player is confirming an existing pending claim
        if (clanManager.hasPendingClaim(player.getUniqueId())) {
            event.setCancelled(true); // Always cancel the event, confirmation logic will handle actual placement
            clanManager.confirmPendingClaim(player, placedBlock.getLocation())
                    .thenAccept(territory -> plugin.runSync(() -> {
                        // Success message is sent by ClanManager
                        // The block is actually placed by the player, so we don't need to do anything here
                    }))
                    .exceptionally(throwable -> {
                        plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                        return null;
                    });
        } else {
            // This is an initiation of a new claim
            event.setCancelled(true); // Cancel the event, we'll handle placement after confirmation

            boolean initiated = clanManager.initiateClaimConfirmation(player, clan, placedBlock.getLocation(), bannerType);
            if (!initiated) {
                // If initiation failed, we don't need to do anything since the event is already cancelled
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (clanManager.hasPendingClaim(player.getUniqueId())) {
            clanManager.cancelPendingClaim(player.getUniqueId()).ifPresent(pendingClaim -> {
                // Give the banner back to the player
                ItemStack banner = plugin.getClanManager().getClanItemFactory().createBannerByType(
                        pendingClaim.bannerType(),
                        pendingClaim.clan().id(),
                        pendingClaim.clan().name()
                );
                giveItemBack(player, banner);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (clanManager.hasPendingClaim(player.getUniqueId())) {
            clanManager.cancelPendingClaim(player.getUniqueId()).ifPresent(pendingClaim -> {
                // Give the banner back to the player
                ItemStack banner = plugin.getClanManager().getClanItemFactory().createBannerByType(
                        pendingClaim.bannerType(),
                        pendingClaim.clan().id(),
                        pendingClaim.clan().name()
                );
                giveItemBack(player, banner);
                plugin.getLogger().info("Cancelled pending claim for " + player.getName() + " due to logout.");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getClickedBlock() == null || !event.getAction().isRightClick()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (!clickedBlock.getType().toString().endsWith("_BANNER")) {
            return;
        }

        Optional<PersistentDataContainer> blockPdcOpt = getPDCFromBlock(clickedBlock);

        if (blockPdcOpt.isEmpty()) {
            return; // Not a clan banner block
        }
        PersistentDataContainer pdc = blockPdcOpt.get();

        String bannerType = pdc.get(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING);
        String clanIdString = pdc.get(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);

        if (bannerType == null || clanIdString == null) {
            return;
        }

        UUID clanId = UUID.fromString(clanIdString);
        Optional<Clan> clanOpt = clanManager.getClanById(clanId);

        if (clanOpt.isEmpty()) {
            return;
        }

        Clan clan = clanOpt.get();
        Player player = event.getPlayer();

        if ("TERRITORY".equals(bannerType)) {
            if (clan.member(player.getUniqueId()).map(m -> m.rank() == ClanRank.LEADER).orElse(false)) {
                event.setCancelled(true);
                clan.territories().stream()
                        .filter(t -> !t.isCapital()
                                && t.bannerX() != null
                                && t.bannerX() == clickedBlock.getX()
                                && t.bannerY() != null
                                && t.bannerY() == clickedBlock.getY()
                                && t.bannerZ() != null
                                && t.bannerZ() == clickedBlock.getZ())
                        .findFirst()
                        .ifPresent(territory -> plugin.getGuiManager().openTerritorySettings(player, clan, territory));
            }
            return;
        }

        if (!"CAPITAL".equals(bannerType)) {
            return;
        }

        // Capital management is available to the leader and guardians, matching the menu's own permission rules
        boolean canManageCapital = clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN)
                .orElse(false);
        if (canManageCapital) {
            event.setCancelled(true);
            plugin.getGuiManager().openClanCapitalManagementMenu(player, clan);
        } else {
            plugin.getMessages().send(player, "gui.territories.capital.no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        Player player = event.getPlayer();

        // The bearing block is always protected, even during a siege: letting it break would pop
        // the banner above via block-physics instead of a BlockBreakEvent on the banner itself,
        // which would skip the hit-toughness counter and the capture flow entirely. Attackers
        // must break the banner block directly.
        Block blockAbove = brokenBlock.getRelative(BlockFace.UP);
        if (blockAbove.getType().toString().endsWith("_BANNER")) {
            Optional<PersistentDataContainer> blockAbovePdcOpt = getPDCFromBlock(blockAbove);
            if (blockAbovePdcOpt.isPresent()) {
                PersistentDataContainer pdcAbove = blockAbovePdcOpt.get();
                String clanIdStringAbove = pdcAbove.get(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);

                if (clanIdStringAbove != null) {
                    plugin.getMessages().send(player, "territory.capital.cannot-break-bearing-block");
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!brokenBlock.getType().toString().endsWith("_BANNER")) {
            return; // Not a banner
        }

        Optional<PersistentDataContainer> blockPdcOpt = getPDCFromBlock(brokenBlock);
        if (blockPdcOpt.isEmpty()) {
            return; // Not a clan banner block
        }
        PersistentDataContainer pdc = blockPdcOpt.get();

        String bannerType = pdc.get(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING);
        String clanIdString = pdc.get(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);

        if (bannerType == null || clanIdString == null) {
            return; // Not a clan banner
        }

        UUID clanId = UUID.fromString(clanIdString);
        Optional<Clan> clanOpt = clanManager.getClanById(clanId);
        if (clanOpt.isEmpty()) {
            return;
        }
        Clan clan = clanOpt.get();

        Optional<ClanWar> warOpt = warManager.findWarByContestedBannerLocation(clanId, brokenBlock.getLocation());
        if (warOpt.isEmpty()) {
            // Not the banner actually being contested right now (or the clan isn't in a war
            // over it at all) - protect it same as in peacetime.
            if (warManager.isAtWar(clanId)) {
                plugin.getMessages().send(player, "territory.banner.not-contested");
            } else {
                plugin.getMessages().send(player, "CAPITAL".equals(bannerType)
                        ? "territory.capital.cannot-break-peace"
                        : "territory.banner.cannot-break-peace");
            }
            event.setCancelled(true);
            return;
        }

        ClanWar war = warOpt.get();
        Optional<Clan> breakerClanOpt = clanManager.getPlayerClan(player.getUniqueId());
        if (breakerClanOpt.isEmpty() || !breakerClanOpt.get().id().equals(war.attackerClanId())) {
            plugin.getMessages().send(player, "territory.banner.not-your-clan");
            event.setCancelled(true);
            return;
        }

        // We fully take over the outcome from here: either chip away at the banner's
        // toughness, or (once enough hits land) manually replace the block and hand the
        // attacker a tagged captured-banner item that starts the capitulation countdown.
        event.setCancelled(true);

        int requiredHits = warManager.bannerBreakHitsRequired();
        long resetMs = warManager.bannerBreakResetMillis();
        int hits = warManager.registerBannerHit(war.id(), resetMs);
        if (hits < requiredHits) {
            plugin.getMessages().sendActionBar(player, "war.banner.progress",
                    Map.of("hits", String.valueOf(hits), "required", String.valueOf(requiredHits)));
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            return;
        }

        warManager.resetBannerHits(war.id());
        brokenBlock.setType(Material.AIR);
        ItemStack capturedBanner = plugin.getClanManager().getClanItemFactory().createCapturedBanner(war.id(), clan.id(), clan.name());
        giveItemBack(player, capturedBanner);
        warManager.startBannerCapture(war, player.getUniqueId());
    }

    /**
     * Подсвечивает эффектом Glowing вражеских (по активным войнам) игроков, находящихся на
     * территории клана, с которым они воюют, а также игрока, несущего захваченное знамя.
     * Эффект перевыдаётся коротким импульсом на каждый тик этого метода (см. LoveClansPlugin),
     * поэтому сам угасает вскоре после того, как игрок покидает территорию/война заканчивается.
     */
    public void updateGlowingPlayers() {
        for (ClanWar war : warManager.activeWars()) {
            Optional<Clan> attackerOpt = clanManager.getClanById(war.attackerClanId());
            Optional<Clan> defenderOpt = clanManager.getClanById(war.defenderClanId());
            if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
                continue;
            }
            glowEnemiesInTerritory(defenderOpt.get(), attackerOpt.get());
            glowEnemiesInTerritory(attackerOpt.get(), defenderOpt.get());

            if (war.capturedBannerBy() != null) {
                Player carrier = Bukkit.getPlayer(war.capturedBannerBy());
                if (carrier != null) {
                    carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
                }
            }
        }
    }

    private void glowEnemiesInTerritory(Clan territoryOwner, Clan enemyClan) {
        List<Player> onlineEnemies = enemyClan.members().keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (onlineEnemies.isEmpty()) {
            return;
        }

        for (ClanTerritory territory : territoryOwner.territories()) {
            World world = Bukkit.getWorld(territory.world());
            if (world == null) {
                continue;
            }
            for (Player enemy : onlineEnemies) {
                if (!enemy.getWorld().equals(world)) {
                    continue;
                }
                if (territory.boundingBox().contains(enemy.getLocation().toVector())) {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Check if victim is in a clan
        Optional<Clan> victimClanOpt = clanManager.getPlayerClan(victim.getUniqueId());
        if (victimClanOpt.isEmpty()) {
            return;
        }
        Clan victimClan = victimClanOpt.get();

        // Check if victim is in their Capital Territory
        Optional<ClanTerritory> capitalTerritoryOpt = victimClan.territories().stream().filter(ClanTerritory::isCapital).findFirst();
        if (capitalTerritoryOpt.isEmpty()) {
            return;
        }
        ClanTerritory capital = capitalTerritoryOpt.get();

        // Check if victim's location is within the capital territory's claim
        if (plugin.getAdvancedClaimsHook().isClaimed(victim.getLocation())) {
            // If the victim's clan is at war, force PvP
            if (warManager.isAtWar(victimClan.id())) {
                event.setCancelled(false); // Ensure PvP is active
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        ItemStack itemStack = item.getItemStack();

        // isClanBanner also matches a captured war banner - intentionally: a carrier can't
        // voluntarily drop it to dodge losing it, only death/logout/war-end takes it away.
        if (isClanBanner(itemStack)) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "territory.banner.cannot-drop");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack item : event.getDrops()) {
            // A captured war banner is battle loot, not a personal clan banner - it must not
            // survive death via itemsToKeep (CombatListener already strips it from the drops
            // entirely and resets the capture when its carrier dies).
            if (isClanBanner(item) && !item.getItemMeta().getPersistentDataContainer().has(ClanItemFactory.CAPTURED_BANNER_WAR_KEY, PersistentDataType.STRING)) {
                event.getItemsToKeep().add(item);
            }
        }
        event.getDrops().removeIf(this::isClanBanner);
    }

    private boolean isClanBanner(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING) &&
               pdc.has(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Helper method to get PersistentDataContainer from a Block (assuming it's a BlockEntity like a banner).
     * This is a more robust implementation using BlockState.
     *
     * @param block The block to check.
     * @return Optional containing the PDC if found and applicable, otherwise empty.
     */
    private Optional<PersistentDataContainer> getPDCFromBlock(Block block) {
        BlockState blockState = block.getState();
        if (blockState instanceof org.bukkit.block.Banner bannerState) {
            return Optional.of(bannerState.getPersistentDataContainer());
        }
        return Optional.empty();
    }

    /**
     * Helper method to give an item back to the player's inventory or drop it if full.
     * @param player The player to give the item to.
     * @param item The item to give back.
     */
    private void giveItemBack(Player player, ItemStack item) {
        if (player.getInventory().addItem(item).size() > 0) {
            // Inventory was full, drop the item
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
