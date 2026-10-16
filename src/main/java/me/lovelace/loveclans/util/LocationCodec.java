package me.lovelace.loveclans.util;

import java.util.Locale;

/**
 * Pure (Bukkit-free) text codec for stored locations: {@code world;x;y;z;yaw;pitch}.
 * Always writes with Locale.ROOT; the reader also accepts decimal commas that older builds
 * wrote under ru_RU, so already-saved homes heal on the next load.
 */
public final class LocationCodec {
    public record Parts(String world, double x, double y, double z, float yaw, float pitch) {}

    private LocationCodec() {}

    public static String format(String world, double x, double y, double z, float yaw, float pitch) {
        return String.format(Locale.ROOT, "%s;%f;%f;%f;%f;%f", world, x, y, z, yaw, pitch);
    }

    /** @return parsed parts, or null when the text is malformed. */
    public static Parts parse(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] p = text.split(";");
        if (p.length != 6) return null;
        try {
            return new Parts(p[0],
                    Double.parseDouble(p[1].replace(',', '.')),
                    Double.parseDouble(p[2].replace(',', '.')),
                    Double.parseDouble(p[3].replace(',', '.')),
                    Float.parseFloat(p[4].replace(',', '.')),
                    Float.parseFloat(p[5].replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
