package me.lovelace.loveclans.model;

import org.bukkit.Location; // Import Location
import org.bukkit.Material;

import me.lovelace.loveclans.model.diplomacy.ClanRelationRecord;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import me.lovelace.loveclans.model.diplomacy.PactType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Clan {
    /** Storage rows of the clan chest; the sixth row of the 54-slot screen is the gui_gen footer. */
    public static final int MAX_CHEST_ROWS = 5;

    private final UUID id;
    private final long createdAt;
    private String name;
    private String tag;
    private String tagColor;
    private String description;
    private Material emblem;
    private int level;
    private long experience;
    private int upgradePoints;
    private int chestRows;
    private ClanSpirit spirit;
    private boolean open;
    private Location homeLocation; // New field for home location

    // --- Influence (§8): raw counters used to derive the cached influence value ---
    private int warsWon;
    private int warsLost;
    private int siegesWon;
    private int siegesLost;
    private int raidsWon;
    private int raidsLost;
    private long influence;

    // --- Perk (§7): unlocked at level >= perks.unlock-level, one active at a time ---
    private ClanDirection direction;
    private long directionChosenAt;

    // --- Клановый сундук (§2): деньги хранятся отдельным полем прямо на клане, а не как
    // произвольный предметный банк - в сундуке ровно одна валюта (clans.chest.currency-item).
    private long chestMoney;
    private long lastTaxAt;
    private boolean chestTaxLocked;

    // --- Признанные и непризнанные кланы, налоги и торговля с сервером ---
    private boolean recognized;
    private long unpaidTaxSince;
    private int serverTradeWeeklyStacks;
    private int serverTradeWeek;

    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();
    private final Map<UUID, ClanTerritory> territories = new ConcurrentHashMap<>();

    private final Map<UUID, ClanRelationRecord> relations = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PactType>> pacts = new ConcurrentHashMap<>();
    private final Map<ClanUpgrade, Integer> upgrades = new ConcurrentHashMap<>();
    private final Map<ClanRank, Set<ClanPermission>> permissions = new ConcurrentHashMap<>();

    public Clan(UUID id,
                String name,
                String tag,
                String tagColor,
                String description,
                Material emblem,
                int level,
                long experience,
                int upgradePoints,
                int chestRows,
                ClanSpirit spirit,
                long createdAt,
                boolean open,
                Location homeLocation) { // Added homeLocation to constructor
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.tagColor = tagColor;
        this.description = description;
        this.emblem = emblem;
        this.level = level;
        this.experience = experience;
        this.upgradePoints = upgradePoints;
        this.chestRows = Math.min(chestRows, MAX_CHEST_ROWS);
        this.spirit = spirit;
        this.createdAt = createdAt;
        this.open = open;
        this.homeLocation = homeLocation; // Initialize homeLocation
        this.lastTaxAt = createdAt;
        for (ClanUpgrade upgrade : ClanUpgrade.values()) {
            upgrades.put(upgrade, 0);
        }
        // Права по умолчанию. Глава получает всё неявно (см. hasPermission), остальные ранги
        // раздаются лесенкой: раньше Хранитель получал сразу все права и был вторым главой —
        // мог распустить настройки, распоряжаться казной и объявлять войну, — а у Со-клановца
        // было единственное право BUILD, которое к тому же нигде не проверялось.
        for (ClanRank rank : ClanRank.values()) {
            permissions.put(rank, EnumSet.noneOf(ClanPermission.class));
        }

        // Новичок: осматривается, ничего не может.

        // Со-клановец: строит на своей земле и тянет клановые обеты.
        permissions.get(ClanRank.MEMBER).addAll(EnumSet.of(
                ClanPermission.BUILD,
                ClanPermission.CONTRACTS));

        // Хранитель: работа с людьми и землёй плюс торговля.
        // Казна, настройки, апгрейды и дипломатия остаются за главой — это решения,
        // которые нельзя откатить, и раздавать их по умолчанию неправильно.
        permissions.get(ClanRank.GUARDIAN).addAll(EnumSet.of(
                ClanPermission.BUILD,
                ClanPermission.CONTRACTS,
                ClanPermission.INVITE,
                ClanPermission.KICK,
                ClanPermission.CLAIM,
                ClanPermission.TRADE));
    }

    public static Clan create(UUID id, String name, String tag, String tagColor, Material emblem, UUID founderId, int chestRows, boolean open) {
        // homeLocation is null initially, will be set when the capital banner is placed
        Clan clan = new Clan(id, name, tag, tagColor, "", emblem, 1, 0L, 0, chestRows, ClanSpirit.fresh(), System.currentTimeMillis(), open, null);
        clan.addMember(founderId, ClanRank.LEADER);
        return clan;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String tag() {
        return tag;
    }

    public String tagColor() {
        return tagColor;
    }

    public String coloredTag() {
        return tagColor + tag;
    }

    public String coloredName() {
        return tagColor + name;
    }

    public String description() {
        return description;
    }

    public Material emblem() {
        return emblem;
    }

    public int level() {
        return level;
    }

    public long experience() {
        return experience;
    }
    
    public int upgradePoints() {
        return upgradePoints;
    }

    public int chestRows() {
        return chestRows;
    }

    public ClanSpirit spirit() {
        return spirit;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public Map<UUID, ClanMember> members() {
        return Collections.unmodifiableMap(members);
    }

    public Collection<ClanTerritory> territories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    public Map<UUID, ClanRelationRecord> relations() {
        return Collections.unmodifiableMap(relations);
    }

    public Map<ClanUpgrade, Integer> upgrades() {
        return Collections.unmodifiableMap(new EnumMap<>(upgrades));
    }

    public Optional<ClanMember> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public boolean hasMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isMember(UUID playerId) {
        return hasMember(playerId);
    }

    public Optional<ClanTerritory> getCapitalTerritory() {
        return territories.values().stream().filter(ClanTerritory::isCapital).findFirst();
    }

    /**
     * Есть ли у клана установленная капитальная территория. Раньше это пересчитывалось ad hoc в
     * нескольких местах (`territories().stream().anyMatch(ClanTerritory::isCapital)` или
     * `getCapitalTerritory().isPresent()`) — вынесено сюда, чтобы у всех вызывающих был один
     * источник истины.
     */
    public boolean hasCapital() {
        return getCapitalTerritory().isPresent();
    }
    
    public boolean hasPermission(UUID playerId, ClanPermission permission) {
        Optional<ClanMember> member = member(playerId);
        if (member.isEmpty()) return false;
        if (member.get().rank() == ClanRank.LEADER) return true;
        return permissions.getOrDefault(member.get().rank(), EnumSet.noneOf(ClanPermission.class)).contains(permission);
    }
    
    public void setPermission(ClanRank rank, ClanPermission permission, boolean value) {
        Set<ClanPermission> perms = permissions.computeIfAbsent(rank, k -> EnumSet.noneOf(ClanPermission.class));
        if (value) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
    }
    
    public boolean getPermission(ClanRank rank, ClanPermission permission) {
        return permissions.getOrDefault(rank, EnumSet.noneOf(ClanPermission.class)).contains(permission);
    }

    public Optional<UUID> leaderId() {
        return members.values().stream()
                .filter(member -> member.rank() == ClanRank.LEADER)
                .map(ClanMember::playerId)
                .findFirst();
    }

    public void addMember(UUID playerId, ClanRank rank) {
        long now = System.currentTimeMillis();
        members.put(playerId, new ClanMember(playerId, rank, now, now));
    }

    public void putMember(ClanMember member) {
        members.put(member.playerId(), member);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public void setRank(UUID playerId, ClanRank rank) {
        ClanMember current = members.get(playerId);
        if (current != null) {
            members.put(playerId, current.withRank(rank));
        }
    }

    public void markSeen(UUID playerId, long timestamp) {
        ClanMember current = members.get(playerId);
        if (current != null) {
            members.put(playerId, current.withLastSeen(timestamp));
        }
    }

    public void addTerritory(ClanTerritory territory) {
        territories.put(territory.id(), territory);
    }

    public void removeTerritory(UUID id) {
        territories.remove(id);
    }

    public Optional<ClanTerritory> territory(UUID id) {
        return Optional.ofNullable(territories.get(id));
    }

    /** Legacy view of the relation, derived from the shared score and the alliance pact (never stored). */
    public DiplomacyRelation relationTo(UUID targetClanId) {
        return tierTo(targetClanId).toLegacyRelation();
    }

    public DiplomacyTier tierTo(UUID targetClanId) {
        return DiplomacyTier.fromScore(relationScore(targetClanId), hasPactWith(targetClanId, PactType.ALLIANCE));
    }

    public int relationScore(UUID targetClanId) {
        ClanRelationRecord record = relations.get(targetClanId);
        return record != null ? record.score() : 0;
    }

    public ClanRelationRecord getRelation(UUID targetClanId) {
        return relations.getOrDefault(targetClanId, ClanRelationRecord.createDefault(id, targetClanId));
    }

    public void setRelation(ClanRelationRecord record) {
        relations.put(record.targetClanId(), record);
    }

    public void removeRelation(UUID targetClanId) {
        relations.remove(targetClanId);
    }

    public boolean hasPactWith(UUID targetClanId, PactType type) {
        Set<PactType> clanPacts = pacts.get(targetClanId);
        return clanPacts != null && clanPacts.contains(type);
    }

    /** Number of clans this clan has an active alliance pact with. */
    public int allianceCount() {
        int count = 0;
        for (Set<PactType> clanPacts : pacts.values()) {
            if (clanPacts.contains(PactType.ALLIANCE)) count++;
        }
        return count;
    }

    public void addPact(UUID targetClanId, PactType type) {
        pacts.computeIfAbsent(targetClanId, k -> ConcurrentHashMap.newKeySet()).add(type);
    }

    public void removePact(UUID targetClanId, PactType type) {
        Set<PactType> clanPacts = pacts.get(targetClanId);
        if (clanPacts != null) {
            clanPacts.remove(type);
            if (clanPacts.isEmpty()) {
                pacts.remove(targetClanId);
            }
        }
    }

    /** Drops every relation, pact and pending state that points at a clan that no longer exists. */
    public void forgetClan(UUID otherClanId) {
        relations.remove(otherClanId);
        pacts.remove(otherClanId);
    }

    public int upgradeLevel(ClanUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void setUpgradeLevel(ClanUpgrade upgrade, int level) {
        upgrades.put(upgrade, Math.max(0, level));
    }

    public long addExperience(long amount) {
        experience = Math.max(0L, experience + amount);
        return experience;
    }
    
    public void removeExperience(long amount) {
        experience = Math.max(0L, experience - amount);
    }

    public void levelUp() {
        level++;
        upgradePoints++; // Give one upgrade point per level
    }

    public void setExperience(long experience) {
        this.experience = Math.max(0L, experience);
    }
    
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }
    
    public void setUpgradePoints(int points) {
        this.upgradePoints = Math.max(0, points);
    }
    
    public void addUpgradePoints(int points) {
        this.upgradePoints = Math.max(0, this.upgradePoints + points);
    }
    
    public void removeUpgradePoints(int points) {
        this.upgradePoints = Math.max(0, this.upgradePoints - points);
    }

    public void setSpirit(ClanSpirit spirit) {
        this.spirit = spirit;
    }

    public void setChestRows(int chestRows) {
        this.chestRows = Math.min(chestRows, MAX_CHEST_ROWS);
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void setTagColor(String tagColor) {
        this.tagColor = tagColor;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setEmblem(Material emblem) {
        this.emblem = emblem;
    }

    // New getter for homeLocation
    public Optional<Location> getHomeLocation() {
        return Optional.ofNullable(homeLocation);
    }

    // New setter for homeLocation
    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }

    // --- Клановый сундук: деньги (§2) ---

    public long chestMoney() {
        return chestMoney;
    }

    public void setChestMoney(long amount) {
        this.chestMoney = Math.max(0L, amount);
    }

    public long addChestMoney(long delta) {
        this.chestMoney = Math.max(0L, this.chestMoney + delta);
        return this.chestMoney;
    }

    public long lastTaxAt() {
        return lastTaxAt;
    }

    public boolean isChestTaxLocked() {
        return chestTaxLocked;
    }

    public void setTaxState(long lastTaxAt, boolean locked) {
        this.lastTaxAt = lastTaxAt;
        this.chestTaxLocked = locked;
    }

    // --- Influence (§8) ---

    public int warsWon() {
        return warsWon;
    }

    public int warsLost() {
        return warsLost;
    }

    public int siegesWon() {
        return siegesWon;
    }

    public int siegesLost() {
        return siegesLost;
    }

    public int raidsWon() {
        return raidsWon;
    }

    public int raidsLost() {
        return raidsLost;
    }

    public void setWarsWon(int value) {
        this.warsWon = Math.max(0, value);
    }

    public void setWarsLost(int value) {
        this.warsLost = Math.max(0, value);
    }

    public void setSiegesWon(int value) {
        this.siegesWon = Math.max(0, value);
    }

    public void setSiegesLost(int value) {
        this.siegesLost = Math.max(0, value);
    }

    public void setRaidsWon(int value) {
        this.raidsWon = Math.max(0, value);
    }

    public void setRaidsLost(int value) {
        this.raidsLost = Math.max(0, value);
    }

    public void addWarResult(boolean won) {
        if (won) warsWon++; else warsLost++;
    }

    public void addSiegeResult(boolean won) {
        if (won) siegesWon++; else siegesLost++;
    }

    public void addRaidResult(boolean won) {
        if (won) raidsWon++; else raidsLost++;
    }

    public long influence() {
        return influence;
    }

    public void setInfluence(long influence) {
        this.influence = Math.max(0L, influence);
    }

    // --- Development direction (chosen once, never reset) ---

    public Optional<ClanDirection> direction() {
        return Optional.ofNullable(direction);
    }

    public long directionChosenAt() {
        return directionChosenAt;
    }

    public void setDirection(ClanDirection direction, long chosenAt) {
        this.direction = direction;
        this.directionChosenAt = chosenAt;
    }

    // --- Признание и статус клана ---

    public boolean isRecognized() {
        return recognized;
    }

    public void setRecognized(boolean recognized) {
        this.recognized = recognized;
    }

    public long getUnpaidTaxSince() {
        return unpaidTaxSince;
    }

    public void setUnpaidTaxSince(long unpaidTaxSince) {
        this.unpaidTaxSince = unpaidTaxSince;
    }

    public int getServerTradeWeeklyStacks() {
        return serverTradeWeeklyStacks;
    }

    public void setServerTradeWeeklyStacks(int serverTradeWeeklyStacks) {
        this.serverTradeWeeklyStacks = Math.max(0, serverTradeWeeklyStacks);
    }

    public void incrementServerTradeWeeklyStacks(int delta) {
        this.serverTradeWeeklyStacks = Math.max(0, this.serverTradeWeeklyStacks + delta);
    }

    public int getServerTradeWeek() {
        return serverTradeWeek;
    }

    public void setServerTradeWeek(int serverTradeWeek) {
        this.serverTradeWeek = serverTradeWeek;
    }
}
