package me.lovelace.loveclans.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Length and character rules for a clan name or tag, with no server types so they can be tested.
 * One place decides, and the dialog, the chat fallback and {@code ClanManager} all ask it, so the
 * player hears about a bad character while typing instead of at the very last step.
 */
public final class ClanNameRules {
    private ClanNameRules() {}

    public enum Problem { OK, TOO_SHORT, TOO_LONG, BAD_CHARACTERS }

    /** Limits as written in config.yml ({@code clans.name.*} / {@code clans.tag.*}). */
    public record Limits(int min, int max, String pattern) {}

    public static Problem check(String value, Limits limits) {
        if (value == null || value.length() < limits.min()) return Problem.TOO_SHORT;
        if (value.length() > limits.max()) return Problem.TOO_LONG;
        try {
            if (!Pattern.compile(limits.pattern()).matcher(value).matches()) return Problem.BAD_CHARACTERS;
        } catch (PatternSyntaxException broken) {
            // A broken pattern in config.yml must not let anything through.
            return Problem.BAD_CHARACTERS;
        }
        return Problem.OK;
    }

    /** What a player typed into a text field: surrounding spaces are never part of a name. */
    public static String clean(String typed) {
        return typed == null ? "" : typed.strip();
    }
}
