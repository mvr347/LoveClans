package me.lovelace.loveclans.storage;

import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.DiplomacyRelation;

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
}