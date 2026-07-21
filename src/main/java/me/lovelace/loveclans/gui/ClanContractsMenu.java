package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
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

/**
 * Compact contract picker (§1.4) - one weekly slot and two daily slots. A clan can run one active
 * weekly and one active daily contract at the same time (§1.1), each with its own claim button, so
 * unlike the original single-contract layout this menu tracks two independent progress states.
 */
public final class ClanContractsMenu {
    private static final int SLOT_WEEKLY = 11;
    private static final int SLOT_DAILY_1 = 13;
    private static final int SLOT_DAILY_2 = 15;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CLAIM_WEEKLY = 20;
    private static final int SLOT_CLAIM_DAILY = 24;
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

        Optional<ClanQuestProgress> weekly = plugin.getContractManager().activeWeekly(clan.id());
        Optional<ClanQuestProgress> daily = plugin.getContractManager().activeDaily(clan.id());

        inventory.setItem(SLOT_INFO, buildInfoItem(weekly, daily, player));
        inventory.setItem(SLOT_WEEKLY, buildWeeklySlot(weekly, player));

        List<ClanContractDefinition> rotation = plugin.getContractManager().dailyRotation();
        if (daily.isPresent()) {
            inventory.setItem(SLOT_DAILY_1, buildDailyProgressItem(daily.get(), player));
        } else {
            inventory.setItem(SLOT_DAILY_1, rotation.size() > 0 ? buildDailyPickItem(rotation.get(0), player) : null);
            inventory.setItem(SLOT_DAILY_2, rotation.size() > 1 ? buildDailyPickItem(rotation.get(1), player) : null);
        }

        inventory.setItem(SLOT_CLAIM_WEEKLY, buildClaimItem(weekly, player));
        inventory.setItem(SLOT_CLAIM_DAILY, buildClaimItem(daily, player));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildInfoItem(Optional<ClanQuestProgress> weekly, Optional<ClanQuestProgress> daily, Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessages().component(weekly.isPresent() ? "gui.contracts.info.weekly-active" : "gui.contracts.info.weekly-none", player));
        lore.add(plugin.getMessages().component(daily.isPresent() ? "gui.contracts.info.daily-active" : "gui.contracts.info.daily-none", player));
        return ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                .name(plugin.getMessages().component("gui.contracts.info.title", player))
                .lore(lore)
                .build();
    }

    private ItemStack buildWeeklySlot(Optional<ClanQuestProgress> weekly, Player player) {
        if (weekly.isPresent()) {
            return buildProgressItem(ContractType.WEEKLY, weekly.get(), player, ItemBuilder.HEAD_WEEKLY_QUESTS);
        }
        Optional<ClanContractDefinition> featured = plugin.getContractManager().featuredWeekly();
        if (featured.isEmpty()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.contracts.item.none-available", player))
                    .build();
        }
        ClanContractDefinition definition = featured.get();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessages().component("gui.contracts.item.description", Map.of("description", definition.description()), player));
        lore.add(plugin.getMessages().component("gui.contracts.item.reward", Map.of("reward", String.valueOf(definition.baseRewardXp())), player));
        lore.add(plugin.getMessages().component("gui.contracts.item.select", player));
        return ItemBuilder.head(ItemBuilder.HEAD_WEEKLY_QUESTS)
                .name(plugin.getMessages().component("gui.contracts.item.name", Map.of("name", definition.displayName()), player))
                .lore(lore)
                .build();
    }

    private ItemStack buildDailyPickItem(ClanContractDefinition definition, Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessages().component("gui.contracts.item.description", Map.of("description", definition.description()), player));
        lore.add(plugin.getMessages().component("gui.contracts.item.reward", Map.of("reward", String.valueOf(definition.baseRewardXp())), player));
        lore.add(plugin.getMessages().component("gui.contracts.item.select", player));
        return ItemBuilder.head(ItemBuilder.HEAD_DAILY_QUESTS)
                .name(plugin.getMessages().component("gui.contracts.item.name", Map.of("name", definition.displayName()), player))
                .lore(lore)
                .build();
    }

    private ItemStack buildDailyProgressItem(ClanQuestProgress progress, Player player) {
        return buildProgressItem(ContractType.DAILY, progress, player, ItemBuilder.HEAD_DAILY_QUESTS);
    }

    private ItemStack buildProgressItem(ContractType type, ClanQuestProgress progress, Player player, String head) {
        Optional<ClanContractDefinition> definitionOpt = plugin.getContractManager().definition(type, progress.questId());
        if (definitionOpt.isEmpty()) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.contracts.info.none.name", player))
                    .build();
        }
        ClanContractDefinition definition = definitionOpt.get();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getContractManager().displayObjective(definition, progress).getDisplayName(player, progress.progress()));
        lore.add(plugin.getMessages().component("gui.contracts.item.current", player));
        if (progress.completed()) {
            lore.add(plugin.getMessages().component(progress.claimed()
                    ? "gui.contracts.info.claimed" : "gui.contracts.info.ready-to-claim", player));
        }
        ItemBuilder builder = ItemBuilder.head(head)
                .name(plugin.getMessages().component("gui.contracts.item.name", Map.of("name", definition.displayName()), player))
                .lore(lore);
        if (progress.completed() && !progress.claimed()) builder.glow(true);
        return builder.build();
    }

    private ItemStack buildClaimItem(Optional<ClanQuestProgress> progress, Player player) {
        boolean canClaim = progress.map(p -> p.completed() && !p.claimed()).orElse(false);
        if (canClaim) {
            return ItemBuilder.head(ItemBuilder.HEAD_COMPLETED_QUESTS)
                    .name(plugin.getMessages().component("gui.contracts.claim.ready.name", player))
                    .lore(plugin.getMessages().component("gui.contracts.claim.ready.lore", player))
                    .glow(true)
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
        if (slot == SLOT_CLAIM_WEEKLY) {
            plugin.getContractManager().claimWeeklyAsync(clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> open(player, clan)))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (slot == SLOT_CLAIM_DAILY) {
            plugin.getContractManager().claimDailyAsync(clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> open(player, clan)))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if (slot == SLOT_WEEKLY && plugin.getContractManager().activeWeekly(clan.id()).isEmpty()) {
            plugin.getContractManager().selectWeeklyAsync(clan, player.getUniqueId())
                    .thenAccept(progress -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.selected");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }
        if ((slot == SLOT_DAILY_1 || slot == SLOT_DAILY_2) && plugin.getContractManager().activeDaily(clan.id()).isEmpty()) {
            List<ClanContractDefinition> rotation = plugin.getContractManager().dailyRotation();
            int index = slot == SLOT_DAILY_1 ? 0 : 1;
            if (index >= rotation.size()) return;
            plugin.getContractManager().selectDailyAsync(clan, player.getUniqueId(), rotation.get(index).id())
                    .thenAccept(progress -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.selected");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
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
