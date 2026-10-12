package me.lovelace.loveclans.gui.conflict;

import java.util.Locale;

/** The three kinds of open conflict a clan can declare; {@link #key()} selects lang and config sections. */
public enum ConflictType {
    WAR, SIEGE, RAID;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
