package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ClanContractsMenu {
    private static final int[] CONTRACT_SLOTS = {11, 13, 15};
    private static final String[] CONTRACT_IDS = {"labor", "blood", "purification"};
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CLAIM = 22;
    private static final int SLOT_BACK = 34;
    private static final int SLOT_CLOSE = 35;

    private final LoveClansPlugin plugin;

    public ClanContractsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        Inventory inventory = Bukkit.createInventory(new ClanMenuHolder(ClanMenuType.CONTRACTS, clan.id()), 36,
                plugin.getMessages().component("gui.contracts-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        fillGlass(inventory);

        Optional<ClanQuestProgress> active = plugin.getContractManager().activeContract(clan.id());
        inventory.setItem(SLOT_INFO, buildInfoItem(active, player));

        for (int i = 0; i < CONTRACT_SLOTS.length; i++) {
            plugin.getContractManager().definition(CONTRACT_IDS[i]).ifPresent(definition ->
                    inventory.setItem(CONTRACT_SLOTS[i], buildContractItem(definition, active, player)));
        }

        boolean canClaim = active.map(p -> p.completed() && !p.claimed()).orElse(false);
        inventory.setItem(SLOT_CLAIM, buildClaimItem(canClaim, player));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildInfoItem(Optional<ClanQuestProgress> active, Player player) {
        if (active.isEmpty()) {
            return ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                    .name(plugin.getMessages().component("gui.contracts.info.none.name", player))
                    .lore(plugin.getMessages().component("gui.contracts.info.none.lore", player))
                    .build();
        }
        ClanQuestProgress progress = active.get();
        ClanContractDefinition definition = plugin.getContractManager().definition(progress.questId()).orElse(null);
        if (definition == null) {
            return ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                    .name(plugin.getMessages().component("gui.contracts.info.none.name", player))
                    .build();
        }
        int current = progress.objectiveProgress().getOrDefault(0, 0);
        List<Component> lore = new ArrayList<>();
        lore.add(definition.objective().getDisplayName(player, current));
        if (progress.completed()) {
            lore.add(plugin.getMessages().component(progress.claimed()
                    ? "gui.contracts.info.claimed" : "gui.contracts.info.ready-to-claim", player));
        }
        return ItemBuilder.head(ItemBuilder.HEAD_WEEKLY_QUESTS)
                .name(plugin.getMessages().component("gui.contracts.info.active.name",
                        Map.of("name", definition.displayName()), player))
                .lore(lore)
                .build();
    }

    private ItemStack buildContractItem(ClanContractDefinition definition, Optional<ClanQuestProgress> active, Player player) {
        boolean isCurrent = active.map(p -> p.questId().equals(definition.id())).orElse(false);
        boolean canSelect = active.isEmpty();

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessages().component("gui.contracts.item.description",
                Map.of("description", definition.description()), player));
        lore.add(plugin.getMessages().component("gui.contracts.item.reward",
                Map.of("reward", definition.reward().getDisplayString()), player));

        if (isCurrent) {
            lore.add(plugin.getMessages().component("gui.contracts.item.current", player));
            return ItemBuilder.head(ItemBuilder.HEAD_WEEKLY_QUESTS)
                    .name(plugin.getMessages().component("gui.contracts.item.name",
                            Map.of("name", definition.displayName()), player))
                    .lore(lore)
                    .build();
        }
        if (!canSelect) {
            lore.add(plugin.getMessages().component("gui.contracts.item.locked", player));
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.contracts.item.name",
                            Map.of("name", definition.displayName()), player))
                    .lore(lore)
                    .build();
        }
        lore.add(plugin.getMessages().component("gui.contracts.item.select", player));
        return ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                .name(plugin.getMessages().component("gui.contracts.item.name",
                        Map.of("name", definition.displayName()), player))
                .lore(lore)
                .build();
    }

    private ItemStack buildClaimItem(boolean canClaim, Player player) {
        if (canClaim) {
            return ItemBuilder.head(ItemBuilder.HEAD_COMPLETED_QUESTS)
                    .name(plugin.getMessages().component("gui.contracts.claim.ready.name", player))
                    .lore(plugin.getMessages().component("gui.contracts.claim.ready.lore", player))
                    .build();
        }
        return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component("gui.contracts.claim.unavailable.name", player))
                .build();
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (slot == SLOT_CLAIM) {
            plugin.getContractManager().claimRewardAsync(clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.reward-claimed");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        for (int i = 0; i < CONTRACT_SLOTS.length; i++) {
            if (slot != CONTRACT_SLOTS[i]) continue;
            plugin.getContractManager().selectContractAsync(clan, player.getUniqueId(), CONTRACT_IDS[i])
                    .thenAccept(progress -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.selected");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
    }

    private void fillGlass(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .build());
        }
    }
}
