package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClanColorPickerMenu {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record ColorOption(String miniTag, Material material, String name) {}

    // slots for colors in 27-slot inventory
    private static final int[] COLOR_SLOTS = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21};

    private final ClansPlugin plugin;

    public ClanColorPickerMenu(ClansPlugin plugin) {
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
            boolean current = opt.miniTag().equals(clan.tagColor());
            inventory.setItem(COLOR_SLOTS[i], ItemBuilder.of(opt.material())
                    .name(MM.deserialize(opt.miniTag() + opt.name() + (current ? " ✔" : "")))
                    .mutate(meta -> meta.getPersistentDataContainer().set(
                            plugin.getGuiManager().memberKey(), PersistentDataType.STRING, opt.miniTag()))
                    .build());
        }

        inventory.setItem(22, ItemBuilder.of(Material.ARROW)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    private List<ColorOption> loadOptions() {
        List<ColorOption> list = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("clans.available-colors");
        if (section == null) return List.of();
        for (String key : section.getKeys(false)) {
            String tag = section.getString(key + ".tag");
            String matName = section.getString(key + ".material");
            String name = section.getString(key + ".name");
            Material mat = Material.matchMaterial(matName != null ? matName : "WHITE_WOOL");
            list.add(new ColorOption(tag, mat != null ? mat : Material.WHITE_WOOL, name));
        }
        return list;
    }

    private void fillGlass(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
    }
}
