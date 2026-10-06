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
    private record ColorOption(String tag, String headTexture, String name) {}

    // 16 colors in the three work rows of a 54-slot menu, side walls left empty (gui_gen v2.1, rule 6):
    // 21 content slots, the last five stay free.
    private static final int[] COLOR_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38
    };
    private static final int SLOT_BACK = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int INVENTORY_SIZE = 54;

    // Жёстко заданный список из 15 цветов с текстурами шерсти для выбора цвета тега клана.
    // Ключ конфигурации (clans.available-colors.<key>) используется только для получения tag/name,
    // чтобы не дублировать локализацию названий цветов — текстура головы берётся из этого маппинга.
    private static final Map<String, String> COLOR_KEY_TO_HEAD_TEXTURE = Map.ofEntries(
            Map.entry("white", ItemBuilder.HEAD_WOOL_WHITE),
            Map.entry("gray", ItemBuilder.HEAD_WOOL_GRAY),
            Map.entry("dark_gray", ItemBuilder.HEAD_WOOL_DARK_GRAY),
            Map.entry("black", ItemBuilder.HEAD_WOOL_BLACK),
            Map.entry("red", ItemBuilder.HEAD_WOOL_RED),
            Map.entry("dark_red", ItemBuilder.HEAD_WOOL_DARK_RED),
            Map.entry("gold", ItemBuilder.HEAD_WOOL_ORANGE_GOLD),
            Map.entry("yellow", ItemBuilder.HEAD_WOOL_YELLOW),
            Map.entry("green", ItemBuilder.HEAD_WOOL_LIME),
            Map.entry("dark_green", ItemBuilder.HEAD_WOOL_DARK_GREEN),
            Map.entry("aqua", ItemBuilder.HEAD_WOOL_LIGHT_BLUE),
            Map.entry("dark_aqua", ItemBuilder.HEAD_WOOL_CYAN),
            Map.entry("blue", ItemBuilder.HEAD_WOOL_BLUE),
            Map.entry("dark_blue", ItemBuilder.HEAD_WOOL_DARK_BLUE),
            Map.entry("light_purple", ItemBuilder.HEAD_WOOL_PINK),
            Map.entry("dark_purple", ItemBuilder.HEAD_WOOL_PURPLE)
    );

    private final LoveClansPlugin plugin;

    public ClanColorPickerMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.COLOR_PICKER, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, INVENTORY_SIZE,
                plugin.getMessages().component("gui.color-picker.title", player));
        holder.setInventory(inventory);

        fillFrame(inventory);
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_WOOL_ORANGE_GOLD)
                .name(plugin.getMessages().component("gui.color-picker.title", player))
                .build());

        List<ColorOption> options = loadOptions();
        for (int i = 0; i < Math.min(options.size(), COLOR_SLOTS.length); i++) {
            ColorOption opt = options.get(i);
            boolean current = opt.tag().equals(clan.tagColor());
            ItemBuilder builder = ItemBuilder.head(opt.headTexture())
                    .name(plugin.getMessages().component("gui.color-picker.preview",
                            Map.of("preview", opt.tag() + opt.name() + (current ? " ✔" : "")), player));
            if (current) builder.glow(true);
            builder.mutate(meta -> meta.getPersistentDataContainer()
                    .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING, opt.tag()));
            inventory.setItem(COLOR_SLOTS[i], builder.build());
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    /**
     * 1-17 (header and Row1) and 45-51 (footer without the buttons) are glass; slot 0 is set by the caller.
     * The work zone 18-44 is not touched.
     */
    private void fillFrame(Inventory inventory) {
        for (int slot = 1; slot <= 17; slot++) {
            inventory.setItem(slot, GuiFrames.glassPane());
        }
        for (int slot = 45; slot < SLOT_BACK; slot++) {
            inventory.setItem(slot, GuiFrames.glassPane());
        }
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, ItemStack clickedItem) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
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
            String name = section.getString(key + ".name", key);
            String headTexture = COLOR_KEY_TO_HEAD_TEXTURE.getOrDefault(key, ItemBuilder.HEAD_WOOL_WHITE);
            list.add(new ColorOption(tag, headTexture, name));
        }
        return list;
    }
}
