package me.lovelace.loveclans.siege;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.siege.SiegeState;

/** Immutable wire/storage data; contains no Bukkit objects. Durations exclude server downtime. */
public record SiegeSnapshot(
    int format,
    UUID id,
    UUID attacker,
    UUID defender,
    TerritoryKey territory,
    long startedAt,
    long paidFee,
    String refundScroll,
    SiegeState phase,
    long remaining,
    long hold,
    long wipeElapsed,
    List<Camp> camps,
    Map<UUID, UUID> participants,
    String outcome,
    long revision) {
  public SiegeSnapshot {
    camps = List.copyOf(camps);
    participants = Map.copyOf(participants);
  }

  public SiegeSnapshot withOutcome(String result) {
    return new SiegeSnapshot(
        format,
        id,
        attacker,
        defender,
        territory,
        startedAt,
        paidFee,
        refundScroll,
        phase,
        remaining,
        hold,
        wipeElapsed,
        camps,
        participants,
        result,
        revision);
  }

  public me.lovelace.loveclans.model.siege.ClanSiege resume(long now) {
    return new me.lovelace.loveclans.model.siege.ClanSiege(
        id,
        attacker,
        defender,
        territory,
        startedAt,
        now + Math.max(0, remaining),
        phase,
        camps.stream().map(c -> c.resume(now)).toList());
  }

  public record Cell(String world, int x, int y, int z, String original) {}

  public record Camp(
      int index,
      String world,
      int x,
      int y,
      int z,
      CampLayout.Direction direction,
      int level,
      int hits,
      boolean broken,
      long respawnRemaining,
      List<Cell> cells) {
    public Camp {
      cells = List.copyOf(cells);
    }

    public me.lovelace.loveclans.model.siege.SiegeCamp resume(long now) {
      return new me.lovelace.loveclans.model.siege.SiegeCamp(
          index, world, x, y, z, now, broken, broken ? now + Math.max(0, respawnRemaining) : 0);
    }
  }
}
