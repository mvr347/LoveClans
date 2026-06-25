package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public final class ClanSpiritAbilityMenu implements InventoryHolder {
    private static final int[] ABILITY_SLOTS = {11, 13, 15};

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan clan;
    private Inventory inventory;

    public ClanSpiritAbilityMenu(LoveClansPlugin plugin, Player player, Clan clan) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
    }

    private Material materialFor(SpiritAbility ability) {
        return switch (ability) {
            case PHOENIX -> Material.TOTEM_OF_UNDYING;
            case BERSERKER -> Material.IRON_SWORD;
            case SANCTUARY -> Material.SHIELD;
        };
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.spirit.ability-menu.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        SpiritAbility current = clan.spirit().ability();
        SpiritAbility[] abilities = SpiritAbility.values();
        for (int i = 0; i < ABILITY_SLOTS.length && i < abilities.length; i++) {
            SpiritAbility ability = abilities[i];
            boolean selected = ability == current;

            ItemBuilder builder = ItemBuilder.of(materialFor(ability))
                    .name(plugin.getMessages().component("gui.spirit.ability-menu.item.name",
                            Map.of("name", ability.displayName()), player))
                    .lore(plugin.getMessages().component("gui.spirit.ability-menu.item.description",
                            Map.of("description", ability.description()), player));

            if (selected) {
                builder.lore(plugin.getMessages().component("gui.spirit.ability-menu.item.selected", player)).glow(true);
            } else {
                builder.lore(plugin.getMessages().component("gui.spirit.ability-menu.item.click-to-select", player));
            }

            inventory.setItem(ABILITY_SLOTS[i], builder.build());
        }

        inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        if (slot == 22) {
            plugin.getGuiManager().openSpiritMenu(clicker, clan);
            return;
        }

        SpiritAbility[] abilities = SpiritAbility.values();
        for (int i = 0; i < ABILITY_SLOTS.length && i < abilities.length; i++) {
            if (slot != ABILITY_SLOTS[i]) continue;
            SpiritAbility ability = abilities[i];
            plugin.getClanManager().chooseSpiritAbilityAsync(clan, clicker.getUniqueId(), ability)
                    .thenAccept(updated -> plugin.runSync(() -> {
                        plugin.getMessages().send(clicker, "gui.spirit.ability-menu.success", Map.of("name", ability.displayName()));
                        new ClanSpiritAbilityMenu(plugin, clicker, updated).open();
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; });
            return;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
