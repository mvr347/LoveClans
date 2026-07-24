package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import me.lovelace.loveclans.util.AbilityLoreFormatter;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
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

    private String headFor(SpiritAbility ability) {
        return switch (ability) {
            case PHOENIX -> ItemBuilder.HEAD_ABILITY_PHOENIX;
            case BERSERKER -> ItemBuilder.HEAD_ABILITY_BERSERKER;
            case SANCTUARY -> ItemBuilder.HEAD_ABILITY_SANCTUARY;
        };
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.spirit.ability-menu.title",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        SpiritAbility current = clan.spirit().ability();
        long now = System.currentTimeMillis();
        long cooldownRemaining = clan.spirit().abilityCooldownRemaining(now);
        SpiritAbility[] abilities = SpiritAbility.values();
        for (int i = 0; i < ABILITY_SLOTS.length && i < abilities.length; i++) {
            SpiritAbility ability = abilities[i];
            boolean selected = ability == current;
            boolean lockedByCooldown = !selected && current != null && cooldownRemaining > 0;

            ItemBuilder builder = ItemBuilder.head(lockedByCooldown ? ItemBuilder.HEAD_INACTIVE : headFor(ability))
                    .name(plugin.getMessages().component("gui.spirit.ability-menu.item.name",
                            Map.of("name", ability.displayName()), player))
                    // Многострочное описание с подсветкой баффов: зелёный — позитивный эффект,
                    // жёлтый — условие/порог, красный — эффект на противника.
                    .lore(AbilityLoreFormatter.format(ability.description()));

            if (selected) {
                builder.lore(plugin.getMessages().component("gui.spirit.ability-menu.item.selected", player)).glow(true);
            } else if (lockedByCooldown) {
                builder.lore(plugin.getMessages().component("gui.spirit.ability-menu.item.on-cooldown",
                        Map.of("time", TimeUtil.formatDuration(cooldownRemaining)), player));
            } else {
                builder.lore(plugin.getMessages().component("gui.spirit.ability-menu.item.click-to-select", player));
            }

            inventory.setItem(ABILITY_SLOTS[i], builder.build());
        }

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        if (slot == 26) {
            clicker.closeInventory();
            return;
        }
        if (slot == 25) {
            plugin.getGuiManager().openSpiritMenu(clicker, clan);
            return;
        }

        SpiritAbility current = clan.spirit().ability();
        long cooldownRemaining = clan.spirit().abilityCooldownRemaining(System.currentTimeMillis());

        SpiritAbility[] abilities = SpiritAbility.values();
        for (int i = 0; i < ABILITY_SLOTS.length && i < abilities.length; i++) {
            if (slot != ABILITY_SLOTS[i]) continue;
            SpiritAbility ability = abilities[i];
            if (ability != current && current != null && cooldownRemaining > 0) {
                plugin.getMessages().send(clicker, "gui.spirit.ability.cooldown", Map.of("time", TimeUtil.formatDuration(cooldownRemaining)));
                return;
            }
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
