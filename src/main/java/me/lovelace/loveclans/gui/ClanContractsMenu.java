package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ContractManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.TimeUtil;
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
 * Clan vows screen (gui_gen v2.1, 27 slots): two control buttons in the header - the weekly and the monthly
 * vow - and, under each button in the work zone, the vow the clan is currently running. A button
 * opens the pick-one-of-three screen when the clan has no vow of that period yet, or pays out the reward of a
 * finished one. The screen itself never changes anything: every decision goes through {@link ContractManager}.
 */
public final class ClanContractsMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_WEEKLY = 3;
    private static final int SLOT_MONTHLY = 5;
    // Work-zone detail items sit directly under their header button.
    private static final int SLOT_WEEKLY_DETAIL = 12;
    private static final int SLOT_MONTHLY_DETAIL = 14;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;
    private final ClanContractChoiceMenu choiceMenu;

    public ClanContractsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.choiceMenu = new ClanContractChoiceMenu(plugin);
    }

    public ClanContractChoiceMenu choiceMenu() {
        return choiceMenu;
    }

    public void open(Player player, Clan clan) {
        // A stale, already expired vow must neither show up as active nor block taking a new one.
        plugin.getContractManager().settleExpired(clan);

        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.CONTRACTS, clan.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                plugin.getMessages().component("gui.contracts-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                .name(plugin.getMessages().component("gui.contracts.board.title", player))
                .lore(plugin.getMessages().components("gui.contracts.board.lore", player))
                .build());

        // The two control buttons replace header glass at the centered positions of GuiFrames#controlSlots(2).
        inventory.setItem(SLOT_WEEKLY, buildButton(ContractType.WEEKLY, clan, player));
        inventory.setItem(SLOT_MONTHLY, buildButton(ContractType.MONTHLY, clan, player));

        buildDetail(ContractType.WEEKLY, clan, player).ifPresent(item -> inventory.setItem(SLOT_WEEKLY_DETAIL, item));
        buildDetail(ContractType.MONTHLY, clan, player).ifPresent(item -> inventory.setItem(SLOT_MONTHLY_DETAIL, item));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private static String headFor(ContractType type) {
        return type == ContractType.MONTHLY ? ItemBuilder.HEAD_MONTHLY_QUESTS : ItemBuilder.HEAD_WEEKLY_QUESTS;
    }

    private static String periodKey(ContractType type) {
        return type == ContractType.MONTHLY ? "monthly" : "weekly";
    }

    private ItemStack buildButton(ContractType type, Clan clan, Player player) {
        ContractManager manager = plugin.getContractManager();
        String period = periodKey(type);
        Optional<ClanQuestProgress> active = manager.active(clan.id(), type);
        List<Component> lore = new ArrayList<>();
        boolean ready = false;
        boolean available = true;

        if (active.isPresent()) {
            ClanQuestProgress progress = active.get();
            String definitionName = manager.definition(type, progress.questId()).map(ClanContractDefinition::displayName).orElse(progress.questId());
            lore.add(plugin.getMessages().component("gui.contracts.button.current", Map.of("name", definitionName), player));
            if (progress.completed() && !progress.claimed()) {
                ready = true;
                lore.add(plugin.getMessages().component("gui.contracts.button.ready", player));
            } else if (progress.claimed()) {
                lore.add(plugin.getMessages().component("gui.contracts.button.done-this-period", player));
            } else {
                lore.add(plugin.getMessages().component("gui.contracts.button.in-progress", player));
            }
        } else if (manager.offers(clan, type).isEmpty()) {
            available = false;
            lore.add(plugin.getMessages().component("gui.contracts.button.none-available", player));
        } else {
            lore.add(plugin.getMessages().component("gui.contracts.button.not-taken", player));
            lore.add(plugin.getMessages().component("gui.contracts.button.choose", player));
        }
        lore.add(plugin.getMessages().component("gui.contracts.button.refresh",
                Map.of("time", TimeUtil.formatDuration(manager.periodEnd(type) - System.currentTimeMillis())), player));

        ItemBuilder builder = ItemBuilder.head(available ? headFor(type) : ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component("gui.contracts.button." + period + "-name", player))
                .lore(lore);
        if (ready) builder.glow(true);
        return builder.build();
    }

    private Optional<ItemStack> buildDetail(ContractType type, Clan clan, Player player) {
        ContractManager manager = plugin.getContractManager();
        Optional<ClanQuestProgress> activeOpt = manager.active(clan.id(), type);
        if (activeOpt.isEmpty()) return Optional.empty();
        ClanQuestProgress progress = activeOpt.get();
        Optional<ClanContractDefinition> definitionOpt = manager.definition(type, progress.questId());
        if (definitionOpt.isEmpty()) return Optional.empty();
        ClanContractDefinition definition = definitionOpt.get();

        List<Component> lore = new ArrayList<>();
        lore.add(manager.displayObjective(definition, progress).getDisplayName(player, progress.progress()));
        lore.add(plugin.getMessages().component("gui.contracts.item.reward-scaled",
                Map.of("reward", String.valueOf(progress.scaledRewardXp())), player));
        boolean ready = progress.completed() && !progress.claimed();
        if (ready) {
            lore.add(plugin.getMessages().component("gui.contracts.item.claim-hint", player));
        } else if (progress.claimed()) {
            lore.add(plugin.getMessages().component("gui.contracts.info.claimed", player));
        } else {
            lore.add(plugin.getMessages().component("gui.contracts.item.expires",
                    Map.of("time", TimeUtil.formatDuration(progress.expiresAt() - System.currentTimeMillis())), player));
        }
        ItemBuilder builder = ItemBuilder.head(ready ? ItemBuilder.HEAD_COMPLETED_QUESTS : headFor(type))
                .name(plugin.getMessages().component("gui.contracts.item.name", Map.of("name", definition.displayName()), player))
                .lore(lore);
        if (ready) builder.glow(true);
        return Optional.of(builder.build());
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_BACK -> plugin.getGuiManager().openMain(player, clan);
            case SLOT_WEEKLY, SLOT_WEEKLY_DETAIL -> handleVow(player, clan, ContractType.WEEKLY);
            case SLOT_MONTHLY, SLOT_MONTHLY_DETAIL -> handleVow(player, clan, ContractType.MONTHLY);
            default -> {
            }
        }
    }

    private void handleVow(Player player, Clan clan, ContractType type) {
        ContractManager manager = plugin.getContractManager();
        manager.settleExpired(clan);
        Optional<ClanQuestProgress> active = manager.active(clan.id(), type);

        if (active.isEmpty()) {
            if (!clan.hasPermission(player.getUniqueId(), ClanPermission.CONTRACTS)) {
                plugin.getMessages().send(player, "general.no-permission");
                return;
            }
            if (manager.offers(clan, type).isEmpty()) {
                plugin.getMessages().send(player, "contract.none-available");
                return;
            }
            choiceMenu.open(player, clan, type);
            return;
        }

        ClanQuestProgress progress = active.get();
        if (!progress.completed() || progress.claimed()) {
            return; // still in progress or already paid: the lore already says everything
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.CONTRACTS)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        // Close first: a second click on the same button must not send a second request while this one runs.
        player.closeInventory();
        manager.claimAsync(clan, player.getUniqueId(), type)
                .thenRun(() -> plugin.runSync(() -> reopen(player, clan)))
                .exceptionally(error -> {
                    plugin.runSync(() -> {
                        plugin.sendOperationError(player, error);
                        reopen(player, clan);
                    });
                    return null;
                });
    }

    /** Back to the vows screen after an async action, but only if the player is still around. */
    private void reopen(Player player, Clan clan) {
        if (player.isOnline()) {
            open(player, clan);
        }
    }
}
