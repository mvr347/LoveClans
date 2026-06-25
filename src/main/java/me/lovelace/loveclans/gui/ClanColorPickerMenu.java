package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClanColorPickerMenu {
    private record ColorOption(String tag, Material material, String name) {}

    private static final int[] COLOR_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};

    private final LoveClansPlugin plugin;

    public ClanColorPickerMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(
                new ClanMenuHolder(ClanMenuType.COLOR_PICKER, clan.id()), 27,
                plugin.getMessages().component("gui.color-picker.title", player));

        fillGlass(inventory);

        List<ColorOption> options = loadOptions();
        for (int i = 0; i < Math.min(options.size(), COLOR_SLOTS.length); i++) {
            ColorOption opt = options.get(i);
            boolean current = opt.tag().equals(clan.tagColor());
            ItemBuilder builder = ItemBuilder.of(opt.material())
                    .name(plugin.getMessages().component("gui.color-picker.preview",
                            Map.of("preview", opt.tag() + opt.name() + (current ? " ✔" : "")), player));
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, opt.tag()));
            inventory.setItem(COLOR_SLOTS[i], builder.build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, ItemStack clickedItem) {
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

    private List<ColorOption> loadOptions() {
        List<ColorOption> list = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("clans.available-colors");
        if (section == null) return List.of();
        for (String key : section.getKeys(false)) {
            String tag = section.getString(key + ".tag", "<white>");
            String matName = section.getString(key + ".material", "WHITE_WOOL");
            String name = section.getString(key + ".name", key);
            Material material = Material.matchMaterial(matName);
            list.add(new ColorOption(tag, material != null ? material : Material.WHITE_WOOL, name));
        }
        return list;
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
