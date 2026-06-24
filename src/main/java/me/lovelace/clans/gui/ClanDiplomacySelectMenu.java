package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.DiplomacyRelation;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClanDiplomacySelectMenu implements InventoryHolder {
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ClansPlugin plugin;
    private final Player player;
    private final Clan sourceClan;
    private final List<Clan> otherClans;
    private int currentPage;

    public ClanDiplomacySelectMenu(ClansPlugin plugin, Player player, Clan sourceClan) {
        this.plugin = plugin;
        this.player = player;
        this.sourceClan = sourceClan;
        this.otherClans = new ArrayList<>(plugin.getClanManager().getAllClans().stream()
                .filter(c -> !c.id().equals(sourceClan.id()))
                .sorted(Comparator.comparingInt(Clan::level).reversed())
                .toList());
        this.currentPage = 0;
    }

    public boolean hasClans() {
        return !otherClans.isEmpty();
    }

    @Override
    public Inventory getInventory() {
        int maxPage = Math.max(0, (otherClans.size() - 1) / CONTENT_SLOTS.length);
        currentPage = Math.max(0, Math.min(currentPage, maxPage));

        Inventory inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.diplomacy-select.title", player));
        fillGlass(inventory);

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, otherClans.size());
        for (int index = start; index < end; index++) {
            Clan clan = otherClans.get(index);
            DiplomacyRelation current = sourceClan.relationTo(clan.id());

            ItemBuilder builder = ItemBuilder.of(clan.emblem()) // Use clan's emblem (banner)
                    .name(plugin.getMessages().component("gui.diplomacy-select.clan-item.name",
                            Map.of("tag", clan.coloredTag(), "name", clan.name()), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.relation",
                            Map.of("relation", current.name()), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.members",
                            Map.of("current", String.valueOf(clan.members().size())), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.click", player));

            // Removed SkullMeta setting as it's no longer a player head

            inventory.setItem(CONTENT_SLOTS[index - start], builder.build());
        }

        if (otherClans.isEmpty()) {
            inventory.setItem(22, ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("gui.diplomacy-select.empty.name", player))
                    .build());
        }

        if (currentPage > 0) {
            inventory.setItem(45, ItemBuilder.of(Material.ARROW)
                    .name(plugin.getMessages().component("gui.previous-page", player))
                    .build());
        }
        if (currentPage < maxPage) {
            inventory.setItem(53, ItemBuilder.of(Material.ARROW)
                    .name(plugin.getMessages().component("gui.next-page", player))
                    .build());
        }
        inventory.setItem(49, ItemBuilder.of(Material.BARRIER)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        return inventory;
    }

    public void open() {
        player.openInventory(getInventory());
    }

    public void handleInventoryClick(int slot) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && currentPage > 0) { currentPage--; open(); return; }
        if (slot == 53 && currentPage < Math.max(0, (otherClans.size() - 1) / CONTENT_SLOTS.length)) { currentPage++; open(); return; }

        int relativeIndex = contentSlotIndex(slot);
        if (relativeIndex < 0) return;
        int clanIndex = currentPage * CONTENT_SLOTS.length + relativeIndex;
        if (clanIndex < 0 || clanIndex >= otherClans.size()) return;

        Clan target = otherClans.get(clanIndex);
        plugin.getGuiManager().openDiplomacy(player, sourceClan, target);
    }

    private int contentSlotIndex(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}