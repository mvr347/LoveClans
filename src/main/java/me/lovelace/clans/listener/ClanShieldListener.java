package me.lovelace.clans.listener;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.util.ClanItemFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ClanShieldListener implements Listener {
    private final ClansPlugin plugin;

    public ClanShieldListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != org.bukkit.Material.SHIELD) return;

        // Check if any ingredient is a clan banner
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && ingredient.hasItemMeta() && ingredient.getType().toString().endsWith("_BANNER")) {
                PersistentDataContainer pdc = ingredient.getItemMeta().getPersistentDataContainer();
                if (pdc.has(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING)) {
                    String clanId = pdc.get(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING);
                    // Add clan ID to the shield
                    org.bukkit.inventory.meta.ItemMeta shieldMeta = result.getItemMeta();
                    shieldMeta.getPersistentDataContainer().set(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING, clanId);
                    shieldMeta.getPersistentDataContainer().set(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING, "SHIELD");
                    result.setItemMeta(shieldMeta);
                    event.getInventory().setResult(result);
                    break;
                }
            }
        }
    }
}
