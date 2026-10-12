package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.entity.Player;

/**
 * Меню территорий клана с чанками полностью упразднено по запросу владельца.
 * Все вызовы перенаправляются в единое меню управления столицей/территорией {@link ClanCapitalManagementMenu}.
 */
public final class ClanTerritoriesMenu {
    private final LoveClansPlugin plugin;

    public ClanTerritoriesMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        plugin.getGuiManager().openClanCapitalManagementMenu(player, clan);
    }

    public void handleTerritoryClick(Player player, Clan clan, int slot, boolean isRightClick) {
        plugin.getGuiManager().openClanCapitalManagementMenu(player, clan);
    }
}