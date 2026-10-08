package me.lovelace.loveclans.model.modifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Модификатор клана: репарации (долг/доход), щиты после набега, дебаффы/баффы осад и доступные поводы (casus belli).
 */
public record ClanModifier(
        UUID id,
        UUID clanId,
        String type,
        Map<String, String> payload,
        long startedAt,
        long endsAt,
        int stacks
) {
    public static final String TYPE_REPARATIONS_DEBT = "REPARATIONS_DEBT";
    public static final String TYPE_REPARATIONS_INCOME = "REPARATIONS_INCOME";
    public static final String TYPE_POST_RAID_SHIELD = "POST_RAID_SHIELD";
    public static final String TYPE_SIEGE_PRESSURE = "SIEGE_PRESSURE";
    public static final String TYPE_SIEGE_REPELLED = "SIEGE_REPELLED";
    public static final String TYPE_JUST_CASUS = "JUST_CASUS";

    public ClanModifier {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(payload));
    }

    public boolean isExpired(long now) {
        return endsAt > 0 && now >= endsAt;
    }

    public long remainingMillis(long now) {
        return Math.max(0L, endsAt - now);
    }

    public UUID targetClanId() {
        String raw = payload.get("target_clan_id");
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long dailyAmount() {
        String raw = payload.get("daily_amount");
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public int daysLeft() {
        String raw = payload.get("days_left");
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int arrears() {
        String raw = payload.get("arrears");
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String conflictType() {
        return payload.get("conflict_type");
    }

    public String reasonId() {
        return payload.get("reason_id");
    }

    public ClanModifier withDaysAndArrears(int newDaysLeft, int newArrears) {
        Map<String, String> copy = new HashMap<>(payload);
        copy.put("days_left", String.valueOf(newDaysLeft));
        copy.put("arrears", String.valueOf(newArrears));
        return new ClanModifier(id, clanId, type, copy, startedAt, endsAt, stacks);
    }
}
