package me.lovelace.loveclans.model.quest.reward;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.QuestReward; // Added import
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public record ItemReward(Material material, int amount, String displayName) implements QuestReward {

    @Override
    public void giveReward(LoveClansPlugin plugin, Player player, Clan clan) {
        if (player != null) {
            ItemStack item = new ItemStack(material, amount);
            if (displayName != null && !displayName.isEmpty()) {
                item = ItemBuilder.of(item).name(plugin.getMessages().component(displayName, player)).build();
            }
            player.getInventory().addItem(item);
            plugin.getMessages().send(player, "quest.reward.item",
                    Map.of("amount", String.valueOf(amount), "item", MiniMessage.miniMessage().serialize(item.displayName())));
        }
    }

    @Override
    public String getDisplayString() {
        return amount + "x " + (displayName != null && !displayName.isEmpty() ? displayName : material.name());
    }
}
