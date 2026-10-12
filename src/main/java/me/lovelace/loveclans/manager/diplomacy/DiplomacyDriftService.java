package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.diplomacy.ClanRelationRecord;
import me.lovelace.loveclans.model.diplomacy.PactType;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Сервис пассивного дрейфа отношений между кланами (§2).
 * Отношения медленно дрейфуют к 0 со временем:
 * <ul>
 *   <li>score > 0 → медленно к 0 (-1)</li>
 *   <li>score < 0 → медленно к 0 (+1)</li>
 *   <li>при торговом соглашении: положительный дрейф укрепляется, падение замедлено</li>
 *   <li>при блокаде/эмбарго: рост заморожен, падение ускорено</li>
 *   <li>во время войны/осады/рейда: дрейф к нулю заморожен</li>
 * </ul>
 * Также сервис обновляет 24-часовой базовый score для расчета тренда (▲/▼).
 */
public final class DiplomacyDriftService {
    private final LoveClansPlugin plugin;
    private BukkitTask task;

    public DiplomacyDriftService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        int intervalMinutes = Math.max(1, plugin.getConfig().getInt("diplomacy.drift-interval-minutes", 30));
        long intervalTicks = 20L * 60L * intervalMinutes;

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tickDrift();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при выполнении пассивного дрейфа дипломатии", t);
            }
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Выполняет один такт дрейфа: каждая пара кланов обрабатывается один раз (счёт общий для пары).
     */
    public void tickDrift() {
        long now = System.currentTimeMillis();
        int baseDrift = Math.max(1, plugin.getConfig().getInt("diplomacy.drift-toward-zero", 1));
        Set<String> processed = new HashSet<>();

        for (Clan sourceClan : new ArrayList<>(plugin.getClanManager().getAllClans())) {
            for (ClanRelationRecord record : new ArrayList<>(sourceClan.relations().values())) {
                UUID targetId = record.targetClanId();
                if (sourceClan.id().equals(targetId)) continue;
                String pairKey = sourceClan.id().compareTo(targetId) < 0
                        ? sourceClan.id() + ":" + targetId : targetId + ":" + sourceClan.id();
                if (!processed.add(pairKey)) continue;

                ClanRelationRecord updated = calculateDrift(sourceClan, targetId, record, baseDrift, now);
                if (updated != record) {
                    plugin.getDiplomacyManager().commitPairRecord(sourceClan.id(), targetId, updated);
                }
            }
        }
        plugin.getDiplomacyManager().cleanupExpired();
    }

    /**
     * Рассчитывает новое значение отношения с учётом конфликтов, блокад, эмбарго и пактов.
     */
    public ClanRelationRecord calculateDrift(Clan sourceClan, UUID targetId, ClanRelationRecord record, int baseDrift, long now) {
        // Проверяем сдвиг 24ч тренда
        ClanRelationRecord current = record.checkAndUpdate24hBaseline(now);
        int score = current.score();

        boolean inConflict = plugin.getClanManager().inConflictWith(sourceClan.id(), targetId);
        if (inConflict) {
            // Во время активной войны/осады/рейда дрейф к нулю заморожен
            return current;
        }

        boolean embargoed = plugin.getDiplomacyManager().isEmbargoed(sourceClan.id(), targetId);
        boolean blockaded = plugin.getDiplomacyManager().isBlockading(sourceClan.id(), targetId)
                || plugin.getDiplomacyManager().isBlockading(targetId, sourceClan.id());
        boolean tradePact = plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetId, PactType.TRADE);

        int newScore = score;

        int allianceFloor = plugin.getDiplomacyManager().hasPact(sourceClan.id(), targetId, PactType.ALLIANCE)
                ? plugin.getDiplomacyManager().minScore(PactType.ALLIANCE) : 0;

        if (score > 0) {
            if (score <= allianceFloor) {
                // Союз держит отношения не ниже порога, иначе он сам развалился бы от затухания
                newScore = score;
            } else if (tradePact && score <= 30) {
                // При торговом соглашении отношения удерживаются на стабильном дружественном уровне
                newScore = score;
            } else {
                // Спад к нулю
                int decay = (embargoed || blockaded) ? baseDrift + 1 : baseDrift;
                newScore = Math.max(allianceFloor, score - decay);
            }
        } else if (score < 0) {
            if (embargoed || blockaded) {
                // Под санкциями отношения не улучшаются сами по себе
                newScore = score;
            } else {
                // Постепенное затухание вражды к нулю
                newScore = Math.min(0, score + baseDrift);
            }
        } else {
            // score == 0
            if (tradePact) {
                // Торговля дает естественный легкий дрейф в плюс
                newScore = Math.min(20, score + 1);
            }
        }

        if (newScore != score || current != record) {
            return current.withScore(newScore, now);
        }
        return current;
    }
}
