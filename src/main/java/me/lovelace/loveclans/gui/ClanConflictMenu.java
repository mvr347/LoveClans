package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.gui.conflict.ConflictLore;
import me.lovelace.loveclans.gui.conflict.ConflictType;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class ClanConflictMenu implements InventoryHolder {
    private static final int SLOT_WAR = 11;
    private static final int SLOT_SIEGE = 13;
    private static final int SLOT_RAID = 15;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan sourceClan;
    private final Clan targetClan;
    private final ClanDiplomacyMenu diplomacyMenu;
    private Inventory inventory;

    public ClanConflictMenu(LoveClansPlugin plugin, Player player, Clan sourceClan, Clan targetClan, ClanDiplomacyMenu diplomacyMenu) {
        this.plugin = plugin;
        this.player = player;
        this.sourceClan = sourceClan;
        this.targetClan = targetClan;
        this.diplomacyMenu = diplomacyMenu;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.conflict.title",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        ConflictLore lore = new ConflictLore(plugin);
        inventory.setItem(0, lore.compareItem(player, sourceClan, targetClan));

        inventory.setItem(SLOT_WAR, lore.typeButton(player, ConflictType.WAR,
                diplomacyMenu.warReadiness(player, sourceClan, targetClan), targetClan));
        inventory.setItem(SLOT_SIEGE, lore.typeButton(player, ConflictType.SIEGE,
                diplomacyMenu.siegeReadiness(player, sourceClan, targetClan), targetClan));
        inventory.setItem(SLOT_RAID, lore.typeButton(player, ConflictType.RAID,
                diplomacyMenu.raidReadiness(player, sourceClan, targetClan), targetClan));

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        if (slot == SLOT_CLOSE) {
            clicker.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openDiplomacy(clicker, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_WAR) {
            diplomacyMenu.handleWarDeclare(clicker, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_SIEGE) {
            diplomacyMenu.handleSiegeDeclare(clicker, sourceClan, targetClan);
            return;
        }
        if (slot == SLOT_RAID) {
            diplomacyMenu.handleRaidDeclare(clicker, sourceClan, targetClan);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
