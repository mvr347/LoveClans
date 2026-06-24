package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.spirit.SpiritBuffLevel;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ClanSpiritMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private Inventory inventory;

    public ClanSpiritMenu(LoveClansPlugin plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;
    }

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessages().component("gui.spirit.title", Map.of("clan", clan.name()), player));

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Center Info
        int currentLevel = clan.spirit().level();
        SpiritBuffLevel buffLevel = SpiritBuffLevel.getByLevel(currentLevel);
        String levelName = buffLevel != null ? buffLevel.getName() : "Пробуждение";
        
        long currentExp = clan.spirit().energy();
        long nextExp = getExpForNextLevel(currentLevel);
        
        String progressStr = createProgressBar(currentExp, nextExp, 20);

        inventory.setItem(13, ItemBuilder.of(Material.NETHER_STAR)
                .name(plugin.getMessages().component("gui.spirit.info.name", Map.of("level", String.valueOf(currentLevel), "name", levelName), player))
                .lore(plugin.getMessages().component("gui.spirit.info.exp", Map.of("current", String.valueOf(currentExp), "next", String.valueOf(nextExp)), player))
                .lore(plugin.getMessages().component("gui.spirit.info.progress", Map.of("bar", progressStr), player))
                .glow(true)
                .build());

        // Buffs List
        inventory.setItem(30, ItemBuilder.of(Material.POTION)
                .name(plugin.getMessages().component("gui.spirit.buffs.name", player))
                .lore(plugin.getMessages().component("gui.spirit.buffs.lore_active", player))
                // Add logic to list active buffs here based on SpiritBuffLevel
                .build());

        // Unique Ability (Level 10)
        inventory.setItem(32, ItemBuilder.of(Material.BEACON)
                .name(plugin.getMessages().component("gui.spirit.ability.name", player))
                .lore(plugin.getMessages().component(currentLevel == 10 ? "gui.spirit.ability.unlocked" : "gui.spirit.ability.locked", player))
                .build());

        // History
        inventory.setItem(40, ItemBuilder.of(Material.PAPER)
                .name(plugin.getMessages().component("gui.spirit.history.name", player))
                .lore(plugin.getMessages().component("gui.spirit.history.lore", player))
                .build());

        // Top Contributors
        inventory.setItem(48, ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.spirit.top.name", player))
                .lore(plugin.getMessages().component("gui.spirit.top.lore", player))
                .build());

        // Back button
        inventory.setItem(50, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == 50) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        // Handle history and top contributors clicks if needed
    }

    private String createProgressBar(long current, long max, int length) {
        if (max == 0) return "<green>" + "█".repeat(length) + "</green>";
        int filled = (int) (((double) current / max) * length);
        int empty = length - filled;
        return "<green>" + "█".repeat(Math.max(0, filled)) + "</green><gray>" + "█".repeat(Math.max(0, empty)) + "</gray>";
    }
    
    private long getExpForNextLevel(int level) {
        // Placeholder formula for spirit exp
        return level * 1000L; 
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
