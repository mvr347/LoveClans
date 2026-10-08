package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ClanManager;
import me.lovelace.loveclans.manager.WarManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.war.ClanWar;
import me.lovelace.loveclans.model.war.WarState;
import me.lovelace.loveclans.gui.RaidLootMenu;
import me.lovelace.loveclans.model.raid.ClanRaid;
import me.lovelace.loveclans.model.raid.RaidPhase;
import me.lovelace.loveclans.model.raid.RaidState;
import me.lovelace.loveclans.util.ClanItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent; // New import
import org.bukkit.event.vehicle.VehicleMoveEvent;
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

        // Запрет стройки в зоне захвата активного набега
        if (plugin.getRaidManager().isInsideCaptureZone(placedBlock.getLocation())) {
            plugin.getMessages().send(player, "raid.capture.zone-protected");
            event.setCancelled(true);
            return;
        }

        // Запрет стройки в 2 блоках от осадного лагеря (анти-коробка лагеря)
        if (plugin.getSiegeManager().isNearAnyCamp(placedBlock.getLocation(), 2)) {
            plugin.getMessages().send(player, "siege.camp-anti-box");
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        if (plugin.getSiegeManager().isNearAnyCamp(block.getLocation(), 2)) {
            plugin.getMessages().send(event.getPlayer(), "siege.camp-anti-box");
            event.setCancelled(true);
        }
    }

    private void handleBannerPlacement(BlockPlaceEvent event, Player player, ItemStack itemInHand, Block placedBlock) {
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
        // A captured war banner is battle loot, not a banner a clan may plant to claim land.
        if (pdc.has(ClanItemFactory.CAPTURED_BANNER_WAR_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
            return;
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
        // Прогрев "/clan home" не переживает логаут — тихо отменяем, чтобы не осталась
        // висящая задача/боссбар (сообщение об отмене всё равно некому показывать).
        if (clanManager.hasPendingHomeTeleport(player.getUniqueId())) {
            clanManager.cancelHomeTeleport(player.getUniqueId(), null);
        }
    }

    /**
     * Движение во время прогрева "/clan home" отменяет телепорт — так же, как это обычно
     * работает у любых других задержанных телепортов ("не двигайся, а то собьёшь"). Сравниваем
     * координаты блока, а не сырые from/to, чтобы одна лишь смена угла обзора (осмотреться на
     * месте) не засчитывалась как движение и не отменяла прогрев впустую.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!clanManager.hasPendingHomeTeleport(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return; // Только осмотрелся — не движение.
        }
        clanManager.cancelHomeTeleport(player.getUniqueId(), "territory.home-teleport.cancelled-moved");
    }

    /**
     * {@link PlayerMoveEvent} НЕ вызывается для игрока-пассажира транспорта (лодка, вагонетка,
     * лошадь и т.п.) — пока игрок едет пассажиром, его перемещение идёт только через
     * {@link VehicleMoveEvent} на самом транспортном средстве. Без этого слушателя игрок мог
     * уехать на лодке (или верхом) далеко от места старта прогрева "/clan home" во время прогрева
     * и всё равно получить телепорт по его истечении — обход проверки в {@link #onPlayerMove}.
     * Планер (элитры) сюда не относится: там движется сам игрок, а не отдельная сущность-транспорт,
     * так что обычный {@code PlayerMoveEvent} по-прежнему срабатывает и отменяет прогрев как надо.
     *
     * <p>{@code VehicleMoveEvent} не реализует {@code Cancellable}, поэтому {@code ignoreCancelled}
     * здесь неприменим (и не нужен — движение транспорта нельзя отменить на этом этапе).</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return; // Транспорт не сменил блок — симметрично проверке блок-координат в onPlayerMove.
        }
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && clanManager.hasPendingHomeTeleport(player.getUniqueId())) {
                clanManager.cancelHomeTeleport(player.getUniqueId(), "territory.home-teleport.cancelled-moved");
            }
        }
    }

    /** The raid chest cannot be blown up: the raid would go on with nothing to capture. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidChestExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getRaidManager().isChestBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidChestBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getRaidManager().isChestBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getClickedBlock() == null || !event.getAction().isRightClick()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();

        // Клик по рейдовому сундуку
        if (clickedBlock.getType() == Material.CHEST && plugin.getRaidManager().isChestBlock(clickedBlock.getLocation())) {
            event.setCancelled(true);
            Optional<ClanRaid> raidOpt = plugin.getRaidManager().getActiveRaidAtChest(clickedBlock.getLocation());
            if (raidOpt.isPresent()) {
                ClanRaid raid = raidOpt.get();
                if (raid.phase() == RaidPhase.CAPTURE) {
                    plugin.getMessages().sendActionBar(player, "raid.chest.locked", Map.of());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
                } else if (raid.phase() == RaidPhase.LOOT) {
                    Optional<Clan> attackerClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
                    if (attackerClanOpt.isPresent() && attackerClanOpt.get().id().equals(raid.attackerClanId())) {
                        Optional<Clan> defenderClanOpt = plugin.getClanManager().getClanById(raid.defenderClanId());
                        if (defenderClanOpt.isPresent()) {
                            RaidLootMenu.open(plugin, raid, defenderClanOpt.get(), player);
                        }
                    } else {
                        plugin.getMessages().send(player, "raid.chest.attacker-only");
                    }
                }
            }
            return;
        }

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

        // Запрет поломки рейдового сундука и блоков в зоне захвата
        if (plugin.getRaidManager().isChestBlock(brokenBlock.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getRaidManager().isInsideCaptureZone(brokenBlock.getLocation())) {
            plugin.getMessages().send(player, "raid.capture.zone-protected");
            event.setCancelled(true);
            return;
        }

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

                if (clanIdStringAbove != null && BannerProtectionListener.isClanBanner(blockAbove)) {
                    plugin.getMessages().send(player, "territory.capital.cannot-break-bearing-block");
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!brokenBlock.getType().toString().endsWith("_BANNER")) {
            // The block behind a wall banner is protected like the one below a standing banner.
            if (BannerProtectionListener.isProtected(brokenBlock)) {
                plugin.getMessages().send(player, "territory.capital.cannot-break-bearing-block");
                event.setCancelled(true);
            }
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
        if (("CAPITAL".equals(bannerType) || "TERRITORY".equals(bannerType)) && !clanManager.isRegisteredBanner(brokenBlock)) {
            return; // Orphan: no territory stands behind this banner, so nothing to protect
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
            Optional<ClanWar> anyWar = warManager.warOf(clanId);
            if (anyWar.isPresent() && anyWar.get().state() == me.lovelace.loveclans.model.war.WarState.PREPARING) {
                plugin.getMessages().send(player, "war.banner.not-started",
                        java.util.Map.of("time", warManager.remainingText(anyWar.get())));
            } else if (anyWar.isPresent() && anyWar.get().defenderClanId().equals(clanId)
                    && warManager.contestedBannerLocation(anyWar.get()).isPresent()) {
                org.bukkit.Location at = warManager.contestedBannerLocation(anyWar.get()).get();
                plugin.getMessages().send(player, "war.banner.wrong-territory", java.util.Map.of(
                        "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ())));
            } else if (anyWar.isPresent()) {
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
        if (war.isBannerSuppressed()) {
            plugin.getMessages().send(player, "war.banner.suppressed-cooldown");
            event.setCancelled(true);
            return;
        }

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
        // Перк ВОЙНА (§7): +N% урона по знамёнам — реализовано как уменьшение числа ударов,
        // необходимых для поломки знамени атакующим кланом с этим перком.
        boolean attackerIsWarrior = breakerClanOpt.get().perk()
                .map(perk -> perk == me.lovelace.loveclans.model.ClanPerk.WARRIOR).orElse(false);
        if (attackerIsWarrior) {
            int bonusPercent = plugin.getConfig().getInt("perks.warrior.banner-damage-bonus-percent", 10);
            // floor, not round: with the defaults 5 hits * 0.9 = 4.5 rounded back up to 5 and the perk did nothing
            requiredHits = Math.max(1, (int) Math.floor(requiredHits * (1.0 - bonusPercent / 100.0)));
        }
        long resetMs = warManager.bannerBreakResetMillis();
        int hits = warManager.registerBannerHit(war.id(), resetMs);
        if (hits < requiredHits) {
            plugin.getMessages().sendActionBar(player, "war.banner.progress",
                    Map.of("hits", String.valueOf(hits), "required", String.valueOf(requiredHits)));
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            return;
        }

        warManager.resetBannerHits(war.id());
        warManager.suppressBanner(war);
    }

    /**
     * Подсвечивает эффектом Glowing участников войны внутри оспариваемой территории (симметрично для обеих сторон).
     */
    public void updateGlowingPlayers() {
        for (ClanWar war : warManager.activeWars()) {
            if (war.state() != WarState.ACTIVE) continue;
            Optional<Clan> attackerOpt = clanManager.getClanById(war.attackerClanId());
            Optional<Clan> defenderOpt = clanManager.getClanById(war.defenderClanId());
            if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
                continue;
            }

            Optional<ClanTerritory> contestedOpt = warManager.resolveContestedTerritory(war);
            if (contestedOpt.isPresent()) {
                ClanTerritory ct = contestedOpt.get();
                World world = Bukkit.getWorld(ct.world());
                if (world != null) {
                    Optional<BoundingBox> boxOpt = plugin.getAdvancedClaimsHook().boundingBoxOf(ct);
                    if (boxOpt.isPresent()) {
                        BoundingBox box = boxOpt.get();
                        java.util.stream.Stream.concat(warManager.onlineMembers(attackerOpt.get()), warManager.onlineMembers(defenderOpt.get()))
                                .filter(p -> p.getWorld().equals(world) && box.contains(p.getLocation().toVector()))
                                .forEach(this::applyGlowPulse);
                    }
                }
            }
        }

        // Подсветка атакующих в набеге: ТОЛЬКО внутри claim защитника + буфер 10 блоков (§6 newraids.md)
        for (ClanRaid raid : plugin.getRaidManager().activeRaids()) {
            if (raid.state() != RaidState.ACTIVE) continue;
            Optional<Clan> attackerOpt = clanManager.getClanById(raid.attackerClanId());
            Optional<Clan> defenderOpt = clanManager.getClanById(raid.defenderClanId());
            if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) continue;

            Optional<ClanTerritory> capitalOpt = defenderOpt.get().getCapitalTerritory();
            if (capitalOpt.isEmpty()) continue;
            ClanTerritory capital = capitalOpt.get();
            World world = Bukkit.getWorld(capital.world());
            if (world == null) continue;

            Optional<BoundingBox> boxOpt = plugin.getAdvancedClaimsHook().boundingBoxOf(capital);
            BoundingBox bufferBox = null;
            if (boxOpt.isPresent()) {
                bufferBox = boxOpt.get().clone().expand(10.0);
            } else if (capital.bannerX() != null && capital.bannerZ() != null) {
                bufferBox = new BoundingBox(
                        capital.bannerX() - 35, world.getMinHeight(), capital.bannerZ() - 35,
                        capital.bannerX() + 35, world.getMaxHeight(), capital.bannerZ() + 35
                );
            }
            if (bufferBox == null) continue;

            for (Player attacker : plugin.getRaidManager().onlineMembers(attackerOpt.get()).toList()) {
                if (!attacker.getWorld().equals(world)) continue;
                if (bufferBox.contains(attacker.getLocation().toVector())) {
                    applyGlowPulse(attacker);
                }
            }
        }
    }

    /** One tick's worth of Glowing - reapplied every second by whichever check still matches, so it fades soon after it stops. */
    private void applyGlowPulse(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
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
                if (plugin.getAdvancedClaimsHook().contains(territory, enemy.getLocation())) {
                    applyGlowPulse(enemy);
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
        Optional<ClanTerritory> capitalTerritoryOpt = victimClan.getCapitalTerritory();
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
