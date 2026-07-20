package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public final class ClanTerritoriesMenu {
    private final LoveClansPlugin plugin;

    public ClanTerritoriesMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        List<ClanTerritory> territories = clan.territories().stream().toList();

        int numTerritories = territories.size();
        int contentRows = (int) Math.ceil(numTerritories / 7.0);
        if (contentRows == 0) contentRows = 1;
        int inventorySize = Math.max(27, Math.min(54, (contentRows + 2) * 9));

        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.TERRITORIES, clan.id()), inventorySize,
                plugin.getMessages().component("gui.territories-title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        fillGlass(inventory);

        if (territories.isEmpty()) {
            inventory.setItem(inventorySize / 2, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.territories.empty.name", player))
                    .lore(plugin.getMessages().component("gui.territories.empty.lore", player))
                    .build());
        } else {
            for (int i = 0; i < numTerritories; i++) {
                ClanTerritory territory = territories.get(i);
                int centerX = territory.key().chunkX() * 16 + 8;
                int centerZ = territory.key().chunkZ() * 16 + 8;

                int row = i / 7;
                int col = i % 7;
                int slot = 9 * (row + 1) + 1 + col;

                inventory.setItem(slot, ItemBuilder.head(ItemBuilder.HEAD_MAP)
                        .name(plugin.getMessages().component("gui.territories.item.name",
                                Map.of("world", territory.key().world()), player))
                        .lore(plugin.getMessages().component("gui.territorries.item.coords",
                                Map.of(
                                        "x", String.valueOf(centerX),
                                        "z", String.valueOf(centerZ),
                                        "cx", String.valueOf(territory.key().chunkX()),
                                        "cz", String.valueOf(territory.key().chunkZ())
                                ), player))
                        .lore(plugin.getMessages().component("gui.territories.item.click-teleport", player))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(),
                                PersistentDataType.STRING,
                                territory.key().world() + ";" + territory.key().chunkX() + ";" + territory.key().chunkZ()
                        ))
                        .build());
            }
        }

        boolean canManage = clan.member(player.getUniqueId())
                .map(m -> m.rank().atLeast(ClanRank.GUARDIAN))
                .orElse(false);

        boolean alreadyHasBanner = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(plugin.getGuiManager().memberKey(), PersistentDataType.STRING)) {
                alreadyHasBanner = true;
                break;
            }
        }

        boolean hasInstalledBanner = clan.territories().stream().anyMatch(t -> t.bannerX() != null);

        inventory.setItem(inventorySize - 2, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(inventorySize - 1, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        if (canManage && !alreadyHasBanner && !hasInstalledBanner) {
            inventory.setItem(inventorySize - 3, ItemBuilder.of(Material.WHITE_BANNER)
                    .name(plugin.getMessages().component("gui.territories.banner.name", player))
                    .lore(plugin.getMessages().component("gui.territories.banner.lore", player))
                    .build());
        }

        player.openInventory(inventory);
    }

    public void handleTerritoryClick(Player player, Clan clan, int slot, boolean isRightClick) {
        int inventorySize = player.getOpenInventory().getTopInventory().getSize();
        if (slot == inventorySize - 1) {
            player.closeInventory();
            return;
        }

        if (slot == inventorySize - 2) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }

        if (slot == inventorySize - 3) {
            boolean canManage = clan.member(player.getUniqueId())
                    .map(m -> m.rank().atLeast(ClanRank.GUARDIAN))
                    .orElse(false);
            if (canManage) {
                ItemStack banner = ItemBuilder.of(Material.WHITE_BANNER)
                        .name(plugin.getMessages().component("item.claim-banner.name", player))
                        .lore(plugin.getMessages().component("item.claim-banner.lore", player))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(),
                                PersistentDataType.STRING,
                                clan.id().toString()
                        ))
                        .build();
                player.getInventory().addItem(banner);
                player.closeInventory();
                plugin.getMessages().send(player, "territory.banner-given");
            }
            return;
        }

        boolean isContentSlot = slot >= 9 && slot < inventorySize - 9;
        if (!isContentSlot) return;

        if ((slot % 9 == 0) || (slot % 9 == 8)) return;

        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;

        String data = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (data == null) return;

        String[] parts = data.split(";");
        if (parts.length != 3) return;

        try {
            String worldName = parts[0];
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);

            if (isRightClick) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getMessages().send(player, "territory.world-not-found");
                    return;
                }
                int centerX = chunkX * 16 + 8;
                int centerZ = chunkZ * 16 + 8;
                int y = world.getHighestBlockYAt(centerX, centerZ) + 1;
                player.closeInventory();
                player.teleport(new Location(world, centerX + 0.5, y, centerZ + 0.5));
                plugin.getMessages().send(player, "territory.teleported");
            } else {
                TerritoryKey key = new TerritoryKey(worldName, chunkX, chunkZ);
                clan.territories().stream()
                        .filter(t -> t.key().equals(key))
                        .findFirst()
                        .ifPresent(territory -> {
                            plugin.getGuiManager().openTerritorySettings(player, clan, territory);
                        });
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build());
            }
        }
    }
}