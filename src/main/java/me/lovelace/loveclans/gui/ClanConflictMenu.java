package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Optional;

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
                plugin.getMessages().component("gui.diplomacy.conflict.open-name",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_ACTIVE_WAR)
                .name(plugin.getMessages().component("gui.diplomacy.conflict.open-name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.conflict.open-lore", player))
                .build());

        // 11 War
        inventory.setItem(SLOT_WAR, buildWarItem().build());

        // 13 Siege
        inventory.setItem(SLOT_SIEGE, buildSiegeItem().build());

        // 15 Raid
        inventory.setItem(SLOT_RAID, buildRaidItem().build());

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemBuilder buildWarItem() {
        Optional<ClanDiplomacyMenu.DenyResult> deny = diplomacyMenu.checkWarDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.war", true);
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_RELATION_HOSTILE)
                .name(plugin.getMessages().component("gui.diplomacy.war.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.war.lore", player));
        if (cbRequired) {
            builder.lore(Component.text("§a✔ Casus Belli готов в инвентаре"));
        }
        return builder;
    }

    private ItemBuilder buildSiegeItem() {
        Optional<ClanDiplomacyMenu.DenyResult> deny = diplomacyMenu.checkSiegeDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        boolean cbRequired = plugin.getConfig().getBoolean("casus-belli.required-for.siege", true);
        ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_BLOCKADE)
                .name(plugin.getMessages().component("gui.diplomacy.siege.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.siege.lore", player));
        if (cbRequired) {
            builder.lore(Component.text("§a✔ Casus Belli готов в инвентаре"));
        }
        return builder;
    }

    private ItemBuilder buildRaidItem() {
        Optional<ClanDiplomacyMenu.DenyResult> deny = diplomacyMenu.checkRaidDeny(player, sourceClan, targetClan);
        if (deny.isPresent()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.raid.name", player))
                    .lore(plugin.getMessages().denyLore(deny.get().key(), deny.get().placeholders(), player));
        }
        return ItemBuilder.head(ItemBuilder.HEAD_ABILITY_BERSERKER)
                .name(plugin.getMessages().component("gui.diplomacy.raid.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.raid.lore", player));
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
