package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Amounts as coin glyphs ({@code %img_<tag>% xN}) split by LoveEconomy denominations, plus a parser
 * for typed amounts ("1500", "3i 50c"). The glyph tags are resolved by {@code MessageService} / {@code
 * ItemsAdderFontHook} when the text is sent to a player.
 */
public final class CoinFormat {

    private CoinFormat() {
    }

    private static Optional<LoveEconomy> economy() {
        try {
            return LoveCore.service(LoveEconomy.class);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Glyph string for the amount, highest denomination first; plain "N монет" without LoveCore. */
    public static String format(long amount) {
        return format(economy().orElse(null), amount);
    }

    public static String format(LoveEconomy eco, long amount) {
        if (eco == null) {
            return amount + " монет";
        }
        List<Denomination> dens = new ArrayList<>(eco.denominations());
        dens.sort(Comparator.comparingLong(Denomination::value).reversed());
        if (amount <= 0 || dens.isEmpty()) {
            return "0 " + eco.currencyName();
        }
        StringBuilder sb = new StringBuilder();
        long remaining = amount;
        for (Denomination den : dens) {
            if (den.value() <= 0) continue;
            long count = remaining / den.value();
            if (count > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append("<white>%img_").append(tag(den)).append("%</white> x").append(count);
                remaining %= den.value();
            }
        }
        return sb.length() == 0 ? "0 " + eco.currencyName() : sb.toString();
    }

    /** Typed amount: a plain number (copper units) or "3i 50c"-style text. */
    public static long parse(String input) {
        String text = input == null ? "" : input.trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // not a plain number: try the denomination syntax
        }
        List<Denomination> dens = economy().map(LoveEconomy::allDenominations).orElse(null);
        return MoneyParser.parse(text, dens);
    }

    private static String tag(Denomination den) {
        String id = den.itemId();
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
