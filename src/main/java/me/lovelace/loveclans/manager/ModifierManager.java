package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.modifier.ClanModifier;
import me.lovelace.loveclans.storage.ClanStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModifierManager {
    private final LoveClansPlugin plugin;
    private final ClanStorage storage;

    // clanId -> list of active modifiers
    private final Map<UUID, List<ClanModifier>> modifiers = new ConcurrentHashMap<>();

    public ModifierManager(LoveClansPlugin plugin, ClanStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> loadAsync() {
        return storage.loadAllModifiersAsync().thenAccept(loaded -> {
            modifiers.clear();
            long now = System.currentTimeMillis();
            for (ClanModifier mod : loaded) {
                if (mod.isExpired(now)) {
                    storage.deleteModifierAsync(mod.id());
                    continue;
                }
                modifiers.computeIfAbsent(mod.clanId(), k -> new CopyOnWriteArrayList<>()).add(mod);
            }
        });
    }

    public List<ClanModifier> getModifiers(UUID clanId) {
        List<ClanModifier> list = modifiers.get(clanId);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        return list.stream().filter(m -> !m.isExpired(now)).toList();
    }

    public void addModifier(ClanModifier modifier) {
        modifiers.computeIfAbsent(modifier.clanId(), k -> new CopyOnWriteArrayList<>()).add(modifier);
        storage.saveModifierAsync(modifier);
    }

    public void removeModifier(UUID modifierId) {
        for (Map.Entry<UUID, List<ClanModifier>> entry : modifiers.entrySet()) {
            entry.getValue().removeIf(m -> m.id().equals(modifierId));
        }
        storage.deleteModifierAsync(modifierId);
    }

    public boolean hasPostRaidShield(UUID clanId) {
        long now = System.currentTimeMillis();
        return getModifiers(clanId).stream()
                .anyMatch(m -> ClanModifier.TYPE_POST_RAID_SHIELD.equals(m.type()) && !m.isExpired(now));
    }

    public void grantPostRaidShield(UUID clanId, long hours) {
        long now = System.currentTimeMillis();
        long endsAt = now + hours * 3600_000L;
        getModifiers(clanId).stream()
                .filter(m -> ClanModifier.TYPE_POST_RAID_SHIELD.equals(m.type()))
                .forEach(m -> removeModifier(m.id()));

        ClanModifier modifier = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_POST_RAID_SHIELD,
                Map.of(),
                now,
                endsAt,
                1
        );
        addModifier(modifier);
    }

    public void grantReparations(UUID debtorClanId, UUID creditorClanId, int days, long dailyAmount) {
        long now = System.currentTimeMillis();
        long endsAt = now + ((long) days * 24 * 3600_000L);

        for (ClanModifier m : getModifiers(debtorClanId)) {
            if (ClanModifier.TYPE_REPARATIONS_DEBT.equals(m.type()) && creditorClanId.equals(m.targetClanId())) {
                removeModifier(m.id());
            }
        }
        for (ClanModifier m : getModifiers(creditorClanId)) {
            if (ClanModifier.TYPE_REPARATIONS_INCOME.equals(m.type()) && debtorClanId.equals(m.targetClanId())) {
                removeModifier(m.id());
            }
        }

        UUID debtId = UUID.randomUUID();
        UUID incomeId = UUID.randomUUID();

        Map<String, String> debtPayload = new HashMap<>();
        debtPayload.put("target_clan_id", creditorClanId.toString());
        debtPayload.put("daily_amount", String.valueOf(dailyAmount));
        debtPayload.put("days_left", String.valueOf(days));
        debtPayload.put("arrears", "0");
        debtPayload.put("last_ticked_at", String.valueOf(now));
        debtPayload.put("paired_id", incomeId.toString());

        Map<String, String> incomePayload = new HashMap<>();
        incomePayload.put("target_clan_id", debtorClanId.toString());
        incomePayload.put("daily_amount", String.valueOf(dailyAmount));
        incomePayload.put("days_left", String.valueOf(days));
        incomePayload.put("paired_id", debtId.toString());

        ClanModifier debt = new ClanModifier(debtId, debtorClanId, ClanModifier.TYPE_REPARATIONS_DEBT, debtPayload, now, endsAt, 1);
        ClanModifier income = new ClanModifier(incomeId, creditorClanId, ClanModifier.TYPE_REPARATIONS_INCOME, incomePayload, now, endsAt, 1);

        addModifier(debt);
        addModifier(income);
    }

    public void grantSiegePressure(UUID clanId, int days) {
        long now = System.currentTimeMillis();
        long endsAt = now + ((long) days * 24 * 3600_000L);
        ClanModifier modifier = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_SIEGE_PRESSURE,
                Map.of(),
                now,
                endsAt,
                1
        );
        addModifier(modifier);
    }

    public void grantSiegeRepelled(UUID clanId, int days) {
        long now = System.currentTimeMillis();
        long endsAt = now + ((long) days * 24 * 3600_000L);
        ClanModifier modifier = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_SIEGE_REPELLED,
                Map.of(),
                now,
                endsAt,
                1
        );
        addModifier(modifier);
    }

    public void grantJustCasus(UUID clanId, UUID targetClanId, String conflictType, String reasonId, int ttlDays) {
        long now = System.currentTimeMillis();
        long endsAt = ttlDays > 0 ? now + ((long) ttlDays * 24 * 3600_000L) : 0L;

        Map<String, String> payload = new HashMap<>();
        payload.put("target_clan_id", targetClanId.toString());
        payload.put("conflict_type", conflictType.toUpperCase());
        payload.put("reason_id", reasonId.toLowerCase());

        ClanModifier modifier = new ClanModifier(
                UUID.randomUUID(),
                clanId,
                ClanModifier.TYPE_JUST_CASUS,
                payload,
                now,
                endsAt,
                1
        );
        addModifier(modifier);
    }

    public boolean hasJustCasus(UUID clanId, UUID targetClanId, String conflictType, String reasonId) {
        long now = System.currentTimeMillis();
        return getModifiers(clanId).stream().anyMatch(m ->
                ClanModifier.TYPE_JUST_CASUS.equals(m.type())
                && !m.isExpired(now)
                && targetClanId.equals(m.targetClanId())
                && conflictType.equalsIgnoreCase(m.conflictType())
                && (reasonId == null || reasonId.equalsIgnoreCase(m.reasonId()))
        );
    }

    public Optional<ClanModifier> findJustCasus(UUID clanId, UUID targetClanId, String conflictType) {
        long now = System.currentTimeMillis();
        return getModifiers(clanId).stream().filter(m ->
                ClanModifier.TYPE_JUST_CASUS.equals(m.type())
                && !m.isExpired(now)
                && targetClanId.equals(m.targetClanId())
                && conflictType.equalsIgnoreCase(m.conflictType())
        ).findFirst();
    }

    public boolean consumeJustCasus(UUID clanId, UUID targetClanId, String conflictType, String reasonId) {
        long now = System.currentTimeMillis();
        Optional<ClanModifier> match = getModifiers(clanId).stream().filter(m ->
                ClanModifier.TYPE_JUST_CASUS.equals(m.type())
                && !m.isExpired(now)
                && targetClanId.equals(m.targetClanId())
                && conflictType.equalsIgnoreCase(m.conflictType())
                && (reasonId == null || reasonId.equalsIgnoreCase(m.reasonId()))
        ).findFirst();

        if (match.isPresent()) {
            removeModifier(match.get().id());
            return true;
        }
        return false;
    }

    public void tickDaily() {
        long now = System.currentTimeMillis();
        long dayMs = 24L * 3600_000L;

        // 1. Process reparations
        for (List<ClanModifier> list : modifiers.values()) {
            for (ClanModifier m : list) {
                if (!ClanModifier.TYPE_REPARATIONS_DEBT.equals(m.type()) || m.isExpired(now)) {
                    continue;
                }
                long lastTickedAt = 0L;
                String rawLast = m.payload().get("last_ticked_at");
                if (rawLast != null) {
                    try {
                        lastTickedAt = Long.parseLong(rawLast);
                    } catch (NumberFormatException ignored) {}
                }
                if (now - lastTickedAt < dayMs) {
                    continue;
                }

                UUID debtorClanId = m.clanId();
                UUID creditorClanId = m.targetClanId();
                if (creditorClanId == null) continue;

                Clan debtor = plugin.getClanManager().getClanById(debtorClanId).orElse(null);
                Clan creditor = plugin.getClanManager().getClanById(creditorClanId).orElse(null);
                if (debtor == null || creditor == null) continue;

                long amount = m.dailyAmount();
                int daysLeft = m.daysLeft() - 1;
                int arrears = m.arrears();

                if (debtor.chestMoney() >= amount) {
                    debtor.addChestMoney(-amount);
                    creditor.addChestMoney(amount);
                    plugin.getStorage().updateClanChestMoney(debtor.id(), debtor.chestMoney());
                    plugin.getStorage().updateClanChestMoney(creditor.id(), creditor.chestMoney());

                    broadcastToClan(debtor, "<yellow>Выплачена ежедневная дань клану <gold>" + creditor.name() + "<yellow>: <white>" + amount + "⛃<yellow>.");
                    broadcastToClan(creditor, "<green>Получена ежедневная дань от клана <gold>" + debtor.name() + "<green>: <white>" + amount + "⛃<green>.");
                } else {
                    arrears++;
                    broadcastToClan(debtor, "<red><bold>Внимание!</bold> В казне клана недостаточно средств для выплаты дани клану <gold>" + creditor.name() + "<red>! Долг зафиксирован (неуплата #" + arrears + ").");
                    broadcastToClan(creditor, "<red>Клан <gold>" + debtor.name() + "<red> не смог выплатить ежедневную дань! (неуплата #" + arrears + ").");

                    if (arrears >= 3) {
                        grantJustCasus(creditorClanId, debtorClanId, "WAR", "unpaid_tribute", 7);
                        broadcastToClan(creditor, "<gold><bold>Казус белли получен!</bold> Из-за 3 неуплат дани вы можете объявить войну клану <white>" + debtor.name() + "<gold>!");
                    }
                }

                if (daysLeft <= 0) {
                    removeModifier(m.id());
                    String pairedId = m.payload().get("paired_id");
                    if (pairedId != null) {
                        try {
                            removeModifier(UUID.fromString(pairedId));
                        } catch (Exception ignored) {}
                    }
                    broadcastToClan(debtor, "<green>Срок выплаты дани клану <gold>" + creditor.name() + "<green> завершён!");
                    broadcastToClan(creditor, "<yellow>Срок получения дани от клана <gold>" + debtor.name() + "<yellow> завершён.");
                } else {
                    Map<String, String> copy = new HashMap<>(m.payload());
                    copy.put("days_left", String.valueOf(daysLeft));
                    copy.put("arrears", String.valueOf(arrears));
                    copy.put("last_ticked_at", String.valueOf(now));
                    ClanModifier updated = new ClanModifier(m.id(), m.clanId(), m.type(), copy, m.startedAt(), m.endsAt(), m.stacks());
                    replaceModifier(updated);

                    String pairedId = m.payload().get("paired_id");
                    if (pairedId != null) {
                        try {
                            UUID pId = UUID.fromString(pairedId);
                            for (ClanModifier inc : getModifiers(creditorClanId)) {
                                if (inc.id().equals(pId)) {
                                    ClanModifier incUpdated = inc.withDaysAndArrears(daysLeft, 0);
                                    replaceModifier(incUpdated);
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 2. Clean up expired modifiers
        for (Map.Entry<UUID, List<ClanModifier>> entry : modifiers.entrySet()) {
            for (ClanModifier m : entry.getValue()) {
                if (m.isExpired(now)) {
                    removeModifier(m.id());
                }
            }
        }
    }

    private void replaceModifier(ClanModifier updated) {
        List<ClanModifier> list = modifiers.get(updated.clanId());
        if (list != null) {
            list.removeIf(m -> m.id().equals(updated.id()));
            list.add(updated);
        }
        storage.saveModifierAsync(updated);
    }

    private void broadcastToClan(Clan clan, String miniMessageText) {
        for (UUID memberId : clan.members().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                plugin.getMessages().sendCustom(p, miniMessageText);
            }
        }
    }
}
