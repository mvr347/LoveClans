package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import org.bukkit.Location;
import org.bukkit.Material;

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

    // --- Clan bank / treasury (ItemsAdder items) ---

    CompletableFuture<Long> adjustBankAmountAsync(UUID clanId, String itemId, long delta);

    CompletableFuture<Boolean> withdrawBankAmountAsync(UUID clanId, String itemId, long amount);
}