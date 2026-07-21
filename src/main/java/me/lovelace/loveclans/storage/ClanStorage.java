package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanPerk;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.diplomacy.ClanLetter;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ClanStorage {
    CompletableFuture<Collection<Clan>> loadAllClansAsync();

    CompletableFuture<Void> saveClanAsync(Clan clan);

    CompletableFuture<Void> deleteClanAsync(UUID clanId);

    CompletableFuture<Void> saveMemberAsync(UUID clanId, ClanMember member);

    CompletableFuture<Void> deleteMemberAsync(UUID clanId, UUID playerId);

    CompletableFuture<Void> saveTerritoryAsync(ClanTerritory territory);

    CompletableFuture<Void> deleteTerritoryAsync(UUID territoryId);

    CompletableFuture<Void> saveDiplomacyAsync(UUID sourceClanId, UUID targetClanId, DiplomacyRelation relation);

    CompletableFuture<Void> saveUpgradeAsync(UUID clanId, ClanUpgrade upgrade, int level);

    CompletableFuture<Collection<ClanApplication>> loadAllApplicationsAsync();

    CompletableFuture<Void> saveApplicationAsync(ClanApplication application);

    CompletableFuture<Void> deleteApplicationAsync(UUID clanId, UUID applicantId);

    CompletableFuture<Void> deleteAllApplicationsForClanAsync(UUID clanId);

    // --- Lightweight, single-field clan updates (avoid re-persisting the whole clan
    // graph via saveClanAsync just to change one column). ---

    CompletableFuture<Void> updateClanName(UUID clanId, String name);

    CompletableFuture<Void> updateClanTag(UUID clanId, String tag);

    CompletableFuture<Void> updateClanTagColor(UUID clanId, String tagColor);

    CompletableFuture<Void> updateClanEmblem(UUID clanId, Material emblem);

    CompletableFuture<Void> updateClanOpenStatus(UUID clanId, boolean open);

    CompletableFuture<Void> updateClanUpgradePoints(UUID clanId, int upgradePoints);

    CompletableFuture<Void> updateClanHomeLocation(UUID clanId, Location homeLocation);

    CompletableFuture<Void> updateClanSpiritAbility(UUID clanId, SpiritAbility ability, long chosenAt);

    CompletableFuture<Void> updateClanProgression(UUID clanId, int level, long experience, int upgradePoints, int spiritLevel);

    CompletableFuture<Void> updateClanInfluenceStats(UUID clanId, int warsWon, int warsLost, int siegesWon,
                                                       int siegesLost, int raidsWon, int raidsLost, long influence);

    CompletableFuture<Void> updateClanPerk(UUID clanId, ClanPerk perk, long chosenAt);

    // --- Клановый сундук: деньги и налог (§2; заменяет прежний отдельный /clan bank) ---

    CompletableFuture<Void> updateClanChestMoney(UUID clanId, long amount);

    CompletableFuture<Void> updateClanTaxState(UUID clanId, long lastTaxAt, boolean locked);

    CompletableFuture<Long> migrateLegacyBankMoneyAsync(UUID clanId, String currencyItemId);

    // --- Clan chest (physical item storage) ---

    CompletableFuture<Void> updateClanChestRows(UUID clanId, int chestRows);

    CompletableFuture<Void> saveChestContentsAsync(UUID clanId, byte[] contents);

    CompletableFuture<byte[]> loadChestContentsAsync(UUID clanId);

    // --- Clan contracts: separate weekly/daily pools with independent active slots (§1) ---

    CompletableFuture<Void> saveContractProgressAsync(ClanQuestProgress progress);

    CompletableFuture<Void> deleteContractProgressAsync(UUID clanId, ContractType type);

    CompletableFuture<Collection<ClanQuestProgress>> loadAllContractsAsync(ContractType type);

    // --- Дипломатия: эмбарго, блокада, письма (§5) ---

    CompletableFuture<Collection<AbstractMap.SimpleImmutableEntry<UUID, UUID>>> loadAllEmbargoesAsync();

    CompletableFuture<Void> saveEmbargoAsync(UUID clanA, UUID clanB);

    CompletableFuture<Void> deleteEmbargoAsync(UUID clanA, UUID clanB);

    CompletableFuture<Collection<AbstractMap.SimpleImmutableEntry<UUID, UUID>>> loadAllBlockadesAsync();

    CompletableFuture<Void> saveBlockadeAsync(UUID blockerClanId, UUID blockedClanId);

    CompletableFuture<Void> deleteBlockadeAsync(UUID blockerClanId, UUID blockedClanId);

    CompletableFuture<Void> saveLetterAsync(ClanLetter letter);

    CompletableFuture<Collection<ClanLetter>> loadLettersBetweenAsync(UUID clanA, UUID clanB);

    CompletableFuture<Void> markLetterReadAsync(UUID letterId);
}