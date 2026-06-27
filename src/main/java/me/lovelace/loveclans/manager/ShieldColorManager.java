package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.block.Banner;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Отвечает за автоматическую окраску щитов (Material.SHIELD) в цвет тега клана.
 * Щит — это предмет с BlockStateMeta, чей BlockState — Banner, поэтому окраска
 * делается через Banner#setBaseColor, а не через несуществующий ShieldMeta/BannerMeta.
 */
public final class ShieldColorManager {
    private final LoveClansPlugin plugin;

    // Маппинг "тег цвета клана" (например "<gold>") -> DyeColor, для покраски щитов.
    // Строится из config.yml: clans.available-colors.<key>.{tag, dye-color}.
    // Кэшируется при загрузке конфига; перезагружается через reloadColorMap().
    private Map<String, DyeColor> tagToDyeColor = Map.of();

    public ShieldColorManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        reloadColorMap();
    }

    public void reloadColorMap() {
        Map<String, DyeColor> map = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("clans.available-colors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String tag = section.getString(key + ".tag");
                String dyeColorName = section.getString(key + ".dye-color");
                if (tag == null || dyeColorName == null) continue;
                try {
                    map.put(tag, DyeColor.valueOf(dyeColorName.toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Некорректный dye-color у цвета клана '" + key + "': " + dyeColorName);
                }
            }
        }
        this.tagToDyeColor = Map.copyOf(map);
    }

    public java.util.Optional<DyeColor> resolveDyeColor(Clan clan) {
        if (clan == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(tagToDyeColor.get(clan.tagColor()));
    }

    /**
     * Перекрашивает все щиты в инвентаре и экипировке игрока в цвет клана.
     * Помечаем щит PDC-тэгом, чтобы при выходе из клана точно знать, какие щиты сбрасывать
     * (а не трогать щиты, которые игрок специально покрасил/получил вручную до вступления).
     */
    public void applyToPlayer(Player player, Clan clan) {
        java.util.Optional<DyeColor> colorOpt = resolveDyeColor(clan);
        if (colorOpt.isEmpty()) return;
        DyeColor color = colorOpt.get();

        forEachShield(player, item -> colorShield(item, color));
    }

    /**
     * Сбрасывает цвет щитов, помеченных как "окрашенные кланом", при выходе игрока из клана.
     * Сбрасываем только щиты с нашим PDC-тэгом — чтобы не трогать щиты, не связанные с кланом.
     */
    public void resetForPlayer(Player player) {
        forEachShield(player, this::resetShield);
    }

    // Важно: ItemStack, полученный из инвентаря/экипировки — это снимок, а не живая ссылка.
    // Мутация его меты не гарантированно отражается в инвентаре, поэтому после action.accept
    // явно записываем предмет обратно через setItem/setItemInMainHand/setItemInOffHand.
    private void forEachShield(Player player, java.util.function.Consumer<ItemStack> action) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() == Material.SHIELD) {
                action.accept(item);
                inv.setItem(slot, item);
            }
        }
        EntityEquipment equipment = player.getEquipment();
        if (equipment != null) {
            ItemStack offHand = equipment.getItemInOffHand();
            if (offHand.getType() == Material.SHIELD) {
                action.accept(offHand);
                equipment.setItemInOffHand(offHand);
            }
            ItemStack mainHand = equipment.getItemInMainHand();
            if (mainHand.getType() == Material.SHIELD) {
                action.accept(mainHand);
                equipment.setItemInMainHand(mainHand);
            }
        }
    }

    public void colorShield(ItemStack shield, DyeColor color) {
        if (shield == null || shield.getType() != Material.SHIELD) return;
        if (!(shield.getItemMeta() instanceof BlockStateMeta blockStateMeta)) return;
        if (!(blockStateMeta.getBlockState() instanceof Banner banner)) return;
        // BlockState здесь "виртуальный" (взят из меты предмета, не из реального блока в мире),
        // поэтому Banner#update() не нужен и не применим — просто записываем state обратно в мету.
        banner.setBaseColor(color);
        blockStateMeta.setBlockState(banner);
        blockStateMeta.getPersistentDataContainer().set(clanShieldKey(), PersistentDataType.BYTE, (byte) 1);
        shield.setItemMeta(blockStateMeta);
    }

    public void resetShield(ItemStack shield) {
        if (shield == null || shield.getType() != Material.SHIELD) return;
        if (!(shield.getItemMeta() instanceof BlockStateMeta blockStateMeta)) return;
        // Сбрасываем только если щит был ранее окрашен кланом (помечен нашим PDC-тэгом).
        if (!blockStateMeta.getPersistentDataContainer().has(clanShieldKey(), PersistentDataType.BYTE)) return;
        if (!(blockStateMeta.getBlockState() instanceof Banner banner)) return;
        banner.setBaseColor(DyeColor.WHITE);
        banner.setPatterns(java.util.List.of());
        blockStateMeta.setBlockState(banner);
        blockStateMeta.getPersistentDataContainer().remove(clanShieldKey());
        shield.setItemMeta(blockStateMeta);
    }

    private org.bukkit.NamespacedKey clanShieldKey() {
        return new org.bukkit.NamespacedKey(plugin, "clan-colored-shield");
    }
}
