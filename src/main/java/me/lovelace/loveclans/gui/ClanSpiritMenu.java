package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanMember;
import me.lovelace.loveclans.model.spirit.SpiritBuffLevel;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ClanSpiritMenu implements InventoryHolder {
    private final LoveClansPlugin plugin;
    private final Clan clan;
    private Inventory inventory;

    public ClanSpiritMenu(LoveClansPlugin plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;
    }

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessages().component("gui.spirit.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }

        // Center Info
        int currentLevel = clan.spirit().level();
        SpiritBuffLevel buffLevel = SpiritBuffLevel.getByLevel(currentLevel);
        String levelName = buffLevel != null ? buffLevel.getName() : "Пробуждение";
        
        long currentExp = clan.spirit().energy();
        long nextExp = plugin.getSpiritManager().getExpForNextLevel(currentLevel);
        
        String progressStr = createProgressBar(currentExp, nextExp, 20);

        inventory.setItem(13, ItemBuilder.of(Material.NETHER_STAR)
                .name(plugin.getMessages().component("gui.spirit.info.name", Map.of("level", String.valueOf(currentLevel), "name", levelName), player))
                .lore(plugin.getMessages().component("gui.spirit.info.exp", Map.of("current", String.valueOf(currentExp), "next", String.valueOf(nextExp)), player))
                .lore(plugin.getMessages().component("gui.spirit.info.progress", Map.of("bar", progressStr), player))
                .glow(true)
                .build());

        // Buffs List
        ItemBuilder buffsItem = ItemBuilder.of(Material.POTION)
                .name(plugin.getMessages().component("gui.spirit.buffs.name", player))
                .lore(plugin.getMessages().component("gui.spirit.buffs.lore_active", player));
        for (SpiritBuffLevel level : SpiritBuffLevel.values()) {
            boolean unlocked = level.getLevel() <= currentLevel;
            buffsItem.lore(plugin.getMessages().component(
                    unlocked ? "gui.spirit.buffs.entry.unlocked" : "gui.spirit.buffs.entry.locked",
                    Map.of("level", String.valueOf(level.getLevel()), "name", level.getName(), "description", level.getDescription()),
                    player));
        }
        inventory.setItem(30, buffsItem.build());

        // Unique Ability (Level 10)
        me.lovelace.loveclans.model.spirit.SpiritAbility chosenAbility = clan.spirit().ability();
        ItemBuilder abilityItem = ItemBuilder.of(Material.BEACON)
                .name(plugin.getMessages().component("gui.spirit.ability.name", player));
        if (currentLevel < 10) {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.locked", player));
        } else if (chosenAbility == null) {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.unlocked", player))
                    .glow(true);
        } else {
            abilityItem.lore(plugin.getMessages().component("gui.spirit.ability.chosen",
                            Map.of("name", chosenAbility.displayName(), "description", chosenAbility.description()), player))
                    .glow(true);
        }
        inventory.setItem(32, abilityItem.build());

        // History
        inventory.setItem(40, ItemBuilder.of(Material.PAPER)
                .name(plugin.getMessages().component("gui.spirit.history.name", player))
                .lore(plugin.getMessages().component("gui.spirit.history.lore", player))
                .build());

        // Top Contributors
        List<ClanMember> topContributors = clan.members().values().stream()
                .sorted(Comparator.comparingInt(ClanMember::contribution).reversed())
                .limit(5)
                .toList();
        ItemBuilder topItem = ItemBuilder.head(ItemBuilder.HEAD_MEMBERS)
                .name(plugin.getMessages().component("gui.spirit.top.name", player))
                .lore(plugin.getMessages().component("gui.spirit.top.lore", player));
        if (topContributors.isEmpty()) {
            topItem.lore(plugin.getMessages().component("gui.spirit.top.empty", player));
        } else {
            int rank = 1;
            for (ClanMember member : topContributors) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerId());
                String memberName = offline.getName() != null ? offline.getName() : member.playerId().toString().substring(0, 8);
                topItem.lore(plugin.getMessages().component("gui.spirit.top.entry",
                        Map.of("rank", String.valueOf(rank), "player", memberName, "amount", String.valueOf(member.contribution())), player));
                rank++;
            }
        }
        inventory.setItem(48, topItem.build());

        // Back button
        inventory.setItem(50, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, int slot) {
        if (slot == 50) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (slot == 40) {
            plugin.getSpiritManager().getHistoryAsync(clan.id(), 27)
                    .thenAccept(entries -> plugin.runSync(() ->
                            new ClanSpiritHistoryMenu(plugin, player, clan, entries).open()))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (slot == 32) {
            if (clan.spirit().level() < 10) {
                plugin.getMessages().send(player, "gui.spirit.ability.locked-message");
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
