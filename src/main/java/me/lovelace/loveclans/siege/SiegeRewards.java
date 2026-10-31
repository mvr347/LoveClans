package me.lovelace.loveclans.siege;

import com.google.gson.Gson;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.upgrade.ExperienceMath;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.model.modifier.ClanModifier;
import me.lovelace.loveclans.model.siege.ClanSiege;
import me.lovelace.loveclans.model.siege.SiegeResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * All economic effects and the settlement receipt are committed atomically; Bukkit stays on main.
 */
public final class SiegeRewards {
  private static final Gson JSON = new Gson();
  private final LoveClansPlugin plugin;
  private final SiegeRepository repository;

  public SiegeRewards(LoveClansPlugin plugin, SiegeRepository repository) {
    this.plugin = plugin;
    this.repository = repository;
  }

  public record Progress(
      UUID id, long money, int level, long xp, int points, int wins, int losses) {}

  private record Result(boolean fresh, List<Progress> progress) {}

  public CompletableFuture<Void> apply(
      ClanSiege siege, SiegeResult outcome, Clan attacker, Clan defender) {
    if (outcome == SiegeResult.CANCELLED) return CompletableFuture.completedFuture(null);
    boolean attackWin = outcome == SiegeResult.ATTACKER_WIN;
    Clan winner = attackWin ? attacker : defender, loser = attackWin ? defender : attacker;
    for (Player p : Bukkit.getOnlinePlayers())
      if (attacker.hasMember(p.getUniqueId()) || defender.hasMember(p.getUniqueId()))
        p.closeInventory();
    if (!plugin.getClanManager().tryLockItemChest(attacker.id()))
      return CompletableFuture.failedFuture(
          new IllegalStateException("Хранилище атакующих занято"));
    if (!plugin.getClanManager().tryLockItemChest(defender.id())) {
      plugin.getClanManager().unlockItemChest(attacker.id());
      return CompletableFuture.failedFuture(
          new IllegalStateException("Хранилище защитников занято"));
    }
    // Let existing close/write tasks finish before reading their committed chest state.
    CompletableFuture<Void> barrier =
        CompletableFuture.runAsync(() -> {}, plugin.getDatabaseManager().executor())
            .thenCompose(
                v ->
                    CompletableFuture.runAsync(
                        () -> {}, plugin.getDatabaseManager().chestMoneyExecutor()));
    return barrier
        .thenCompose(v -> plugin.getClanManager().loadChestContentsAsync(attacker))
        .thenCombine(
            plugin.getClanManager().loadChestContentsAsync(defender),
            (a, d) -> new ItemStack[][] {a, d})
        .thenCompose(
            chests ->
                plugin.supplySync(
                    () -> prepare(siege, outcome, attacker, defender, winner, loser, chests)))
        .thenCompose(
            prepared ->
                repository
                    .submit(
                        c -> {
                          boolean fresh =
                              SiegeRepository.once(
                                  c,
                                  siege.id(),
                                  "economic",
                                  tx -> {
                                    prepared.commit(tx);
                                    return null;
                                  });
                          List<Progress> values = new ArrayList<>();
                          for (UUID id : List.of(attacker.id(), defender.id()))
                            try (PreparedStatement ps =
                                c.prepareStatement(
                                    "SELECT"
                                        + " chest_money,level,experience,upgrade_points,sieges_won,sieges_lost"
                                        + " FROM clans WHERE id=?")) {
                              ps.setString(1, id.toString());
                              try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next())
                                  values.add(
                                      new Progress(
                                          id,
                                          rs.getLong(1),
                                          rs.getInt(2),
                                          rs.getLong(3),
                                          rs.getInt(4),
                                          rs.getInt(5),
                                          rs.getInt(6)));
                              }
                            }
                          return new Result(fresh, List.copyOf(values));
                        })
                    .thenCompose(
                        result ->
                            plugin.runSync(
                                () -> {
                                  for (Progress p : result.progress())
                                    plugin
                                        .getClanManager()
                                        .getClanById(p.id())
                                        .ifPresent(
                                            clan -> {
                                              int previousLevel = clan.level();
                                              clan.setChestMoney(p.money());
                                              clan.setLevel(p.level());
                                              clan.setExperience(p.xp());
                                              clan.setUpgradePoints(p.points());
                                              clan.setSiegesWon(p.wins());
                                              clan.setSiegesLost(p.losses());
                                              if (result.fresh() && p.level() > previousLevel)
                                                Bukkit.getPluginManager()
                                                    .callEvent(
                                                        new me.lovelace.loveclans.api.events
                                                            .ClanLevelUpEvent(
                                                            clan, previousLevel, p.level()));
                                            });
                                  plugin.getClanManager().invalidateSiegeChest(attacker.id());
                                  plugin.getClanManager().invalidateSiegeChest(defender.id());
                                  if (result.fresh()) {
                                    plugin
                                        .getModifierManager()
                                        .acceptSiegeModifiers(
                                            prepared.removeModifiers, prepared.modifiers);
                                    plugin
                                        .getMessages()
                                        .send(
                                            Bukkit.getConsoleSender(),
                                            "siege.models.settled",
                                            Map.of(
                                                "attacker",
                                                attacker.tag(),
                                                "defender",
                                                defender.tag()));
                                  }
                                  plugin.getConflictArchive().refreshFreshnessAsync(attacker.id());
                                  plugin.getConflictArchive().refreshFreshnessAsync(defender.id());
                                  plugin.getClanManager().recalculateInfluenceAsync(attacker);
                                  plugin.getClanManager().recalculateInfluenceAsync(defender);
                                })))
        .whenComplete(
            (v, e) ->
                plugin.runSync(
                    () -> {
                      plugin.getClanManager().unlockItemChest(attacker.id());
                      plugin.getClanManager().unlockItemChest(defender.id());
                    }));
  }

  private Prepared prepare(
      ClanSiege siege,
      SiegeResult outcome,
      Clan attacker,
      Clan defender,
      Clan winner,
      Clan loser,
      ItemStack[][] chests) {
    ItemStack[] a = chests[0].clone(), d = chests[1].clone();
    boolean win = outcome == SiegeResult.ATTACKER_WIN;
    if (win) {
      int al = me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(attacker.chestRows(), a.length),
          dl = me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(defender.chestRows(), d.length);
      List<Integer> occupied = new ArrayList<>();
      for (int i = 0; i < dl; i++) if (d[i] != null && !d[i].getType().isAir()) occupied.add(i);
      Collections.shuffle(occupied);
      int count =
          (int)
              Math.round(
                  occupied.size()
                      * plugin.getConfig().getDouble("siege.instant-loot.item-slot-percent", 20)
                      / 100);
      for (int i = 0; i < Math.min(count, occupied.size()); i++)
        for (int slot = 0; slot < al; slot++)
          if (a[slot] == null || a[slot].getType().isAir()) {
            a[slot] = d[occupied.get(i)];
            d[occupied.get(i)] = null;
            break;
          }
    }
    ItemStack artifact = null;
    if (win && plugin.getConfig().getBoolean("siege.reward-artifact", true)) {
      ArtifactType[] types = ArtifactType.values();
      artifact =
          plugin
              .getArtifactManager()
              .createArtifact(
                  types[java.util.concurrent.ThreadLocalRandom.current().nextInt(types.length)]);
      int limit =
          me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(attacker.chestRows(), a.length);
      for (int i = 0; i < limit; i++)
        if (a[i] == null || a[i].getType().isAir()) {
          a[i] = artifact;
          artifact = null;
          break;
        }
    }
    long now = System.currentTimeMillis();
    List<ClanModifier> modifiers = new ArrayList<>();
    List<UUID> remove = new ArrayList<>();
    if (win) {
      int days = Math.max(1, plugin.getConfig().getInt("siege.reparations.duration-days", 3));
      long daily =
          Math.max(
              plugin.getConfig().getLong("siege.reparations.min-daily", 100),
              Math.min(
                  plugin.getConfig().getLong("siege.reparations.max-daily", 2000),
                  plugin.getConfig().getLong("siege.reparations.daily-amount", 300)
                      + defender.level()
                          * plugin
                              .getConfig()
                              .getLong("siege.reparations.daily-per-defender-level", 40)));
      UUID debt = id(siege, "debt"), income = id(siege, "income");
      long until = now + (days + 1L) * 86400000;
      modifiers.add(
          new ClanModifier(
              debt,
              defender.id(),
              ClanModifier.TYPE_REPARATIONS_DEBT,
              Map.of(
                  "target_clan_id",
                  attacker.id().toString(),
                  "daily_amount",
                  "" + daily,
                  "days_left",
                  "" + days,
                  "arrears",
                  "0",
                  "last_ticked_at",
                  "" + now,
                  "paired_id",
                  income.toString()),
              now,
              until,
              1));
      modifiers.add(
          new ClanModifier(
              income,
              attacker.id(),
              ClanModifier.TYPE_REPARATIONS_INCOME,
              Map.of(
                  "target_clan_id",
                  defender.id().toString(),
                  "daily_amount",
                  "" + daily,
                  "days_left",
                  "" + days,
                  "paired_id",
                  debt.toString()),
              now,
              until,
              1));
      modifiers.add(
          new ClanModifier(
              id(siege, "pressure"),
              defender.id(),
              ClanModifier.TYPE_SIEGE_PRESSURE,
              Map.of(),
              now,
              now + 3 * 86400000L,
              1));
      for (String type : List.of("WAR", "SIEGE"))
        modifiers.add(
            new ClanModifier(
                id(siege, "revenge-" + type),
                defender.id(),
                ClanModifier.TYPE_JUST_CASUS,
                Map.of(
                    "target_clan_id",
                    attacker.id().toString(),
                    "conflict_type",
                    type,
                    "reason_id",
                    "revenge_siege"),
                now,
                now + 14 * 86400000L,
                1));
      for (Clan side : List.of(attacker, defender))
        for (ClanModifier mod : plugin.getModifierManager().getModifiers(side.id()))
          if ((ClanModifier.TYPE_REPARATIONS_DEBT.equals(mod.type())
                  || ClanModifier.TYPE_REPARATIONS_INCOME.equals(mod.type()))
              && (attacker.id().equals(mod.targetClanId())
                  || defender.id().equals(mod.targetClanId()))) remove.add(mod.id());
    } else
      modifiers.add(
          new ClanModifier(
              id(siege, "repelled"),
              defender.id(),
              ClanModifier.TYPE_SIEGE_REPELLED,
              Map.of(),
              now,
              now + 2 * 86400000L,
              1));
    long base = plugin.getConfig().getLong("leveling.war-win-exp", 1200);
    long xp =
        ExperienceMath.apply(
            Math.round(
                base
                    * plugin
                        .getConfig()
                        .getDouble(
                            win ? "siege.win-exp-multiplier" : "siege.defend-win-exp-multiplier",
                            win ? 0.75 : 0.45)),
            plugin.getClanBonuses().experiencePercent(winner),
            plugin.getClanManager().experienceTaxFactor(winner));
    int max = plugin.getConfig().getInt("limits.max-level", 20);
    long[] thresholds = new long[max + 2];
    for (int i = 1; i < thresholds.length; i++)
      thresholds[i] = plugin.getClanManager().experienceForLevel(i);
    double percent =
        win
            ? Math.max(
                0,
                Math.min(
                    plugin.getConfig().getDouble("siege.instant-loot.money-percent-cap", 35),
                    plugin.getConfig().getDouble("siege.instant-loot.money-percent-base", 20)
                        + defender.level()
                            * plugin
                                .getConfig()
                                .getDouble("siege.instant-loot.money-percent-per-level", 0.5)))
            : 0;
    return new Prepared(
        siege,
        winner.id(),
        loser.id(),
        win,
        bytes(a),
        bytes(d),
        artifact == null ? null : bytes(new ItemStack[] {artifact}),
        List.copyOf(modifiers),
        List.copyOf(remove),
        percent,
        xp,
        max,
        thresholds,
        now);
  }

  private record Parcel(UUID siege, UUID clan, byte[] payload) {}

  public CompletableFuture<Void> deliverPending() {
    return repository
        .submit(
            c -> {
              List<Parcel> parcels = new ArrayList<>();
              try (Statement st = c.createStatement();
                  ResultSet rs =
                      st.executeQuery(
                          "SELECT siege_id,clan_id,payload FROM clan_siege_artifacts LIMIT 20")) {
                while (rs.next())
                  parcels.add(
                      new Parcel(
                          UUID.fromString(rs.getString(1)),
                          UUID.fromString(rs.getString(2)),
                          rs.getBytes(3)));
              }
              return parcels;
            })
        .thenCompose(
            parcels -> {
              CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
              for (Parcel parcel : parcels) chain = chain.thenCompose(v -> deliver(parcel));
              return chain;
            });
  }

  private CompletableFuture<Void> deliver(Parcel parcel) {
    return plugin
        .supplySync(
            () ->
                plugin
                    .getClanManager()
                    .getClanById(parcel.clan())
                    .filter(c -> plugin.getClanManager().tryLockItemChest(c.id()))
                    .orElse(null))
        .thenCompose(
            clan -> {
              if (clan == null) return CompletableFuture.completedFuture(null);
              return plugin
                  .getClanManager()
                  .loadChestContentsAsync(clan)
                  .thenCompose(
                      source ->
                          plugin.supplySync(
                              () -> {
                                ItemStack[] items = source.clone();
                                int max =
                                    me.lovelace.loveclans.gui.ChestLayout.unlockedSlots(
                                        clan.chestRows(), items.length);
                                ItemStack[] parcelItems =
                                    me.lovelace.loveclans.util.InventorySerialization.deserialize(
                                        parcel.payload(), 1);
                                for (int i = 0; i < max; i++)
                                  if (items[i] == null || items[i].getType().isAir()) {
                                    items[i] = parcelItems[0];
                                    return bytes(items);
                                  }
                                return null;
                              }))
                  .thenCompose(
                      bytes -> {
                        if (bytes == null) return CompletableFuture.completedFuture(false);
                        return repository.once(
                            parcel.siege(),
                            "parcel",
                            c -> {
                              Prepared.chest(c, parcel.clan(), bytes);
                              try (PreparedStatement ps =
                                  c.prepareStatement(
                                      "DELETE FROM clan_siege_artifacts WHERE siege_id=?")) {
                                ps.setString(1, parcel.siege().toString());
                                ps.executeUpdate();
                              }
                              return null;
                            });
                      })
                  .thenCompose(
                      done ->
                          plugin.runSync(
                              () -> {
                                if (done)
                                  plugin.getClanManager().invalidateSiegeChest(parcel.clan());
                              }))
                  .whenComplete(
                      (v, e) ->
                          plugin.runSync(
                              () -> plugin.getClanManager().unlockItemChest(parcel.clan())));
            });
  }

  private static UUID id(ClanSiege siege, String step) {
    return UUID.nameUUIDFromBytes(
        (siege.id() + ":" + step).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  record Prepared(
      ClanSiege siege,
      UUID winner,
      UUID loser,
      boolean attackWin,
      byte[] attackerChest,
      byte[] defenderChest,
      byte[] pendingArtifact,
      List<ClanModifier> modifiers,
      List<UUID> removeModifiers,
      double percent,
      long xp,
      int maxLevel,
      long[] thresholds,
      long now) {
    void commit(Connection c) throws SQLException {
      if (attackWin) {
        long balance;
        try (PreparedStatement ps =
            c.prepareStatement("SELECT chest_money FROM clans WHERE id=?")) {
          ps.setString(1, siege.defenderClanId().toString());
          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("Missing defender");
            balance = rs.getLong(1);
          }
        }
        long amount = Math.round(balance * percent / 100);
        money(c, siege.defenderClanId(), -amount);
        money(c, siege.attackerClanId(), amount);
        chest(c, siege.attackerClanId(), attackerChest);
        chest(c, siege.defenderClanId(), defenderChest);
        if (pendingArtifact != null)
          try (PreparedStatement ps =
              c.prepareStatement(
                  "INSERT INTO clan_siege_artifacts(siege_id,clan_id,payload) VALUES(?,?,?)")) {
            ps.setString(1, siege.id().toString());
            ps.setString(2, siege.attackerClanId().toString());
            ps.setBytes(3, pendingArtifact);
            ps.executeUpdate();
          }
      }
      try (PreparedStatement ps =
          c.prepareStatement("SELECT level,experience,upgrade_points FROM clans WHERE id=?")) {
        ps.setString(1, winner.toString());
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) throw new SQLException("Missing winner");
          int level = rs.getInt(1), points = rs.getInt(3);
          long exp = rs.getLong(2) + xp;
          while (level < maxLevel && exp >= thresholds[level + 1]) {
            level++;
            points++;
          }
          try (PreparedStatement up =
              c.prepareStatement(
                  "UPDATE clans SET level=?,experience=?,upgrade_points=?,sieges_won=sieges_won+1"
                      + " WHERE id=?")) {
            up.setInt(1, level);
            up.setLong(2, exp);
            up.setInt(3, points);
            up.setString(4, winner.toString());
            up.executeUpdate();
          }
        }
      }
      try (PreparedStatement ps =
          c.prepareStatement("UPDATE clans SET sieges_lost=sieges_lost+1 WHERE id=?")) {
        ps.setString(1, loser.toString());
        ps.executeUpdate();
      }
      for (UUID id : removeModifiers)
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_modifiers WHERE id=?")) {
          ps.setString(1, id.toString());
          ps.executeUpdate();
        }
      for (ClanModifier m : modifiers)
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO clan_modifiers(id,clan_id,type,payload,started_at,ends_at,stacks)"
                    + " VALUES(?,?,?,?,?,?,?)")) {
          ps.setString(1, m.id().toString());
          ps.setString(2, m.clanId().toString());
          ps.setString(3, m.type());
          ps.setString(4, JSON.toJson(m.payload()));
          ps.setLong(5, m.startedAt());
          ps.setLong(6, m.endsAt());
          ps.setInt(7, 1);
          ps.executeUpdate();
        }
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO"
                  + " clan_conflicts(id,kind,attacker_clan_id,defender_clan_id,winner_clan_id,attacker_score,defender_score,started_at,ended_at)"
                  + " VALUES(?,'SIEGE',?,?,?,0,0,?,?)")) {
        ps.setString(1, siege.id().toString());
        ps.setString(2, siege.attackerClanId().toString());
        ps.setString(3, siege.defenderClanId().toString());
        ps.setString(4, winner.toString());
        ps.setLong(5, siege.startedAt());
        ps.setLong(6, now);
        ps.executeUpdate();
      }
    }

    private static void money(Connection c, UUID id, long delta) throws SQLException {
      try (PreparedStatement ps =
          c.prepareStatement("UPDATE clans SET chest_money=chest_money+? WHERE id=?")) {
        ps.setLong(1, delta);
        ps.setString(2, id.toString());
        ps.executeUpdate();
      }
    }

    private static void chest(Connection c, UUID id, byte[] bytes) throws SQLException {
      try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_chest WHERE clan_id=?")) {
        ps.setString(1, id.toString());
        ps.executeUpdate();
      }
      try (PreparedStatement ps =
          c.prepareStatement("INSERT INTO clan_chest(clan_id,contents) VALUES(?,?)")) {
        ps.setString(1, id.toString());
        ps.setBytes(2, bytes);
        ps.executeUpdate();
      }
    }
  }

  public static byte[] bytes(ItemStack[] items) {
    try (ByteArrayOutputStream b = new ByteArrayOutputStream();
        BukkitObjectOutputStream out = new BukkitObjectOutputStream(b)) {
      out.writeInt(items.length);
      for (ItemStack item : items) out.writeObject(item);
      out.flush();
      return b.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
