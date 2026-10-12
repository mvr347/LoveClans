package me.lovelace.loveclans.gui.conflict;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the military-actions screen and the server-side declaration need to agree on for one conflict type:
 * at most one <i>hard block</i> (rights, ally/pact/tier, shield, an already running conflict) that hides the
 * checklist, plus an ordered checklist of requirements. The first failing entry is the single reason shown on the
 * button and the one the server rejects with, so the lore never promises what the code would refuse.
 */
public record ConflictReadiness(Deny hardBlock, List<Requirement> requirements) {

    /** A deny key from {@code deny.*} plus its placeholders. */
    public record Deny(String key, Map<String, String> placeholders) {
    }

    /**
     * One checklist row. {@code id} selects the lang pair {@code gui.conflict.req.<id>.ok|fail}; {@code labelPlaceholders}
     * fill both; {@code deny} is what the server throws when {@code ok} is false.
     */
    public record Requirement(String id, boolean ok, Map<String, String> labelPlaceholders, Deny deny) {
    }

    /** First failing step in declaration order, hard block first. */
    public Optional<Deny> firstDeny() {
        if (hardBlock != null) return Optional.of(hardBlock);
        for (Requirement requirement : requirements) {
            if (!requirement.ok()) return Optional.of(requirement.deny());
        }
        return Optional.empty();
    }

    public boolean ready() {
        return firstDeny().isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Deny hard;
        private final List<Requirement> requirements = new ArrayList<>();

        /** Records the first hard block only; later ones are ignored so evaluation order decides the shown reason. */
        public Builder blockIf(boolean condition, String key) {
            return blockIf(condition, key, Map.of());
        }

        public Builder blockIf(boolean condition, String key, Map<String, String> placeholders) {
            if (condition && hard == null) {
                hard = new Deny(key, placeholders);
            }
            return this;
        }

        public Builder require(String id, boolean ok, Map<String, String> labelPlaceholders, String denyKey, Map<String, String> denyPlaceholders) {
            requirements.add(new Requirement(id, ok, labelPlaceholders, new Deny(denyKey, denyPlaceholders)));
            return this;
        }

        public Builder require(String id, boolean ok, String denyKey) {
            return require(id, ok, Map.of(), denyKey, Map.of());
        }

        public ConflictReadiness build() {
            return new ConflictReadiness(hard, List.copyOf(requirements));
        }
    }
}
