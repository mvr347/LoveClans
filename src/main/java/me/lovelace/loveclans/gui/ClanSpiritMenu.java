package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.spirit.SpiritAbility;
import me.lovelace.loveclans.model.spirit.SpiritBuffLevel;
import me.lovelace.loveclans.util.AbilityLoreFormatter;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Clan Spirit Menu (gui-gen-5 compliant, 27 slots):
 * - Slot 0: Описание Духа клана (что это и зачем нужен).
 * - Slot 12: Активное состояние духа (уровень, энергия, текущий бонус).
 *   Клик переключает отображение всех бафов духа в описании (без уровней, простой список).
 * - Slot 14: Уникальная способность (заблокирована до 10 уровня).
 * - Slots 25/26: Назад в главное меню / Закрыть.
 */
public final class ClanSpiritMenu implements InventoryHolder {
    private static final int SLOT_INFO = 12;
    private static final int SLOT_ABILITY = 14;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private Inventory inventory;
    private boolean showAllBuffs = false;

    public ClanSpiritMenu(LoveClansPlugin plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;
    }

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.spirit.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        // Header слот 0 — голова темы с подробным описанием что такое Дух клана
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_SPIRIT_STAR)
                .name(plugin.getMessages().component("gui.spirit.header.name",
                        Map.of("tag", clan.tag(), "color", clan.tagColor()), player))
                .lore(plugin.getMessages().components("gui.spirit.header.lore", Map.of(), player))
                .build());

        // Слот 12: Информационный предмет (активное состояние / переключение на все бафы)
        inventory.setItem(SLOT_INFO, buildInfoItem(player));

        // Слот 14: Уникальная способность
        int level = clan.spirit().level();
        SpiritAbility ability = clan.spirit().ability();
        ItemBuilder abilityItem = ItemBuilder.head(level < 10 ? ItemBuilder.HEAD_INACTIVE : ItemBuilder.HEAD_SPIRIT_ABILITIES)
                .name(plugin.getMessages().component("gui.spirit.ability.name", player));
        if (level < 10) {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.locked", player));
        } else if (ability == null) {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.unlocked", player))
                    .glow(true);
        } else {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.chosen",
                            Map.of("name", ability.displayName()), player))
                    .lore(AbilityLoreFormatter.format(ability.description()))
                    .glow(true);
        }
        inventory.setItem(SLOT_ABILITY, abilityItem.build());

        // Footer
        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildInfoItem(Player player) {
        int level = clan.spirit().level();
        SpiritBuffLevel buffLevel = SpiritBuffLevel.getByLevel(level);
        String levelName = buffLevel != null ? buffLevel.getName() : SpiritBuffLevel.LEVEL_1.getName();
        long energy = clan.spirit().energy();
        boolean maxed = level >= 10;

        ItemBuilder info = ItemBuilder.head(ItemBuilder.HEAD_SPIRIT_STAR)
                .name(plugin.getMessages().component("gui.spirit.info.name",
                        Map.of("level", String.valueOf(level), "name", levelName), player))
                .glow(true);

        if (!showAllBuffs) {
            // Режим только активного состояния
            if (maxed) {
                info.lore(plugin.getMessages().component("gui.spirit.info.maximum", player));
            } else {
                long next = plugin.getSpiritManager().getExpForNextLevel(level);
                String bar = createProgressBar(energy, next, 20);
                info.lore(plugin.getMessages().component("gui.spirit.info.exp",
                                Map.of("current", String.valueOf(energy), "next", String.valueOf(next)), player))
                        .lore(plugin.getMessages().component("gui.spirit.info.progress", Map.of("bar", bar), player));
            }
            info.lore(plugin.getMessages().component("gui.spirit.info.how", player));

            // Текущее активное благословение
            if (buffLevel != null) {
                info.lore(Component.empty());
                info.lore(plugin.getMessages().component("gui.spirit.info.active-buff",
                        Map.of("name", buffLevel.getName(), "description", buffLevel.getDescription()), player));
            }

            info.lore(Component.empty());
            info.lore(plugin.getMessages().component("gui.spirit.info.toggle-show", player));
        } else {
            // Режим перечисления всех доступных бонусов (без уровней, просто список)
            info.lore(plugin.getMessages().component("gui.spirit.buffs.all-title",
                    Map.of("count", String.valueOf(SpiritBuffLevel.values().length)), player));

            for (SpiritBuffLevel bl : SpiritBuffLevel.values()) {
                info.lore(plugin.getMessages().component("gui.spirit.buffs.simple-entry",
                        Map.of("name", bl.getName(), "description", bl.getDescription()), player));
            }

            info.lore(Component.empty());
            info.lore(plugin.getMessages().component("gui.spirit.info.toggle-hide", player));
        }

        return info.build();
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (slot == SLOT_INFO) {
            showAllBuffs = !showAllBuffs;
            inventory.setItem(SLOT_INFO, buildInfoItem(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }
        if (slot == SLOT_ABILITY) {
            if (clan.spirit().level() < 10) {
                return;
            }
            new ClanSpiritAbilityMenu(plugin, player, clan).open();
        }
    }

    private String createProgressBar(long current, long max, int length) {
        if (max == 0) return "<green>" + "█".repeat(length) + "</green>";
        int filled = (int) (((double) current / max) * length);
        int empty = length - filled;
        return "<green>" + "█".repeat(Math.max(0, filled)) + "</green><gray>" + "█".repeat(Math.max(0, empty)) + "</gray>";
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
