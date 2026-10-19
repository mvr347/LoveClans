package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ContractManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
import me.lovelace.loveclans.model.quest.ClanQuestProgress;
import me.lovelace.loveclans.model.quest.ContractType;
import me.lovelace.loveclans.util.ItemBuilder;
import me.lovelace.loveclans.util.LoreBar;
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
 * Clan vows screen (gui_gen v2.1, 27 slots): one card per period - weekly and monthly - in the work zone. A card
 * shows the vow the clan is currently running; without one it opens the pick-one-of-three screen, and a
 * finished vow is paid out by a click. The screen itself never changes anything: every decision goes through {@link ContractManager}.
 */
public final class ClanContractsMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_WEEKLY = 12;
    private static final int SLOT_MONTHLY = 14;
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

        inventory.setItem(SLOT_WEEKLY, buildCard(ContractType.WEEKLY, clan, player));
        inventory.setItem(SLOT_MONTHLY, buildCard(ContractType.MONTHLY, clan, player));

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

    /** The running vow when there is one, otherwise the "take a vow" button of that period. */
    private ItemStack buildCard(ContractType type, Clan clan, Player player) {
        return buildDetail(type, clan, player).orElseGet(() -> buildButton(type, clan, player));
    }

    /** "Take a vow" button of a period that has no running vow; greyed out with a reason when nothing can be offered. */
    private ItemStack buildButton(ContractType type, Clan clan, Player player) {
        ContractManager manager = plugin.getContractManager();
        var messages = plugin.getMessages();
        String nameKey = "gui.contracts.button." + periodKey(type) + "-name";
        boolean available = !manager.offers(clan, type).isEmpty();
        Map<String, String> values = Map.of("time", TimeUtil.formatDuration(manager.periodEnd(type) - System.currentTimeMillis()));
        LoreBlocks.Hint choose = LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.contracts.button.choose-action");

        ItemBuilder builder = ItemBuilder.head(available ? headFor(type) : ItemBuilder.HEAD_INACTIVE)
                .name(available ? messages.component("gui.contracts.button.name-not-taken", Map.of("name", messages.raw(nameKey)), player)
                        : LoreBlocks.disabledName(messages, player, nameKey))
                .lore(messages.components("gui.contracts.button.lore", values, player));
        if (available) {
            builder.lore(LoreBlocks.clicks(messages, player, List.of(choose)));
        } else {
            builder.lore(LoreBlocks.blockedReason(messages, player, "gui.contracts.button.none-available", Map.of(), choose));
        }
        return builder.build();
    }

    /** The running vow: name with its state, goal with a progress bar, reward, term and the claim click when it is done. */
    private Optional<ItemStack> buildDetail(ContractType type, Clan clan, Player player) {
        ContractManager manager = plugin.getContractManager();
        var messages = plugin.getMessages();
        Optional<ClanQuestProgress> activeOpt = manager.active(clan.id(), type);
        if (activeOpt.isEmpty()) return Optional.empty();
        ClanQuestProgress progress = activeOpt.get();
        Optional<ClanContractDefinition> definitionOpt = manager.definition(type, progress.questId());
        // A vow whose definition left the config still shows up (and can be claimed), just without its objective text.
        String questName = definitionOpt.map(ClanContractDefinition::displayName).orElse(progress.questId());

        boolean ready = progress.completed() && !progress.claimed();
        boolean claimed = progress.claimed();
        List<Component> lore = new ArrayList<>();
        lore.add(messages.component("gui.contracts.item.subtitle-" + periodKey(type), player));
        lore.add(Component.empty());
        lore.add(messages.component("gui.contracts.item.goal-title", player));
        definitionOpt.ifPresent(definition -> lore.add(Component.text("  ")
                .append(manager.displayObjective(definition, progress).getDisplayName(player, progress.progress()))));
        lore.add(messages.component("gui.contracts.item.progress-bar", Map.of(
                "bar", LoreBar.bar(progress.progress(), Math.max(1, progress.scaledTarget()))), player));
        lore.add(Component.empty());
        lore.add(messages.component("gui.contracts.item.reward-title", player));
        lore.add(messages.component("gui.contracts.item.reward-scaled",
                Map.of("reward", String.valueOf(progress.scaledRewardXp())), player));
        lore.add(Component.empty());
        lore.add(messages.component("gui.contracts.item.time-title", player));
        if (!claimed && !ready) {
            lore.add(messages.component("gui.contracts.item.expires",
                    Map.of("time", TimeUtil.formatDuration(progress.expiresAt() - System.currentTimeMillis())), player));
        }
        lore.add(messages.component("gui.contracts.item.refresh",
                Map.of("time", TimeUtil.formatDuration(manager.periodEnd(type) - System.currentTimeMillis())), player));
        if (ready) {
            lore.add(Component.empty());
            lore.add(messages.component("gui.contracts.item.ready", player));
            lore.addAll(LoreBlocks.clicks(messages, player, List.of(
                    LoreBlocks.hint(messages, LoreBlocks.Click.LEFT, "gui.contracts.item.claim-action"))));
        } else if (claimed) {
            lore.add(Component.empty());
            lore.add(messages.component("gui.contracts.item.claimed", player));
        }
        String nameKey = ready ? "gui.contracts.item.name-ready" : claimed ? "gui.contracts.item.name-claimed" : "gui.contracts.item.name-progress";
        ItemBuilder builder = ItemBuilder.head(ready ? ItemBuilder.HEAD_COMPLETED_QUESTS : headFor(type))
                .name(messages.component(nameKey, Map.of("name", questName), player))
                .lore(lore);
        if (ready) builder.glow(true);
        return Optional.of(builder.build());
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_BACK -> {
                if (plugin.getGuiManager().inGuildmasterSession(player)) {
                    plugin.getGuiManager().openGuildmaster(player, clan);
                } else {
                    plugin.getGuiManager().openMain(player, clan);
                }
            }
            case SLOT_WEEKLY -> handleVow(player, clan, ContractType.WEEKLY);
            case SLOT_MONTHLY -> handleVow(player, clan, ContractType.MONTHLY);
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
