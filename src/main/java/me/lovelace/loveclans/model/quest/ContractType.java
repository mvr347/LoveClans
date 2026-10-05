package me.lovelace.loveclans.model.quest;

/**
 * Weekly contracts run on a fixed calendar week (Mon-Sun); monthly contracts run on a calendar month.
 * The old DAILY type was removed (2026-10-05) - its table is left untouched in the database.
 */
public enum ContractType {
    WEEKLY,
    MONTHLY
}
