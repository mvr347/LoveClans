package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.ClanRank;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class ClanRoleSettingsMenu extends ClanMenuHolder {
    private final ClansPlugin plugin;

    public ClanRoleSettingsMenu(ClansPlugin plugin, Clan clan) {
        super(ClanMenuType.ROLE_SETTINGS, clan.id());
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.role-settings.title", player));

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Rank RECRUIT
        inventory.setItem(10, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzJiZDVlYTA0OTliYjM0Y2JhMzE1OTc3ZGMwMjFjNmI0NGM0MGE1OWZhYmI4ODI1YWIxOGI0NjAyYWExOWU4YSJ9fX0=")
                .name(plugin.getMessages().component("gui.role-settings.rank.name", Map.of("rank", plugin.getMessages().raw(ClanRank.RECRUIT.key())), player))
                .lore(plugin.getMessages().component("gui.role-settings.rank.click", player))
                .mutate(meta -> meta.getPersistentDataContainer().set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, ClanRank.RECRUIT.name()))
                .build());

        // Rank MEMBER
        inventory.setItem(13, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDNhNDdlOTRlOGE0OTliYjM0Y2JhMzE1OTc3ZGMwMjFjNmI0NGM0MGE1OWZhYmI4ODI1YWIxOGI0NjAyYWExOWU4YSJ9fX0=")
                .name(plugin.getMessages().component("gui.role-settings.rank.name", Map.of("rank", plugin.getMessages().raw(ClanRank.MEMBER.key())), player))
                .lore(plugin.getMessages().component("gui.role-settings.rank.click", player))
                .mutate(meta -> meta.getPersistentDataContainer().set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, ClanRank.MEMBER.name()))
                .build());

        // Rank GUARDIAN
        inventory.setItem(16, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE5OWEzYmNkMmQ2MDMyODQ0M2QwMDNiNjA4NDI1YjY4YzNkYTI3MTAyY2M1ZGFlMzE2MjVjNTY4NTkyOWY0MSJ9fX0=")
                .name(plugin.getMessages().component("gui.role-settings.rank.name", Map.of("rank", plugin.getMessages().raw(ClanRank.GUARDIAN.key())), player))
                .lore(plugin.getMessages().component("gui.role-settings.rank.click", player))
                .mutate(meta -> meta.getPersistentDataContainer().set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, ClanRank.GUARDIAN.name()))
                .build());

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }
}