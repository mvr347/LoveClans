package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compact contract picker (§1.4) - one weekly slot and one daily slot, each paired with its own
 * claim button directly next to it. A clan can run one active weekly and one active daily contract
 * at the same time (§1.1). gui_gen 27-slot standard: slot 0 is the thematic quest-board head (this
 * screen isn't a player profile), row 1-8 and 18-24 are frame, and the four content buttons live
 * packed together in 9-17 with weekly/claim-weekly/daily/claim-daily as adjacent pairs - each claim
 * button sits right next to the slot whose progress it claims, so there's no column mismatch between
 * what's being tracked and what claims it (the old 36-slot layout put claim buttons on a different
 * row than the contract they belonged to).
 */
public final class ClanContractsMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_WEEKLY = 11;
    private static final int SLOT_CLAIM_WEEKLY = 12;
    private static final int SLOT_DAILY = 13;
    private static final int SLOT_DAILY_2_OR_CLAIM = 14;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;

    public ClanContractsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.CONTRACTS, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                plugin.getMessages().component("gui.contracts-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        Optional<ClanQuestProgress> weekly = plugin.getContractManager().activeWeekly(clan.id());
        Optional<ClanQuestProgress> daily = plugin.getContractManager().activeDaily(clan.id());

        inventory.setItem(SLOT_INFO, buildInfoItem(weekly, daily, player));
        inventory.setItem(SLOT_WEEKLY, buildWeeklySlot(weekly, player));
        inventory.setItem(SLOT_CLAIM_WEEKLY, buildClaimItem(weekly, player));

        List<ClanContractDefinition> rotation = plugin.getContractManager().dailyRotation();
        if (daily.isPresent()) {
            inventory.setItem(SLOT_DAILY, buildDailyProgressItem(daily.get(), player));
            inventory.setItem(SLOT_DAILY_2_OR_CLAIM, buildClaimItem(daily, player));
        } else {
            if (!rotation.isEmpty()) {
                inventory.setItem(SLOT_DAILY, buildDailyPickItem(rotation.get(0), player));
            }
            if (rotation.size() > 1) {
                inventory.setItem(SLOT_DAILY_2_OR_CLAIM, buildDailyPickItem(rotation.get(1), player));
            }
        }

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
        if (slot == SLOT_WEEKLY && plugin.getContractManager().activeWeekly(clan.id()).isEmpty()) {
            plugin.getContractManager().selectWeeklyAsync(clan, player.getUniqueId())
                    .thenAccept(progress -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.selected");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }

        boolean dailyActive = plugin.getContractManager().activeDaily(clan.id()).isPresent();

        if (slot == SLOT_DAILY_2_OR_CLAIM && dailyActive) {
            plugin.getContractManager().claimDailyAsync(clan, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> open(player, clan)))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }

        if ((slot == SLOT_DAILY || slot == SLOT_DAILY_2_OR_CLAIM) && !dailyActive) {
            List<ClanContractDefinition> rotation = plugin.getContractManager().dailyRotation();
            int index = slot == SLOT_DAILY ? 0 : 1;
            if (index >= rotation.size()) return;
            plugin.getContractManager().selectDailyAsync(clan, player.getUniqueId(), rotation.get(index).id())
                    .thenAccept(progress -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "contract.selected");
                        open(player, clan);
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
        }
    }
}
