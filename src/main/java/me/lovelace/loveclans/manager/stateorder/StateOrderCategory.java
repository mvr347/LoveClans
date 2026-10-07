package me.lovelace.loveclans.manager.stateorder;

/** Category of a state order; its material pool lives in config under {@code clans.trade.state-orders.categories.<key>}. */
public enum StateOrderCategory {
    FOOD("food"),
    BUILDING("building"),
    RAW("raw");

    private final String key;

    StateOrderCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static StateOrderCategory parse(String raw, StateOrderCategory fallback) {
        if (raw == null) return fallback;
        for (StateOrderCategory category : values()) {
            if (category.name().equalsIgnoreCase(raw) || category.key.equalsIgnoreCase(raw)) return category;
        }
        return fallback;
    }
}
