package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.manager.ContractManager;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.quest.ClanContractDefinition;
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
import java.util.UUID;

/**
 * "Pick one of three" screen of a weekly or monthly vow (gui_gen v2.1, 27 slots): the offered vows are the work-zone
 * content, centered in the middle row; back/close are the footer. The offer comes from
 * {@link ContractManager#offers} - the same for every member for the whole period, so closing and reopening
 * the screen never re-rolls it. The holder remembers which vow sits in which slot, and the server checks the
 * chosen id against the current offer again when the vow is taken.
 */
public final class ClanContractChoiceMenu {
    private static final int SLOT_INFO = 0;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    /** Work-zone slots by number of offers, so one or two offers stay centered. */
    private static final int[][] OFFER_SLOTS = {{}, {13}, {12, 14}, {11, 13, 15}};

    private final LoveClansPlugin plugin;

    public ClanContractChoiceMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** Remembers what was offered in which slot at the moment the screen was built. */
    public static final class Holder extends ClanMenuHolder {
        private final ContractType type;
        private final List<String> offerIds;
        private final int[] slots;

        Holder(UUID clanId, ContractType type, List<String> offerIds, int[] slots) {
            super(ClanMenuType.CONTRACT_CHOICE, clanId);
            this.type = type;
            this.offerIds = offerIds;
            this.slots = slots;
        }

        public ContractType contractType() {
            return type;
        }

        /** The offered vow id in this slot, or null when the slot holds nothing to choose. */
        String offerAt(int slot) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) return offerIds.get(i);
            }
            return null;
        }
    }

    public void open(Player player, Clan clan, ContractType type) {
        ContractManager manager = plugin.getContractManager();
        List<ClanContractDefinition> offers = manager.offers(clan, type);
        if (offers.isEmpty()) {
            plugin.getMessages().send(player, "contract.none-available");
            return;
        }
        int[] slots = OFFER_SLOTS[offers.size()];
        Holder holder = new Holder(clan.id(), type, offers.stream().map(ClanContractDefinition::id).toList(), slots);
        String period = type == ContractType.MONTHLY ? "monthly" : "weekly";
        Inventory inventory = Bukkit.createInventory(holder, 27,
                plugin.getMessages().component("gui.contracts.choice." + period + "-title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame27(inventory);

        inventory.setItem(SLOT_INFO, ItemBuilder.head(ItemBuilder.HEAD_QUEST)
                .name(plugin.getMessages().component("gui.contracts.choice.info-title", player))
                .lore(plugin.getMessages().components("gui.contracts.choice.info-lore",
                        Map.of("time", TimeUtil.formatDuration(manager.periodEnd(type) - System.currentTimeMillis())), player))
                .build());

        for (int i = 0; i < offers.size(); i++) {
            inventory.setItem(slots[i], buildOffer(manager.scale(clan, offers.get(i)), type, player));
        }

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildOffer(ContractManager.ScaledOffer offer, ContractType type, Player player) {
        List<Component> lore = new ArrayList<>();
        // The objective line is rebuilt from the scaled target: the static config description carries the
        // unscaled numbers, which would be wrong for every clan bigger than one member.
        lore.add(offer.objective().getDisplayName(player, 0));
        lore.add(plugin.getMessages().component("gui.contracts.item.reward-scaled", Map.of("reward", String.valueOf(offer.rewardXp())), player));
        int points = Math.max(0, plugin.getConfig().getInt(
                "clans.contracts." + (type == ContractType.MONTHLY ? "monthly" : "weekly") + ".reward-upgrade-points",
                type == ContractType.MONTHLY ? 3 : 1));
        if (points > 0) {
            lore.add(plugin.getMessages().component("gui.contracts.item.reward-points", Map.of("points", String.valueOf(points)), player));
        }
        lore.add(Component.empty());
        lore.add(plugin.getMessages().component("gui.contracts.item.select", player));
        return ItemBuilder.head(type == ContractType.MONTHLY ? ItemBuilder.HEAD_MONTHLY_QUESTS : ItemBuilder.HEAD_WEEKLY_QUESTS)
                .name(plugin.getMessages().component("gui.contracts.item.name", Map.of("name", offer.definition().displayName()), player))
                .lore(lore)
                .build();
    }

    public void handleInventoryClick(Player player, Clan clan, int slot, Holder holder) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openContracts(player, clan);
            return;
        }
        String contractId = holder.offerAt(slot);
        if (contractId == null) return;

        // Close first: a double click must not send two requests (the server would reject the second one,
        // but the player would see a confusing error right after a success).
        player.closeInventory();
        plugin.getContractManager().selectAsync(clan, player.getUniqueId(), holder.contractType(), contractId)
                .thenRun(() -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "contract.selected");
                    if (player.isOnline()) plugin.getGuiManager().openContracts(player, clan);
                }))
                .exceptionally(error -> {
                    plugin.runSync(() -> {
                        plugin.sendOperationError(player, error);
                        if (player.isOnline()) plugin.getGuiManager().openContracts(player, clan);
                    });
                    return null;
                });
    }
}
