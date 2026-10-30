package me.lovelace.loveclans.util;

import me.lovelace.loveclans.integration.NameCheck;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Everything a clan name or tag has to pass before it is accepted, in the order a player should
 * hear about it: length and characters, then swearing and advertising, then (for a tag) whether it
 * is already taken. Returns the lang key of the first failure; no server types.
 */
public final class ClanInputValidator {
    public enum Field { NAME, TAG }

    private final ClanNameRules.Limits nameLimits;
    private final ClanNameRules.Limits tagLimits;
    private final NameCheck nameCheck;

    public ClanInputValidator(ClanNameRules.Limits nameLimits, ClanNameRules.Limits tagLimits, NameCheck nameCheck) {
        this.nameLimits = nameLimits;
        this.tagLimits = tagLimits;
        this.nameCheck = nameCheck;
    }

    /** Lang key (under {@code dialog.error}) of the first problem, empty when the value is fine. */
    public Optional<String> firstProblem(Field field, String value, Predicate<String> tagTaken) {
        String word = field == Field.NAME ? "name" : "tag";
        ClanNameRules.Problem problem = ClanNameRules.check(value, field == Field.NAME ? nameLimits : tagLimits);
        if (problem != ClanNameRules.Problem.OK) return Optional.of("invalid-" + word);
        switch (nameCheck.check(value)) {
            case PROFANE -> { return Optional.of(word + "-profane"); }
            case ADVERTISING -> { return Optional.of(word + "-ad"); }
            default -> { }
        }
        if (field == Field.TAG && tagTaken.test(value)) return Optional.of("tag-exists");
        return Optional.empty();
    }
}
