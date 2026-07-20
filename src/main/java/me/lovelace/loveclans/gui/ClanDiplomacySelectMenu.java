package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClanDiplomacySelectMenu implements InventoryHolder {
    // Framed content grid — columns 0 and 8 of each row stay reserved for the border/pagination.
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan sourceClan;
    private final List<Clan> otherClans;
    private int currentPage;
    private Inventory inventory;

    public ClanDiplomacySelectMenu(LoveClansPlugin plugin, Player player, Clan sourceClan) {
        this.plugin = plugin;
        this.player = player;
        this.sourceClan = sourceClan;
        this.otherClans = plugin.getClanManager().getAllClans().stream()
                .filter(clan -> !clan.id().equals(sourceClan.id()))
                .sorted(Comparator.comparing(Clan::name))
                .toList();
        this.currentPage = 0;
    }

    public boolean hasClans() {
        return !otherClans.isEmpty();
    }

    public void open() {
        int maxPage = Math.max(0, (otherClans.size() - 1) / CONTENT_SLOTS.length);
        currentPage = Math.max(0, Math.min(currentPage, maxPage));

        this.inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.diplomacy-select.title", player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        int start = currentPage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, otherClans.size());
        for (int index = start; index < end; index++) {
            Clan target = otherClans.get(index);
            int targetSlot = CONTENT_SLOTS[index - start];

            Material emblemMaterial = target.emblem().name().endsWith("_BANNER") ? target.emblem() : Material.WHITE_BANNER;
            ItemBuilder builder = ItemBuilder.of(emblemMaterial)
                    .name(plugin.getMessages().component("gui.diplomacy-select.clan-item.name",
                            Map.of("tag", target.tag(), "color", target.tagColor(), "name", target.name()), player))
                    .lore(plugin.getMessages().component("gui.diplomacy-select.clan-item.relation",
                            Map.of("relation", sourceClan.relationTo(target.id()).name()), player));
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, target.id().toString()));
            inventory.setItem(targetSlot, builder.build());
        }

        if (currentPage > 0) {
            inventory.setItem(36, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player)).build());
        }
        if (currentPage < maxPage) {
            inventory.setItem(44, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player)).build());
        }

        inventory.setItem(52, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(53, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(int slot) {
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 52) {
            plugin.getGuiManager().openMain(player, sourceClan);
            return;
        }

        int maxPage = Math.max(0, (otherClans.size() - 1) / CONTENT_SLOTS.length);
        if (slot == 36 && currentPage > 0) { currentPage--; open(); return; }
        if (slot == 44 && currentPage < maxPage) { currentPage++; open(); return; }

        org.bukkit.inventory.ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        String rawId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rawId == null) return;
        try {
            UUID targetId = UUID.fromString(rawId);
            plugin.getClanManager().getClanById(targetId).ifPresent(targetClan ->
                    plugin.getGuiManager().openDiplomacy(player, sourceClan, targetClan));
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
