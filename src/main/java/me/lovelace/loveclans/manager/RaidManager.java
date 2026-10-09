package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.history.ConflictKind;
import me.lovelace.loveclans.model.raid.ClanRaid;
import me.lovelace.loveclans.model.raid.RaidPhase;
import me.lovelace.loveclans.model.raid.RaidResult;
import me.lovelace.loveclans.model.raid.RaidState;
import me.lovelace.loveclans.util.ClanItemFactory;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер рейдов по новому дизайну (newraids.md):
 * - Рейдовый сундук на капитальной территории защитника
 * - Зона захвата (цилиндр радиус 5, dy +-3)
 * - Скорость захвата 0..100% с блокировкой и спадом при защитниках в зоне
 * - Компас выдаётся ТОЛЬКО атакующим
 * - Подсветка атакующих ТОЛЬКО внутри claim защитника + буфер 10 блоков
 * - Snapshot казны (40% денег, 40% предметов) при 100% захвата
 * - Substantial loot (15% денег или 1 предмет) + 60s extract таймер выхода из зоны
 * - Щит на защитника после рейда 12 часов
 * - Игроки в AFK > 15 минут не учитываются в порогах онлайна
 */
public final class RaidManager {
    private final LoveClansPlugin plugin;
    private final Map<UUID, ClanRaid> activeRaids = new ConcurrentHashMap<>();
    private final Map<AbstractMap.SimpleImmutableEntry<UUID, UUID>, Long> raidCooldowns = new ConcurrentHashMap<>();
    private final Map<Location, Material> originalChestBlocks = new ConcurrentHashMap<>();
    /** Money taken from the defender during LOOT, per raid and looter: paid out only on ATTACKER_WIN. */
    private final Map<UUID, Map<UUID, Long>> lootBags = new ConcurrentHashMap<>();

    private final Map<UUID, List<Long>> attackerRaidTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> defenderRaidTimestamps = new ConcurrentHashMap<>();

    private final Map<UUID, BossBar> raidBossBars = new ConcurrentHashMap<>();
    private final Set<UUID> oneMinuteWarned = ConcurrentHashMap.newKeySet();

    public RaidManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** Restores the pair cooldowns after a restart. */
    public void loadCooldowns() {
        plugin.getConflictCooldownStore().loadInto(me.lovelace.loveclans.storage.ConflictCooldownStore.RAID, raidCooldowns);
    }

