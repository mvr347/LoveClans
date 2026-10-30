package me.lovelace.loveclans.integration;

import java.util.function.BooleanSupplier;

/**
 * Asks LoveChatFilter whether a clan name or tag is swearing or advertising. The filter is optional,
 * so the question goes through a small {@link Filter} that answers "no" when the plugin is absent.
 */
public final class NameCheck {

    public interface Filter {
        boolean isProfane(String text);

        boolean containsAdvertising(String text);
    }

    public enum Verdict { OK, PROFANE, ADVERTISING }

    private final Filter filter;
    private final BooleanSupplier enabled;

    public NameCheck(Filter filter, BooleanSupplier enabled) {
        this.filter = filter;
        this.enabled = enabled;
    }

    public Verdict check(String text) {
        if (text == null || text.isBlank() || !enabled.getAsBoolean()) return Verdict.OK;
        if (filter.isProfane(text)) return Verdict.PROFANE;
        if (filter.containsAdvertising(text)) return Verdict.ADVERTISING;
        return Verdict.OK;
    }
}
