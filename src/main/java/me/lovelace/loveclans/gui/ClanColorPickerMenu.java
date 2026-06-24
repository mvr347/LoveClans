package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClanColorPickerMenu {
    private static final Map<String, Material> PALETTE = new LinkedHashMap<>();

    static {
        PALETTE.put("<white>", Material.WHITE_DYE);
        PALETTE.put("<gray>", Material.GRAY_DYE);
        PALETTE.put("<dark_gray>", Material.BLACK_DYE);
        PALETTE.put("<red>", Material.RED_DYE);
        PALETTE.put("<gold>", Material.YELLOW_DYE);
        PALETTE.put("<yellow>", Material.YELLOW_DYE);
        PALETTE.put("<green>", Material.LIME_DYE);
        PALETTE.put("<dark_green>", Material.GREEN_DYE);
        PALETTE.put("<aqua>", Material.LIGHT_BLUE_DYE);
        PALETTE.put("<blue>", Material.BLUE_DYE);
        PALETTE.put("<dark_blue>", Material.BLUE_DYE);
        PALETTE.put("<light_purple>", Material.PINK_DYE);
        PALETTE.put("<dark_purple>", Material.PURPLE_DYE);
    }

    private final LoveClansPlugin plugin;

    public ClanColorPickerMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.COLOR_PICKER, clan.id()), 27,
                plugin.getMessages().component("gui.color-picker.title", player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        int slot = 0;
        for (Map.Entry<String, Material> entry : PALETTE.entrySet()) {
            if (slot >= 21) break;
            String colorTag = entry.getKey();
            ItemBuilder builder = ItemBuilder.of(entry.getValue())
                    .name(plugin.getMessages().component("gui.color-picker.preview",
                            Map.of("preview", colorTag + clan.tag()), player));
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, colorTag));
            inventory.setItem(slot, builder.build());
            slot++;
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, org.bukkit.inventory.ItemStack clickedItem) {
        if (slot == 22) {
            plugin.getGuiManager().openSettings(player, clan);
            return;
        }
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String colorTag = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (colorTag == null) return;
        plugin.getClanManager().changeTagColorAsync(clan, player.getUniqueId(), colorTag)
                .thenAccept(updated -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "gui.settings.change-color.success");
                    open(player, updated);
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }
}
