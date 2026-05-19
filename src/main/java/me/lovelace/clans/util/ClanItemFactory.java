package me.lovelace.clans.util;

import me.lovelace.clans.ClansPlugin;
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

    private final ClansPlugin plugin;
    public static final NamespacedKey BANNER_TYPE_KEY = new NamespacedKey(ClansPlugin.getPlugin(ClansPlugin.class), "banner_type");
    public static final NamespacedKey CLAN_ID_KEY = new NamespacedKey(ClansPlugin.getPlugin(ClansPlugin.class), "clan_id");

    public ClanItemFactory(ClansPlugin plugin) {
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