    private Duration preStartDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("raid.pre-start-minutes", 5));
    }

    private Duration raidDuration() {
        return Duration.ofMinutes(plugin.getConfig().getLong("raid.duration-minutes", 12));
    }

    private Duration cooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("raid.cooldown-hours", 24));
    }

    private Duration preparingCancelCooldownDuration() {
        return Duration.ofHours(plugin.getConfig().getLong("raid.preparing-cancel-cooldown-hours", 2));
    }

    private AbstractMap.SimpleImmutableEntry<UUID, UUID> pairKey(UUID clan1, UUID clan2) {
        return clan1.compareTo(clan2) < 0
                ? new AbstractMap.SimpleImmutableEntry<>(clan1, clan2)
                : new AbstractMap.SimpleImmutableEntry<>(clan2, clan1);
    }

    public int countOnlineNonAfk(Clan clan, long afkIgnoreMinutes) {
        int online = 0;
        for (UUID memberId : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && !plugin.getAfkManager().isAfkMinutes(memberId, afkIgnoreMinutes)) {
                online++;
            }
        }
        return online;
    }

    public int countRaidsAsAttackerToday(UUID clanId) {
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        List<Long> timestamps = attackerRaidTimestamps.computeIfAbsent(clanId, k -> new ArrayList<>());
        timestamps.removeIf(t -> t < cutoff);
        return timestamps.size();
    }

    public int countRaidsAsDefenderToday(UUID clanId) {
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        List<Long> timestamps = defenderRaidTimestamps.computeIfAbsent(clanId, k -> new ArrayList<>());
        timestamps.removeIf(t -> t < cutoff);
        return timestamps.size();
    }

    private void refundRaidCount(UUID attackerId, UUID defenderId) {
        List<Long> a = attackerRaidTimestamps.get(attackerId);
        if (a != null && !a.isEmpty()) a.remove(a.size() - 1);
        List<Long> d = defenderRaidTimestamps.get(defenderId);
        if (d != null && !d.isEmpty()) d.remove(d.size() - 1);
    }

    private void recordRaidStart(UUID attackerId, UUID defenderId) {
        long now = System.currentTimeMillis();
        attackerRaidTimestamps.computeIfAbsent(attackerId, k -> new ArrayList<>()).add(now);
        defenderRaidTimestamps.computeIfAbsent(defenderId, k -> new ArrayList<>()).add(now);
    }

    public CompletableFuture<ClanRaid> startRaidAsync(Clan attacker, Clan defender) {
        return plugin.supplySync(() -> {
            if (attacker.id().equals(defender.id())) {
                throw new IllegalStateException("war.cannot-target-self");
            }
            if (!attacker.hasCapital()) {
                throw new IllegalStateException("raid.attacker-no-capital");
            }
            if (!defender.hasCapital()) {
                throw new IllegalStateException("raid.defender-no-capital");
            }
            if (isInRaid(attacker.id()) || isInRaid(defender.id())) {
                throw new IllegalStateException("raid.already-in-raid");
            }
            if (plugin.getClanManager().inAnyConflict(attacker.id()) || plugin.getClanManager().inAnyConflict(defender.id())) {
                throw new IllegalStateException("raid.conflict-in-progress");
            }
            if (attacker.relationTo(defender.id()) == DiplomacyRelation.ALLY) {
                throw new IllegalStateException("war.cannot-declare-on-ally");
            }

            // Щит на защитника после рейда (12 часов)
            if (plugin.getModifierManager().hasPostRaidShield(defender.id())) {
                throw new IllegalStateException("raid.defender-shielded");
            }

            long afkMinutes = plugin.getConfig().getLong("raid.afk-ignore-minutes", 15L);
            int minAttackerOnline = plugin.getConfig().getInt("raid.min-attacker-online", 2);
            int maxDefenderOnline = plugin.getConfig().getInt("raid.max-defender-online", 2);

            if (countOnlineNonAfk(attacker, afkMinutes) < minAttackerOnline) {
                throw new IllegalStateException("raid.not-enough-attackers");
            }
            if (countOnlineNonAfk(defender, afkMinutes) > maxDefenderOnline) {
                throw new IllegalStateException("raid.too-many-defenders");
            }

            // Суточные лимиты
            int maxAttackerPerDay = plugin.getConfig().getInt("raid.max-raids-per-clan-per-day", 5);
            int maxDefenderPerDay = plugin.getConfig().getInt("raid.max-times-raided-per-day", 1);
            if (countRaidsAsAttackerToday(attacker.id()) >= maxAttackerPerDay) {
                throw new IllegalStateException("raid.limit-reached-attacker");
            }
            if (countRaidsAsDefenderToday(defender.id()) >= maxDefenderPerDay) {
                throw new IllegalStateException("raid.limit-reached-defender");
            }

            // Кулдаун пары кланов
            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(attacker.id(), defender.id());
            long now = System.currentTimeMillis();
            Long lastRaidTime = raidCooldowns.get(cooldownKey);
            if (lastRaidTime != null && (now - lastRaidTime < cooldownDuration().toMillis())) {
                long remainingSeconds = (cooldownDuration().toMillis() - (now - lastRaidTime)) / 1000;
                throw new WarCooldownException(remainingSeconds);
            }

            ClanRaid raid = new ClanRaid(UUID.randomUUID(), attacker.id(), defender.id(), now,
                    now + preStartDuration().toMillis(), RaidState.PREPARING);
            activeRaids.put(raid.id(), raid);
            raidCooldowns.put(cooldownKey, now);
            plugin.getConflictCooldownStore().saveAsync(me.lovelace.loveclans.storage.ConflictCooldownStore.RAID, cooldownKey, now);
            recordRaidStart(attacker.id(), defender.id());

            beginPendingPhase(raid, attacker, defender);
            me.lovelace.loveclans.activity.ConflictEvents.declared(me.lovelace.loveclans.model.history.ConflictKind.RAID,
                    raid.id(), attacker.id(), defender.id());
            return raid;
        });
    }

    private record RaidClans(Clan attacker, Clan defender) {}

    private Optional<RaidClans> resolveClans(ClanRaid raid) {
        Optional<Clan> attacker = plugin.getClanManager().getClanById(raid.attackerClanId());
        Optional<Clan> defender = plugin.getClanManager().getClanById(raid.defenderClanId());
        if (attacker.isEmpty() || defender.isEmpty()) return Optional.empty();
        return Optional.of(new RaidClans(attacker.get(), defender.get()));
    }

    private void beginPendingPhase(ClanRaid raid, Clan attacker, Clan defender) {
        String time = formatDuration(raid.endsAt() - System.currentTimeMillis());
        Component title = plugin.getMessages().component("raid.pending.bossbar", Map.of(
                "attacker", attacker.tag(), "color1", attacker.tagColor(),
                "defender", defender.tag(), "color2", defender.tagColor(), "time", time));
        BossBar bar = BossBar.bossBar(title, 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        raidBossBars.put(raid.id(), bar);

        java.util.stream.Stream.concat(onlineMembers(attacker), onlineMembers(defender)).forEach(p -> p.showBossBar(bar));
        onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "raid.pending.declared",
                Map.of("tag", defender.tag(), "color", defender.tagColor(), "time", time)));
        onlineMembers(defender).forEach(p -> plugin.getMessages().send(p, "raid.pending.declared",
                Map.of("tag", attacker.tag(), "color", attacker.tagColor(), "time", time)));
    }

    private void clearBossBar(UUID raidId, RaidClans clans) {
        BossBar bar = raidBossBars.remove(raidId);
        oneMinuteWarned.remove(raidId);
        if (bar != null && clans != null) {
            java.util.stream.Stream.concat(onlineMembers(clans.attacker()), onlineMembers(clans.defender()))
                    .forEach(p -> p.hideBossBar(bar));
        }
    }

    private void activateRaid(ClanRaid raid) {
        Optional<RaidClans> clansOpt = resolveClans(raid);
        if (clansOpt.isEmpty()) {
            activeRaids.remove(raid.id());
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        // 1. Поиск локации и спавн сундука
        Location chestLoc = locateAndSpawnChest(defender);
        if (chestLoc == null) {
            plugin.getLogger().warning("Failed to locate solid ground for raid chest in defender capital! Cancelling raid.");
            // Not the attackers' fault: the short cooldown of a cancelled PREPARING, and the day's counters are refunded
            AbstractMap.SimpleImmutableEntry<UUID, UUID> failedKey = pairKey(raid.attackerClanId(), raid.defenderClanId());
            long shortCooldown = System.currentTimeMillis() - (cooldownDuration().toMillis() - preparingCancelCooldownDuration().toMillis());
            raidCooldowns.put(failedKey, shortCooldown);
            plugin.getConflictCooldownStore().saveAsync(me.lovelace.loveclans.storage.ConflictCooldownStore.RAID, failedKey, shortCooldown);
            refundRaidCount(raid.attackerClanId(), raid.defenderClanId());
            onlineMembers(attacker).forEach(p -> plugin.getMessages().send(p, "raid.chest-failed"));
            endRaid(raid, RaidResult.CANCELLED);
            return;
        }

        // 2. Создание голограммы над сундуком
        ArmorStand hologram = spawnChestHologram(chestLoc);
        UUID hologramId = hologram != null ? hologram.getUniqueId() : null;

        // 3. Вычисление лимита лута
        long now = System.currentTimeMillis();
        long moneyCap = Math.round(defender.chestMoney() * (plugin.getConfig().getInt("raid.loot-money-percent", 40) / 100.0));
        int unlockedSlots = me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(defender.chestRows(), ClanManager.CHEST_MAX_SIZE);
        int itemSlotCap = (int) Math.round(unlockedSlots * (plugin.getConfig().getInt("raid.loot-item-slot-percent", 40) / 100.0));

        ClanRaid activated = raid.activate(now + raidDuration().toMillis(), chestLoc, hologramId, moneyCap, itemSlotCap);
        activeRaids.put(activated.id(), activated);

        // The defenders' own chest/treasury menus would hold the item-chest lock for the whole raid
        onlineMembers(defender).forEach(p -> {
            org.bukkit.inventory.Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getType() == org.bukkit.event.inventory.InventoryType.CHEST && top.getHolder() == null) {
                p.closeInventory();
            }
        });

        // 4. Компас выдаётся ТОЛЬКО атакующим
        distributeRaidCompasses(activated, attacker, defender, chestLoc);

        // 5. Аларм защитникам и атакующим (title + sound)
        onlineMembers(attacker).forEach(p -> {
            plugin.getMessages().sendTitle(p, "raid.start.attacker-title", "raid.start.attacker-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor(),
                            "time", String.valueOf(raidDuration().toMinutes())));
            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 1.0f);
        });

        onlineMembers(defender).forEach(p -> {
            plugin.getMessages().sendTitle(p, "raid.start.defender-title", "raid.start.defender-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
        });
    }

    private Location locateAndSpawnChest(Clan defender) {
        Optional<ClanTerritory> capitalOpt = defender.getCapitalTerritory();
        if (capitalOpt.isEmpty()) return null;
        ClanTerritory capital = capitalOpt.get();

        World world = Bukkit.getWorld(capital.world());
        if (world == null) return null;

        Optional<BoundingBox> boxOpt = plugin.getAdvancedClaimsHook().boundingBoxOf(capital);
        int minX, maxX, minZ, maxZ;
        if (boxOpt.isPresent()) {
            BoundingBox box = boxOpt.get();
            minX = (int) box.getMinX() + 3;
            maxX = (int) box.getMaxX() - 3;
            minZ = (int) box.getMinZ() + 3;
            maxZ = (int) box.getMaxZ() - 3;
        } else if (capital.bannerX() != null && capital.bannerZ() != null) {
            minX = capital.bannerX() - 25;
            maxX = capital.bannerX() + 25;
            minZ = capital.bannerZ() - 25;
            maxZ = capital.bannerZ() + 25;
        } else {
            return null;
        }

        if (minX > maxX) { int t = minX; minX = maxX; maxX = t; }
        if (minZ > maxZ) { int t = minZ; minZ = maxZ; maxZ = t; }

        int attempts = plugin.getConfig().getInt("raid.chest.resspawn-attempts", 10);
        double minBannerDist = plugin.getConfig().getDouble("raid.chest.min-distance-from-banner", 8.0);
        double minBannerDistSq = minBannerDist * minBannerDist;

        for (int i = 0; i < attempts; i++) {
            int rx = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int rz = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);

            if (capital.bannerX() != null && capital.bannerZ() != null) {
                double bx = capital.bannerX();
                double bz = capital.bannerZ();
                double distSq = (rx - bx) * (rx - bx) + (rz - bz) * (rz - bz);
                if (distSq < minBannerDistSq) continue;
            }

            // getHighestBlockYAt returns the Y of the topmost solid block: the chest goes ON it
            int floorY = world.getHighestBlockYAt(rx, rz);
            if (floorY <= world.getMinHeight() || floorY >= world.getMaxHeight() - 3) continue;

            Material floorMat = world.getBlockAt(rx, floorY, rz).getType();
            if (!floorMat.isSolid() || floorMat == Material.MAGMA_BLOCK || floorMat == Material.CACTUS) {
                continue;
            }

            Location chestLoc = new Location(world, rx, floorY + 1, rz);
            if (!isFreeSpace(chestLoc.getBlock().getType()) || !isFreeSpace(chestLoc.clone().add(0, 1, 0).getBlock().getType())) {
                continue;
            }

            // Only soft decorations (grass, flowers, snow layers) around the chest are cleared: never a build
            int clearRadius = plugin.getConfig().getInt("raid.chest.clear-radius", 2);
            for (int dx = -clearRadius; dx <= clearRadius; dx++) {
                for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        org.bukkit.block.Block around = chestLoc.clone().add(dx, dy, dz).getBlock();
                        if (org.bukkit.Tag.REPLACEABLE.isTagged(around.getType()) && !around.getType().isAir()) {
                            around.setType(Material.AIR);
                        }
                    }
                }
            }

            originalChestBlocks.put(chestLoc, chestLoc.getBlock().getType());
            chestLoc.getBlock().setType(Material.CHEST);
            persistChest(chestLoc, originalChestBlocks.get(chestLoc));
            return chestLoc;
        }

        return null;
    }

    private static boolean isFreeSpace(Material material) {
        return material.isAir() || org.bukkit.Tag.REPLACEABLE.isTagged(material);
    }

    // --- Crash safety: the chest block is world state, so its location is kept on disk until the raid ends ---

    private java.io.File chestFile() {
        return new java.io.File(plugin.getDataFolder(), "raid-chests.yml");
    }

    private void persistChest(Location loc, Material original) {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(chestFile());
        List<String> entries = new ArrayList<>(yaml.getStringList("chests"));
        entries.add(loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + original.name());
        yaml.set("chests", entries);
        saveChestFile(yaml);
    }

    private void forgetChest(Location loc) {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(chestFile());
        String prefix = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ",";
        List<String> entries = new ArrayList<>(yaml.getStringList("chests"));
        if (entries.removeIf(e -> e.startsWith(prefix))) {
            yaml.set("chests", entries);
            saveChestFile(yaml);
        }
    }

    private void saveChestFile(org.bukkit.configuration.file.YamlConfiguration yaml) {
        try {
            yaml.save(chestFile());
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not write raid-chests.yml: " + e.getMessage());
        }
    }

    /** Removes raid chests left in the world by a crash (the in-memory registry is gone, the file is not). */
    public void recoverOrphanChests() {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(chestFile());
        for (String entry : yaml.getStringList("chests")) {
            String[] parts = entry.split(",");
            if (parts.length != 5) continue;
            World world = Bukkit.getWorld(parts[0]);
            Material original = Material.matchMaterial(parts[4]);
            if (world == null) continue;
            try {
                org.bukkit.block.Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (block.getType() == Material.CHEST) {
                    block.setType(original == null ? Material.AIR : original);
                }
            } catch (NumberFormatException ignored) {
                // malformed line: drop it
            }
        }
        yaml.set("chests", List.of());
        saveChestFile(yaml);
    }

    private ArmorStand spawnChestHologram(Location chestLoc) {
        World world = chestLoc.getWorld();
        if (world == null) return null;
        Location holoLoc = chestLoc.clone().add(0.5, 1.2, 0.5);
        ArmorStand stand = (ArmorStand) world.spawnEntity(holoLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        // Never saved with the chunk: a crash cannot leave a ghost label behind
        stand.setPersistent(false);
        stand.customName(plugin.getMessages().component("raid.chest.label-capture", Map.of("progress", "0")));
        return stand;
    }

    private void updateChestHologram(ClanRaid raid) {
        if (raid.chestHologramId() == null) return;
        Entity entity = Bukkit.getEntity(raid.chestHologramId());
        if (!(entity instanceof ArmorStand stand)) return;

        if (raid.phase() == RaidPhase.CAPTURE) {
            stand.customName(plugin.getMessages().component("raid.chest.label-capture",
                    Map.of("progress", String.valueOf((int) raid.captureProgress()))));
        } else if (raid.phase() == RaidPhase.LOOT) {
            stand.customName(plugin.getMessages().component("raid.chest.label-loot"));
        }
    }

    private void distributeRaidCompasses(ClanRaid raid, Clan attacker, Clan defender, Location chestLoc) {
        onlineMembers(attacker).forEach(p -> giveRaidCompass(p, raid, chestLoc, defender));
    }

    private void giveRaidCompass(Player player, ClanRaid raid, Location targetLocation, Clan enemyClan) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ClanItemFactory.RAID_COMPASS_KEY, PersistentDataType.STRING, raid.id().toString());
            meta.displayName(plugin.getMessages().component("raid.compass.name", Map.of("tag", enemyClan.tag())));
            meta.lore(List.of(plugin.getMessages().component("raid.compass.lore")));
            if (meta instanceof org.bukkit.inventory.meta.CompassMeta compassMeta && targetLocation.getWorld() != null) {
                compassMeta.setLodestone(targetLocation);
                compassMeta.setLodestoneTracked(false);
            }
            compass.setItemMeta(meta);
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(compass);
        overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void removeRaidCompasses(ClanRaid raid) {
        Optional<RaidClans> clansOpt = resolveClans(raid);
        if (clansOpt.isEmpty()) return;
        onlineMembers(clansOpt.get().attacker()).forEach(player -> clearRaidCompass(player, raid.id()));
    }

    private void clearRaidCompass(Player player, UUID raidId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String taggedRaidId = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.RAID_COMPASS_KEY, PersistentDataType.STRING);
                if (taggedRaidId != null && taggedRaidId.equals(raidId.toString())) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    // --- Looting ---

    public Optional<ClanRaid> findRaid(UUID first, UUID second) {
        return activeRaids.values().stream().filter(r -> r.between(first, second)).findFirst();
    }

    public Optional<ClanRaid> findActiveRaidAsAttacker(UUID attackerClanId) {
        return activeRaids.values().stream()
                .filter(r -> r.attackerClanId().equals(attackerClanId))
                .findFirst();
    }

    /**
     * Looting needs the LOOT phase and an attacking clan member standing inside the capture zone:
     * the chest cannot be emptied remotely, from the capture phase or by the defenders.
     */
    public boolean canLoot(ClanRaid raid, Player player) {
        if (raid == null || player == null || raid.state() != RaidState.ACTIVE || raid.phase() != RaidPhase.LOOT) return false;
        if (!plugin.getClanManager().getPlayerClan(player.getUniqueId()).map(c -> c.id().equals(raid.attackerClanId())).orElse(false)) return false;
        double radius = plugin.getConfig().getDouble("raid.capture.radius", 5.0);
        return isInCaptureZone(player.getLocation(), raid.chestLocation(), radius * radius);
    }

    private record MoneyLootResult(Clan defender, long amountTaken) {}

    public CompletableFuture<Long> lootMoneyAsync(ClanRaid raid, Player looter, long amount) {
        if (raid == null || looter == null || amount <= 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Raid, looter and amount must be valid."));
        return plugin.supplySync(() -> {
            ClanRaid current = activeRaids.get(raid.id());
            if (current == null || current.state() != RaidState.ACTIVE || current.phase() != RaidPhase.LOOT) {
                throw new IllegalStateException("raid.not-active");
            }
            if (!canLoot(current, looter)) {
                throw new IllegalStateException("raid.loot.not-in-zone");
            }
            Clan defender = plugin.getClanManager().getClanById(current.defenderClanId())
                    .orElseThrow(() -> new IllegalStateException("clan.not-found"));
            long take = Math.min(amount, Math.min(current.moneyRemaining(), defender.chestMoney()));
            if (take <= 0) {
                throw new IllegalStateException("raid.nothing-left");
            }
            defender.addChestMoney(-take);
            // Held in the raid's bag: it reaches the looter only when the raid is won, otherwise it goes back
            lootBags.computeIfAbsent(current.id(), k -> new ConcurrentHashMap<>()).merge(looter.getUniqueId(), take, Long::sum);

            ClanRaid updated = current.withMoneyLooted(take);
            if (updated.hasSubstantialLoot(plugin.getConfig().getDouble("raid.win-min-money-percent", 15.0),
                    plugin.getConfig().getInt("raid.win-min-item-slots", 1)) && updated.firstLootAt() == 0) {
                updated = updated.withFirstLootAt(System.currentTimeMillis());
                looter.sendMessage(plugin.getMessages().component("raid.extract.prompt", Map.of("time",
                        String.valueOf(plugin.getConfig().getLong("raid.extract.seconds-after-first-loot", 60L)))));
            }
            activeRaids.put(current.id(), updated);
            return new MoneyLootResult(defender, take);
        }).thenCompose(result -> plugin.getStorage().updateClanChestMoney(result.defender().id(), result.defender().chestMoney())
                .thenApply(v -> result.amountTaken()));
    }

    public void recordItemSlotLooted(UUID raidId) {
        ClanRaid current = activeRaids.get(raidId);
        if (current != null && current.state() == RaidState.ACTIVE && current.phase() == RaidPhase.LOOT) {
            ClanRaid updated = current.withItemSlotsLooted(1);
            if (updated.hasSubstantialLoot(plugin.getConfig().getDouble("raid.win-min-money-percent", 15.0),
                    plugin.getConfig().getInt("raid.win-min-item-slots", 1)) && updated.firstLootAt() == 0) {
                updated = updated.withFirstLootAt(System.currentTimeMillis());
            }
            activeRaids.put(raidId, updated);
        }
    }

    public Optional<ClanRaid> getRaid(UUID raidId) {
        return Optional.ofNullable(activeRaids.get(raidId));
    }

    // --- Lifecycle queries & Protection helpers ---

    /** A defender's treasury is frozen from the declaration to the end, so it cannot be emptied before the snapshot. */
    public boolean isRaidDefender(UUID clanId) {
        return activeRaids.values().stream().anyMatch(r -> r.defenderClanId().equals(clanId));
    }

    public boolean isInRaid(UUID clanId) {
        return activeRaids.values().stream().anyMatch(r -> r.involves(clanId));
    }

    public Optional<ClanRaid> getActiveRaid(UUID clanId) {
        return activeRaids.values().stream().filter(r -> r.involves(clanId)).findFirst();
    }

    public boolean areInRaid(UUID first, UUID second) {
        return activeRaids.values().stream().anyMatch(r -> r.between(first, second));
    }

    public Collection<ClanRaid> activeRaids() {
        return List.copyOf(activeRaids.values());
    }

    public boolean isChestBlock(Location loc) {
        if (loc == null) return false;
        for (ClanRaid raid : activeRaids.values()) {
            if (raid.state() == RaidState.ACTIVE && raid.chestLocation() != null) {
                Location cl = raid.chestLocation();
                if (cl.getWorld() != null && cl.getWorld().equals(loc.getWorld())
                        && cl.getBlockX() == loc.getBlockX()
                        && cl.getBlockY() == loc.getBlockY()
                        && cl.getBlockZ() == loc.getBlockZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInCaptureZone(Location loc, Location chestLoc, double radiusSq) {
        if (loc == null || chestLoc == null || loc.getWorld() == null || !loc.getWorld().equals(chestLoc.getWorld())) {
            return false;
        }
        double dx = loc.getX() - chestLoc.getX();
        double dz = loc.getZ() - chestLoc.getZ();
        double dy = loc.getY() - chestLoc.getY();
        return (dx * dx + dz * dz <= radiusSq) && (dy >= -2.0 && dy <= 4.0);
    }

    public boolean isInsideCaptureZone(Location loc) {
        if (loc == null) return false;
        double radius = plugin.getConfig().getDouble("raid.capture.radius", 5.0);
        double radiusSq = radius * radius;
        for (ClanRaid raid : activeRaids.values()) {
            if (raid.state() == RaidState.ACTIVE && raid.chestLocation() != null) {
                if (isInCaptureZone(loc, raid.chestLocation(), radiusSq)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Optional<ClanRaid> getActiveRaidAtChest(Location loc) {
        if (loc == null) return Optional.empty();
        for (ClanRaid raid : activeRaids.values()) {
            if (raid.state() == RaidState.ACTIVE && raid.chestLocation() != null) {
                Location cl = raid.chestLocation();
                if (cl.getWorld() != null && cl.getWorld().equals(loc.getWorld())
                        && cl.getBlockX() == loc.getBlockX()
                        && cl.getBlockY() == loc.getBlockY()
                        && cl.getBlockZ() == loc.getBlockZ()) {
                    return Optional.of(raid);
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Void> peaceAsync(Clan source, Clan target) {
        return plugin.supplySync(() -> {
            ClanRaid raid = findRaid(source.id(), target.id()).orElseThrow(() -> new IllegalStateException("war.not-at-war"));
            endRaid(raid, RaidResult.CANCELLED);
            onlineMembers(source).forEach(p -> plugin.getMessages().send(p, "war.peace", Map.of("tag", target.tag(), "color", target.tagColor())));
            onlineMembers(target).forEach(p -> plugin.getMessages().send(p, "war.peace", Map.of("tag", source.tag(), "color", source.tagColor())));
            return null;
        });
    }

    private void endRaid(ClanRaid raid, RaidResult result) {
        activeRaids.remove(raid.id());
        Optional<RaidClans> clansOpt = resolveClans(raid);
        clearBossBar(raid.id(), clansOpt.orElse(null));

        // The chest block and its label leave the world with the raid
        if (raid.chestLocation() != null) {
            Location cl = raid.chestLocation();
            Material orig = originalChestBlocks.remove(cl);
            if (orig == null) orig = Material.AIR;
            if (cl.getBlock().getType() == Material.CHEST) cl.getBlock().setType(orig);
            forgetChest(cl);
        }
        removeHologram(raid);

        settleLootBag(raid, result, clansOpt);

        // Изъятие компасов набега
        removeRaidCompasses(raid);

        if (clansOpt.isEmpty() || result == RaidResult.CANCELLED) {
            plugin.getConflictParticipants().forget(raid.id());
            return;
        }

        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();

        if (result == RaidResult.ATTACKER_WIN) {
            onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.victory-title", "raid.end.victory-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor())));
            onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.defeat-title", "raid.end.defeat-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor())));

            double multiplier = plugin.getConfig().getDouble("raid.win-exp-multiplier", 0.5);
            long reward = Math.round(plugin.getConfig().getLong("leveling.war-win-exp", 1200L) * multiplier);
            plugin.getClanManager().addExperienceAsync(attacker, reward).exceptionally(t -> {
                plugin.getLogger().warning("Failed to award raid experience to clan " + attacker.id() + ": " + t.getMessage());
                return null;
            });
            grantBonusItem(attacker);
            // The robbed clan may answer with a cheap casus belli
            plugin.getModifierManager().grantJustCasusBoth(defender.id(), attacker.id(), "revenge_raid");
        } else {
            double multiplier = plugin.getConfig().getDouble("raid.defend-win-exp-multiplier", 0.3);
            long reward = Math.round(plugin.getConfig().getLong("leveling.war-win-exp", 1200L) * multiplier);
            plugin.getClanManager().addExperienceAsync(defender, reward).exceptionally(t -> {
                plugin.getLogger().warning("Failed to award defend experience to clan " + defender.id() + ": " + t.getMessage());
                return null;
            });

            onlineMembers(defender).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.repelled-title", "raid.end.repelled-subtitle",
                    Map.of("tag", attacker.tag(), "color", attacker.tagColor())));
            onlineMembers(attacker).forEach(p -> plugin.getMessages().sendTitle(p, "raid.end.failed-title", "raid.end.failed-subtitle",
                    Map.of("tag", defender.tag(), "color", defender.tagColor())));
        }

        // Щит на защитника после любого завершённого набега на 12 часов (§8)
        int shieldHours = plugin.getConfig().getInt("raid.post-raid-shield-hours", 12);
        plugin.getModifierManager().grantPostRaidShield(defender.id(), shieldHours);

        plugin.getConflictArchive().record(ConflictKind.RAID, raid.attackerClanId(), raid.defenderClanId(),
                result == RaidResult.ATTACKER_WIN ? raid.attackerClanId() : raid.defenderClanId(),
                (int) raid.moneyLooted(), 0, raid.startedAt());

        me.lovelace.loveclans.activity.ConflictEvents.resolved(plugin, me.lovelace.loveclans.model.history.ConflictKind.RAID,
                raid.id(), raid.attackerClanId(), raid.defenderClanId(),
                result == RaidResult.ATTACKER_WIN ? me.lovelace.loveclans.api.events.ConflictOutcome.ATTACKER_WIN
                        : me.lovelace.loveclans.api.events.ConflictOutcome.DEFENDER_WIN);

        plugin.getClanManager().recordRaidResultAsync(attacker, result == RaidResult.ATTACKER_WIN).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record raid result for clan " + attacker.id() + ": " + t.getMessage());
            return null;
        });
        plugin.getClanManager().recordRaidResultAsync(defender, result != RaidResult.ATTACKER_WIN).exceptionally(t -> {
            plugin.getLogger().warning("Failed to record raid result for clan " + defender.id() + ": " + t.getMessage());
            return null;
        });
    }

    /**
     * A login picks up the raid state of the player's clan: the boss bar, the attacker's compass, and compasses
     * left over from raids that ended while the owner was offline are taken away.
     */
    public void syncPlayer(Player player) {
        Set<String> live = new java.util.HashSet<>();
        for (ClanRaid raid : activeRaids.values()) live.add(raid.id().toString());
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) continue;
            String tagged = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.RAID_COMPASS_KEY, PersistentDataType.STRING);
            if (tagged != null && !live.contains(tagged)) player.getInventory().setItem(i, null);
        }
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).flatMap(c -> activeRaids.values().stream()
                .filter(r -> r.involves(c.id())).findFirst().map(r -> Map.entry(c, r))).ifPresent(entry -> {
            ClanRaid raid = entry.getValue();
            BossBar bar = raidBossBars.get(raid.id());
            if (bar != null) player.showBossBar(bar);
            if (raid.state() == RaidState.ACTIVE && raid.chestLocation() != null
                    && raid.attackerClanId().equals(entry.getKey().id()) && !hasRaidCompass(player, raid.id())) {
                plugin.getClanManager().getClanById(raid.defenderClanId())
                        .ifPresent(defender -> giveRaidCompass(player, raid, raid.chestLocation(), defender));
            }
        });
    }

    private boolean hasRaidCompass(Player player, UUID raidId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                String tagged = item.getItemMeta().getPersistentDataContainer().get(ClanItemFactory.RAID_COMPASS_KEY, PersistentDataType.STRING);
                if (raidId.toString().equals(tagged)) return true;
            }
        }
        return false;
    }

    /** Called when a player leaves or is removed from a clan: bar and compass of that clan's raid go with them. */
    public void detachPlayer(Player player) {
        raidBossBars.values().forEach(player::hideBossBar);
        for (ClanRaid raid : activeRaids.values()) clearRaidCompass(player, raid.id());
    }

    private void removeHologram(ClanRaid raid) {
        if (raid.chestHologramId() == null) return;
        Entity holo = Bukkit.getEntity(raid.chestHologramId());
        if (holo == null && raid.chestLocation() != null && raid.chestLocation().getWorld() != null) {
            // The label may sit in an unloaded chunk: loading it exposes its entities through the chunk
            for (Entity e : raid.chestLocation().getChunk().getEntities()) {
                if (e.getUniqueId().equals(raid.chestHologramId())) { holo = e; break; }
            }
        }
        if (holo != null) holo.remove();
    }

    /** Pays the looters on a win; on a loss or a cancel the money goes back to the defender's treasury. */
    private void settleLootBag(ClanRaid raid, RaidResult result, Optional<RaidClans> clansOpt) {
        Map<UUID, Long> bag = lootBags.remove(raid.id());
        if (bag == null || bag.isEmpty() || clansOpt.isEmpty()) return;
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();
        long total = bag.values().stream().mapToLong(Long::longValue).sum();
        if (result != RaidResult.ATTACKER_WIN) {
            defender.addChestMoney(total);
            plugin.getStorage().updateClanChestMoney(defender.id(), defender.chestMoney());
            return;
        }
        var economy = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.LoveEconomy.class);
        long toTreasury = 0L;
        for (Map.Entry<UUID, Long> entry : bag.entrySet()) {
            Player looter = Bukkit.getPlayer(entry.getKey());
            if (looter != null && economy.isPresent()) {
                economy.get().give(looter, entry.getValue());
            } else {
                // Offline looter or no economy service: the share is kept for the attacking clan instead of vanishing
                toTreasury += entry.getValue();
            }
        }
        if (toTreasury > 0) {
            attacker.addChestMoney(toTreasury);
            plugin.getStorage().updateClanChestMoney(attacker.id(), attacker.chestMoney());
        }
    }

    private void grantBonusItem(Clan attacker) {
        List<String> bonusItems = plugin.getConfig().getStringList("raid.bonus-items");
        if (bonusItems.isEmpty()) return;
        Material material = Material.matchMaterial(bonusItems.get(ThreadLocalRandom.current().nextInt(bonusItems.size())));
        if (material == null) return;
        onlineMembers(attacker).findFirst().ifPresent(player -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material));
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            plugin.getMessages().send(player, "raid.bonus-item", Map.of("item", material.name()));
        });
    }

    public void purgeClan(UUID clanId) {
        raidCooldowns.keySet().removeIf(pair -> pair.getKey().equals(clanId) || pair.getValue().equals(clanId));
        attackerRaidTimestamps.remove(clanId);
        defenderRaidTimestamps.remove(clanId);
        plugin.getConflictCooldownStore().deleteClanAsync(clanId);
    }

    public void endActiveRaidsInvolvingClan(UUID clanId) {
        for (ClanRaid raid : activeRaids()) {
            if (raid.involves(clanId)) {
                endRaid(raid, RaidResult.CANCELLED);
            }
        }
    }

    public void shutdown() {
        for (ClanRaid raid : activeRaids()) {
            endRaid(raid, RaidResult.CANCELLED);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownDuration().toMillis();
        raidCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

        for (ClanRaid raid : activeRaids.values()) {
            if (raid.state() == RaidState.PREPARING) {
                tickPending(raid, now);
            } else if (raid.state() == RaidState.ACTIVE) {
                tickActive(raid, now);
            }
        }
    }

    private void tickPending(ClanRaid raid, long now) {
        long remainingMs = raid.endsAt() - now;
        Optional<RaidClans> clansOpt = resolveClans(raid);
        if (clansOpt.isEmpty()) {
            endRaid(raid, RaidResult.CANCELLED);
            return;
        }

        // Проверка отмены в PREPARING: если защитников не-AFK стало больше max-defender-online
        long afkMinutes = plugin.getConfig().getLong("raid.afk-ignore-minutes", 15L);
        int maxDefenderOnline = plugin.getConfig().getInt("raid.max-defender-online", 2);
        if (countOnlineNonAfk(clansOpt.get().defender(), afkMinutes) > maxDefenderOnline) {
            // Отменяем набег и ставим кулдаун пары на 2 часа
            AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(raid.attackerClanId(), raid.defenderClanId());
            long cancelCooldownTime = now - (cooldownDuration().toMillis() - preparingCancelCooldownDuration().toMillis());
            raidCooldowns.put(cooldownKey, cancelCooldownTime);
            plugin.getConflictCooldownStore().saveAsync(me.lovelace.loveclans.storage.ConflictCooldownStore.RAID, cooldownKey, cancelCooldownTime);

            java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "raid.pending.cancelled-defenders"));

            endRaid(raid, RaidResult.CANCELLED);
            return;
        }

        if (remainingMs <= 0) {
            activateRaid(raid);
            return;
        }

        BossBar bar = raidBossBars.get(raid.id());
        if (bar != null) {
            long totalMs = preStartDuration().toMillis();
            bar.progress(Math.max(0f, Math.min(1f, (float) remainingMs / (float) totalMs)));
            bar.name(plugin.getMessages().component("raid.pending.bossbar", Map.of(
                    "attacker", clansOpt.get().attacker().tag(), "color1", clansOpt.get().attacker().tagColor(),
                    "defender", clansOpt.get().defender().tag(), "color2", clansOpt.get().defender().tagColor(),
                    "time", formatDuration(remainingMs))));
        }

        if (remainingMs <= 60_000L && oneMinuteWarned.add(raid.id())) {
            java.util.stream.Stream.concat(onlineMembers(clansOpt.get().attacker()), onlineMembers(clansOpt.get().defender()))
                    .forEach(p -> plugin.getMessages().send(p, "raid.pending.one-minute-warning"));
        }
    }

    private void tickActive(ClanRaid raid, long now) {
        Optional<RaidClans> clansOpt = resolveClans(raid);
        if (clansOpt.isEmpty()) {
            endRaid(raid, RaidResult.CANCELLED);
            return;
        }
        Clan attacker = clansOpt.get().attacker();
        Clan defender = clansOpt.get().defender();
        Location chestLoc = raid.chestLocation();

        if (chestLoc == null) {
            endRaid(raid, RaidResult.CANCELLED);
            return;
        }

        double radius = plugin.getConfig().getDouble("raid.capture.radius", 5.0);
        double radiusSq = radius * radius;
        long afkMinutes = plugin.getConfig().getLong("raid.afk-ignore-minutes", 15L);

        // Подсчёт атакующих и защитников в цилиндре захвата
        int attackersInZone = 0;
        int defendersInZone = 0;
        for (Player p : onlineMembers(attacker).toList()) {
            if (isInCaptureZone(p.getLocation(), chestLoc, radiusSq)) {
                attackersInZone++;
                me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, raid.id(), p, attacker.id());
            }
        }
        for (Player p : onlineMembers(defender).toList()) {
            if (isInCaptureZone(p.getLocation(), chestLoc, radiusSq) && !plugin.getAfkManager().isAfkMinutes(p.getUniqueId(), afkMinutes)) {
                defendersInZone++;
                me.lovelace.loveclans.activity.ConflictEvents.mark(plugin, raid.id(), p, defender.id());
            }
        }

        // Партиклы периметра зоны захвата
        World world = chestLoc.getWorld();
        if (world != null) {
            for (int degree = 0; degree < 360; degree += 24) {
                double rad = Math.toRadians(degree);
                double px = chestLoc.getX() + 0.5 + radius * Math.cos(rad);
                double pz = chestLoc.getZ() + 0.5 + radius * Math.sin(rad);
                world.spawnParticle(raid.phase() == RaidPhase.LOOT ? Particle.HAPPY_VILLAGER : Particle.FLAME,
                        px, chestLoc.getY() + 0.1, pz, 1, 0, 0, 0, 0);
            }
            world.spawnParticle(Particle.ENCHANT, chestLoc.clone().add(0.5, 0.8, 0.5), 4, 0.2, 0.2, 0.2, 0.05);
        }

        long remainingActiveMs = Math.max(0L, raid.endsAt() - now);

        // Фаза 1: CAPTURE
        if (raid.phase() == RaidPhase.CAPTURE) {
            double currentProgress = raid.captureProgress();
            double delta;
            if (defendersInZone > 0) {
                delta = -plugin.getConfig().getDouble("raid.capture.defender-decay-per-second", 1.2);
            } else if (attackersInZone > 0) {
                double base = plugin.getConfig().getDouble("raid.capture.base-rate-per-second", 1.5);
                double perExtra = plugin.getConfig().getDouble("raid.capture.per-extra-attacker", 0.6);
                double maxRate = plugin.getConfig().getDouble("raid.capture.max-rate-per-second", 4.0);
                delta = Math.min(maxRate, base + (attackersInZone - 1) * perExtra);
            } else {
                delta = -plugin.getConfig().getDouble("raid.capture.passive-decay-per-second", 0.4);
            }

            double nextProgress = Math.max(0.0, Math.min(100.0, currentProgress + delta));
            ClanRaid updated = raid.withProgress(nextProgress);

            if (nextProgress >= 100.0) {
                // Взлом сундука! Переход в фазу LOOT
                updated = updated.withPhase(RaidPhase.LOOT);
                activeRaids.put(updated.id(), updated);
                updateChestHologram(updated);

                // Оповещения о взломе
                onlineMembers(attacker).forEach(p -> {
                    plugin.getMessages().sendTitle(p, "raid.loot.hacked-title", "raid.loot.hacked-subtitle",
                            Map.of("tag", defender.tag(), "color", defender.tagColor()));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                });
                onlineMembers(defender).forEach(p -> {
                    plugin.getMessages().sendTitle(p, "raid.loot.defender-breached-title", "raid.loot.defender-breached-subtitle",
                            Map.of("tag", attacker.tag(), "color", attacker.tagColor()));
                    p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 0.7f);
                });
            } else {
                activeRaids.put(updated.id(), updated);
                updateChestHologram(updated);
            }

            // Обновление BossBar
            BossBar bar = raidBossBars.get(raid.id());
            if (bar != null) {
                bar.color(nextProgress >= 50.0 ? BossBar.Color.YELLOW : BossBar.Color.RED);
                bar.progress((float) (nextProgress / 100.0));
                bar.name(plugin.getMessages().component("raid.active.bossbar.capture", Map.of(
                        "attacker", attacker.tag(), "color1", attacker.tagColor(),
                        "defender", defender.tag(), "color2", defender.tagColor(),
                        "progress", String.valueOf((int) nextProgress),
                        "time", formatDuration(remainingActiveMs))));
            }

            if (remainingActiveMs <= 0) {
                endRaid(raid, RaidResult.DEFENDER_WIN);
                return;
            }
        }
        // Фаза 2: LOOT и EXTRACT
        else if (raid.phase() == RaidPhase.LOOT) {
            double minMoneyPercent = plugin.getConfig().getDouble("raid.win-min-money-percent", 15.0);
            int minItemSlots = plugin.getConfig().getInt("raid.win-min-item-slots", 1);
            boolean hasSubstantial = raid.hasSubstantialLoot(minMoneyPercent, minItemSlots);

            if (hasSubstantial) {
                // Если взят substantial лут, проверяем выход атакующих из зоны сундука (extract)
                boolean allLeftZone = checkAllAttackersLeftZone(raid);
                if (allLeftZone) {
                    endRaid(raid, RaidResult.ATTACKER_WIN);
                    return;
                }

                // Таймер эвакуации 60 секунд после первого substantial забора
                long extractSeconds = plugin.getConfig().getLong("raid.extract.seconds-after-first-loot", 60L);
                if (raid.firstLootAt() > 0 && (now - raid.firstLootAt() >= extractSeconds * 1000L)) {
                    // Таймер эвакуации истёк, а атакующие не вышли из зоны!
                    endRaid(raid, RaidResult.DEFENDER_WIN);
                    return;
                }
            }

            BossBar bar = raidBossBars.get(raid.id());
            if (bar != null) {
                bar.color(BossBar.Color.YELLOW);
                bar.progress(1.0f);
                bar.name(plugin.getMessages().component("raid.active.bossbar.loot", Map.of(
                        "attacker", attacker.tag(), "color1", attacker.tagColor(),
                        "defender", defender.tag(), "color2", defender.tagColor(),
                        "time", formatDuration(remainingActiveMs))));
            }

            if (remainingActiveMs <= 0) {
                endRaid(raid, RaidResult.DEFENDER_WIN);
            }
        }
    }

    private boolean checkAllAttackersLeftZone(ClanRaid raid) {
        Location chestLoc = raid.chestLocation();
        if (chestLoc == null) return true;
        Optional<Clan> attackerOpt = plugin.getClanManager().getClanById(raid.attackerClanId());
        if (attackerOpt.isEmpty()) return true;
        List<Player> attackers = onlineMembers(attackerOpt.get()).toList();
        if (attackers.isEmpty()) return false;
        double radius = plugin.getConfig().getDouble("raid.capture.radius", 5.0);
        double radiusSq = radius * radius;
        for (Player p : attackers) {
            if (isInCaptureZone(p.getLocation(), chestLoc, radiusSq)) {
                return false;
            }
        }
        return true;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public java.util.stream.Stream<Player> onlineMembers(Clan clan) {
        return clan.members().values().stream()
                .map(ClanMember::playerId)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull);
    }

    public long getCooldownRemaining(UUID clan1, UUID clan2) {
        AbstractMap.SimpleImmutableEntry<UUID, UUID> cooldownKey = pairKey(clan1, clan2);
        Long last = raidCooldowns.get(cooldownKey);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        long duration = cooldownDuration().toMillis();
        return elapsed < duration ? (duration - elapsed) : 0;
    }

    public int activeRaidsCount() {
        return activeRaids.size();
    }
}
