package me.lovelace.loveclans.util;

import me.lovelace.loveclans.model.Clan;
import org.bukkit.Material;

/** Menu icons that stand for a clan. */
public final class ClanIcons {

    private ClanIcons() {
    }

    /** The clan's emblem banner; a clan without a valid banner emblem gets a white one. */
    public static Material emblem(Clan clan) {
        Material emblem = clan.emblem();
        return emblem != null && emblem.name().endsWith("_BANNER") ? emblem : Material.WHITE_BANNER;
    }
}
