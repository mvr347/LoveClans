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
import java.util.Optional;
import java.util.UUID;

public final class ClanItemFactory {

    private final LoveClansPlugin plugin;
    public static final NamespacedKey BANNER_TYPE_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "banner_type");
    public static final NamespacedKey CLAN_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "clan_id");
    public static final NamespacedKey WAR_COMPASS_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "war_compass_war_id");
    public static final NamespacedKey RAID_COMPASS_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "raid_compass_raid_id");
    public static final NamespacedKey SIEGE_COMPASS_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "siege_compass_siege_id");
    public static final NamespacedKey RAID_CHEST_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "raid_chest_id");
    public static final NamespacedKey CAPTURED_BANNER_WAR_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "captured_banner_war_id");
    public static final NamespacedKey SIEGE_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "siege_id");
    public static final NamespacedKey SIEGE_CAMP_INDEX_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "siege_camp_index");
    public static final NamespacedKey CLAN_CREATION_BANNER_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "clan_creation_banner");

    public static final NamespacedKey CASUS_BELLI_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "casus_belli");
    public static final NamespacedKey CASUS_TARGET_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "casus_target_id");
    public static final NamespacedKey CASUS_CONFLICT_TYPE_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "casus_conflict_type");
    public static final NamespacedKey CASUS_REASON_ID_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "casus_reason_id");
    public static final NamespacedKey CASUS_EXPIRES_AT_KEY = new NamespacedKey(LoveClansPlugin.getPlugin(LoveClansPlugin.class), "casus_expires_at");

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

    /**
     * Creates a Clan Creation Banner - an unassigned banner bought from the NPC merchant.
     * Placing it will found a clan and establish its capital territory.
     */
    public ItemStack createClanCreationBanner() {
        ItemStack banner = new ItemStack(Material.RED_BANNER);
        ItemMeta meta = banner.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CLAN_CREATION_BANNER_KEY, PersistentDataType.INTEGER, 1);

            meta.displayName(plugin.getMessages().component("item.clan-creation-banner.name", Map.of(), null));
            meta.lore(plugin.getMessages().components("item.clan-creation-banner.lore", null));
            banner.setItemMeta(meta);
        }
        return banner;
    }

    /**
     * Checks if the given item is an unassigned Clan Creation Banner.
     */
    public boolean isClanCreationBanner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(CLAN_CREATION_BANNER_KEY, PersistentDataType.INTEGER);
    }

    public record CasusBelliData(UUID targetClanId, String conflictType, String reasonId, long expiresAt) {
        public boolean isExpired(long now) {
            return expiresAt > 0 && now >= expiresAt;
        }
    }

    public ItemStack createCasusBelliItem(UUID targetClanId, String targetClanName, String conflictType, String reasonId, String reasonTitle, long expiresAt, boolean isJust) {
        return createCasusBelliItem(targetClanId, targetClanName, conflictType, reasonId, reasonTitle, expiresAt, isJust, null);
    }

    public ItemStack createCasusBelliItem(UUID targetClanId, String targetClanName, String conflictType, String reasonId, String reasonTitle, long expiresAt, boolean isJust, String customItemsAdderId) {
        String iaId = (customItemsAdderId != null && !customItemsAdderId.isBlank())
                ? customItemsAdderId
                : getCasusBelliItemsAdderId(conflictType);

        ItemStack item = null;
        if (iaId != null && !iaId.isBlank()) {
            item = ItemsAdderHook.createCustomStack(iaId);
        }
        if (item == null) {
            item = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CASUS_BELLI_KEY, PersistentDataType.INTEGER, 1);
            pdc.set(CASUS_TARGET_ID_KEY, PersistentDataType.STRING, targetClanId.toString());
            pdc.set(CASUS_CONFLICT_TYPE_KEY, PersistentDataType.STRING, conflictType.toUpperCase());
            pdc.set(CASUS_REASON_ID_KEY, PersistentDataType.STRING, reasonId.toLowerCase());
            pdc.set(CASUS_EXPIRES_AT_KEY, PersistentDataType.LONG, expiresAt);

            String conflictTitle = conflictType.equalsIgnoreCase("WAR") ? "Война" : "Осада";
            String title = (isJust ? "§cКазус белли: §e" : "§6Казус белли: §f") + reasonTitle;
            meta.displayName(Component.text(title));

            List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text("§8────────────────────────"));
            lore.add(Component.text("§7Цель:  §c[" + targetClanName + "]"));
            lore.add(Component.text("§7Тип:   §f" + conflictTitle));
            lore.add(Component.text("§7Повод: §a" + reasonTitle));
            if (expiresAt > 0) {
                long remaining = Math.max(0, expiresAt - System.currentTimeMillis());
                lore.add(Component.text("§7Годен: §e" + TimeUtil.formatDuration(remaining)));
            } else {
                lore.add(Component.text("§7Годен: §eБессрочно"));
            }
            lore.add(Component.text("§8────────────────────────"));
            if (isJust) {
                lore.add(Component.text("§aСправедливое право на объявление"));
                lore.add(Component.text("§aоформлено у Гильдмастера."));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(Component.text("§7Повод сомнительный, но оплачен"));
                lore.add(Component.text("§7звонкой монетой."));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getCasusBelliItemsAdderId(String conflictType) {
        if (conflictType != null) {
            String key = conflictType.equalsIgnoreCase("WAR") ? "casus-belli.war-itemsadder-id" : "casus-belli.siege-itemsadder-id";
            String specific = plugin.getConfig().getString(key, "");
            if (specific != null && !specific.isBlank()) {
                return specific;
            }
        }
        return plugin.getConfig().getString("casus-belli.itemsadder-id", "");
    }

    public Optional<CasusBelliData> getCasusBelliData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(CASUS_BELLI_KEY, PersistentDataType.INTEGER)) return Optional.empty();
        try {
            String targetStr = pdc.get(CASUS_TARGET_ID_KEY, PersistentDataType.STRING);
            String typeStr = pdc.get(CASUS_CONFLICT_TYPE_KEY, PersistentDataType.STRING);
            String reasonStr = pdc.get(CASUS_REASON_ID_KEY, PersistentDataType.STRING);
            Long exp = pdc.get(CASUS_EXPIRES_AT_KEY, PersistentDataType.LONG);
            if (targetStr == null || typeStr == null) return Optional.empty();
            return Optional.of(new CasusBelliData(
                    UUID.fromString(targetStr),
                    typeStr,
                    reasonStr != null ? reasonStr : "",
                    exp != null ? exp : 0L
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean matchesCasusBelli(ItemStack item, UUID targetClanId, String conflictType) {
        return getCasusBelliData(item).map(data ->
                data.targetClanId().equals(targetClanId)
                && data.conflictType().equalsIgnoreCase(conflictType)
                && !data.isExpired(System.currentTimeMillis())
        ).orElse(false);
    }

    public boolean hasCasusBelli(Player player, UUID targetClanId, String conflictType) {
        if (player == null) return false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && matchesCasusBelli(stack, targetClanId, conflictType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes ONE matching, unexpired casus belli out of the player's inventory and hands back a copy of it, so a failed
     * declaration can return exactly that item. Main thread only.
     */
    public Optional<ItemStack> takeCasusBelli(Player player, UUID targetClanId, String conflictType) {
        if (player == null) return Optional.empty();
        long now = System.currentTimeMillis();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            Optional<CasusBelliData> data = getCasusBelliData(stack);
            if (data.isPresent()) {
                CasusBelliData cb = data.get();
                if (cb.targetClanId().equals(targetClanId) && cb.conflictType().equalsIgnoreCase(conflictType) && !cb.isExpired(now)) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    stack.setAmount(stack.getAmount() - 1);
                    player.getInventory().setItem(i, stack.getAmount() > 0 ? stack : null);
                    return Optional.of(one);
                }
            }
        }
        return Optional.empty();
    }

    public boolean consumeCasusBelli(Player player, UUID targetClanId, String conflictType) {
        return takeCasusBelli(player, targetClanId, conflictType).isPresent();
    }
}
