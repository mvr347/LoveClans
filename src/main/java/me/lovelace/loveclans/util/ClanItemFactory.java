package me.lovelace.loveclans.util;

import me.lovelace.loveclans.LoveClansPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClanItemFactory {

    private final LoveClansPlugin plugin;
    public static final NamespacedKey BANNER_TYPE_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "banner_type");
    public static final NamespacedKey CLAN_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "clan_id");
    // Помечает боевой компас войны идентификатором войны, для которой он выдан - используется,
    // чтобы надёжно находить и изымать компас у игрока (независимо от локали отображаемого имени).
    public static final NamespacedKey WAR_COMPASS_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "war_compass_war_id");
    // Помечает знамя, захваченное (сломанное и поднятое) вражеским игроком во время войны,
    // идентификатором этой войны - чтобы его можно было изъять при завершении войны.
    public static final NamespacedKey CAPTURED_BANNER_WAR_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "captured_banner_war_id");
    // Помечают физический блок осадного лагеря (§3.1) идентификатором осады и индексом лагеря
    // в её списке - используются, чтобы связать BlockBreakEvent на этом блоке с конкретным
    // SiegeCamp в SiegeManager.
    public static final NamespacedKey SIEGE_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "siege_id");
    public static final NamespacedKey SIEGE_CAMP_INDEX_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "siege_camp_index");

    public ClanItemFactory(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a Capital Banner ItemStack with specific NBT tags.
     *
     * @param clanId The UUID of the clan.
     * @param clanName The name of the clan.
     * @return The ItemStack representing the Capital Banner.
     */
    public ItemStack createCapitalBanner(UUID clanId, String clanName) {

        ItemStack banner = new ItemStack(Material.RED_BANNER);
        ItemMeta meta = banner.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BANNER_TYPE_KEY, PersistentDataType.STRING, "CAPITAL");
            pdc.set(CLAN_ID_KEY, PersistentDataType.STRING, clanId.toString());

            meta.displayName(plugin.getMessages().component("item.capital-banner.name", Map.of("clan", clanName), null));
            meta.lore(List.of(
                    plugin.getMessages().component("item.capital-banner.lore.type", Map.of(), null),
                    plugin.getMessages().component("item.capital-banner.lore.clan", Map.of("clan", clanName), null),
                    plugin.getMessages().component("item.capital-banner.lore.info", Map.of(), null)
            ));
            banner.setItemMeta(meta);
        }
        return banner;
    }

    /**
     * Creates a Territory Banner ItemStack with specific NBT tags.
     *
     * @param clanId The UUID of the clan.
     * @param clanName The name of the clan.
     * @return The ItemStack representing the Territory Banner.
     */
    public ItemStack createTerritoryBanner(UUID clanId, String clanName) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER); // Default for territory banners
        ItemMeta meta = banner.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BANNER_TYPE_KEY, PersistentDataType.STRING, "TERRITORY");
            pdc.set(CLAN_ID_KEY, PersistentDataType.STRING, clanId.toString());

            meta.displayName(plugin.getMessages().component("item.territory-banner.name", Map.of("clan", clanName), null));
            meta.lore(List.of(
                    plugin.getMessages().component("item.territory-banner.lore.type", Map.of(), null),
                    plugin.getMessages().component("item.territory-banner.lore.clan", Map.of("clan", clanName), null),
                    plugin.getMessages().component("item.territory-banner.lore.info", Map.of(), null)
            ));

            banner.setItemMeta(meta);
        }
        return banner;
    }

    /**
     * Creates a captured war banner ItemStack - given to the player who breaks a defending
     * clan's contested territory banner during a war. Tagged with the war id so it can be
     * reliably found and confiscated when the war ends (peace, timeout, victory or defeat).
     *
     * @param warId The UUID of the war during which the banner was captured.
     * @param defenderClanId The UUID of the clan that owned the banner.
     * @param defenderClanName The name of the clan that owned the banner.
     * @return The ItemStack representing the captured banner.
     */
    public ItemStack createCapturedBanner(UUID warId, UUID defenderClanId, String defenderClanName) {
        ItemStack banner = new ItemStack(Material.RED_BANNER);
        ItemMeta meta = banner.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BANNER_TYPE_KEY, PersistentDataType.STRING, "TERRITORY");
            pdc.set(CLAN_ID_KEY, PersistentDataType.STRING, defenderClanId.toString());
            pdc.set(CAPTURED_BANNER_WAR_KEY, PersistentDataType.STRING, warId.toString());

            meta.displayName(plugin.getMessages().component("item.captured-banner.name", Map.of("clan", defenderClanName), null));
            meta.lore(plugin.getMessages().components("item.captured-banner.lore", null));
            banner.setItemMeta(meta);
        }
        return banner;
    }

    /**
     * Checks whether an item is the captured war banner belonging to the given war.
     */
    public boolean isCapturedBanner(ItemStack item, UUID warId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String taggedWarId = pdc.get(CAPTURED_BANNER_WAR_KEY, PersistentDataType.STRING);
        return taggedWarId != null && taggedWarId.equals(warId.toString());
    }

    /**
     * Creates a clan banner ItemStack based on its type.
     *
     * @param bannerType The type of banner ("CAPITAL" or "TERRITORY").
     * @param clanId The UUID of the clan.
     * @param clanName The name of the clan.
     * @return The ItemStack representing the clan banner.
     */
    public ItemStack createBannerByType(String bannerType, UUID clanId, String clanName) {
        if ("CAPITAL".equals(bannerType)) {
            return createCapitalBanner(clanId, clanName);
        } else if ("TERRITORY".equals(bannerType)) {
            return createTerritoryBanner(clanId, clanName);
        }
        return new ItemStack(Material.AIR); // Should not happen
    }

    /**
     * Checks if a player's inventory or Ender Chest contains a banner with the specified NBT tags.
     *
     * @param player The player to check.
     * @param bannerType The type of banner ("CAPITAL" or "TERRITORY").
     * @param clanId The UUID of the clan. Can be null if checking for any CAPITAL banner before clan creation.
     * @return True if an existing banner is found, false otherwise.
     */
    public boolean hasExistingBanner(Player player, String bannerType, UUID clanId) {
        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMatchingBanner(item, bannerType, clanId)) {
                return true;
            }
        }
        // Check Ender Chest
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isMatchingBanner(item, bannerType, clanId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMatchingBanner(ItemStack item, String bannerType, UUID clanId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String type = pdc.get(BANNER_TYPE_KEY, PersistentDataType.STRING);
        String id = pdc.get(CLAN_ID_KEY, PersistentDataType.STRING);

        // If clanId is null, we are checking for *any* banner of the given type (e.g., before clan creation)
        if (clanId == null) {
            return bannerType.equals(type) && id != null;
        }
        return bannerType.equals(type) && clanId.toString().equals(id);
    }
}