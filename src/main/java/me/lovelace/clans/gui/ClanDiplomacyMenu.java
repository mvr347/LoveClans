package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.DiplomacyRelation;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class ClanDiplomacyMenu {
    private final ClansPlugin plugin;

    public ClanDiplomacyMenu(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan sourceClan, Clan targetClan) {
        DiplomacyRelation current = sourceClan.relationTo(targetClan.id());

        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.DIPLOMACY, targetClan.id()), 27,
                plugin.getMessages().component("gui.diplomacy.title",
                        Map.of("tag", targetClan.tag()), player));

        fillGlass(inventory);

        inventory.setItem(10, ItemBuilder.of(current == DiplomacyRelation.ALLY ? Material.LIME_TERRACOTTA : Material.GREEN_TERRACOTTA)
                .name(plugin.getMessages().component("gui.diplomacy.ally.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.ally.lore", player))
                .build());

        inventory.setItem(13, ItemBuilder.of(current == DiplomacyRelation.NEUTRAL ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_TERRACOTTA)
                .name(plugin.getMessages().component("gui.diplomacy.neutral.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.neutral.lore", player))
                .build());

        inventory.setItem(16, ItemBuilder.of(current == DiplomacyRelation.ENEMY ? Material.RED_TERRACOTTA : Material.ORANGE_TERRACOTTA)
                .name(plugin.getMessages().component("gui.diplomacy.enemy.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.enemy.lore", player))
                .build());

        inventory.setItem(22, ItemBuilder.of(Material.ARROW)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
