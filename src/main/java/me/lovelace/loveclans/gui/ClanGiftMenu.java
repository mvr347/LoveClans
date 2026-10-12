package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Map;

public final class ClanGiftMenu implements InventoryHolder {
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;
    private static final int[] PRESET_SLOTS = {11, 12, 13, 14};
    private static final int SLOT_CUSTOM = 15;

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Clan sourceClan;
    private final Clan targetClan;
    private Inventory inventory;

    public ClanGiftMenu(LoveClansPlugin plugin, Player player, Clan sourceClan, Clan targetClan) {
        this.plugin = plugin;
        this.player = player;
        this.sourceClan = sourceClan;
        this.targetClan = targetClan;
    }

    private List<Long> presets() {
        List<Long> presets = plugin.getConfig().getLongList("clans.gifts.presets");
        return presets.isEmpty() ? List.of(1000L, 5000L, 10000L, 50000L) : presets;
    }

    private long maxAmount() {
        return plugin.getConfig().getLong("clans.gifts.max-amount", 100_000L);
    }

    private long cooldownSeconds() {
        return plugin.getClanGiftManager().getCooldownRemaining(sourceClan.id(), targetClan.id());
    }

    /** Same limits the server applies, as a menu-side check: a deny key from {@code deny.gift.*} or {@code null}. */
    private String giftDeny(long amount) {
        return me.lovelace.loveclans.manager.GiftRules.deny(plugin.getConfig().getBoolean("clans.gifts.enabled", true),
                amount, maxAmount(), cooldownSeconds(), sourceClan.chestMoney());
    }

    private Map<String, String> denyPlaceholders() {
        return Map.of("max", String.valueOf(maxAmount()),
                "time", me.lovelace.loveclans.util.TimeUtil.formatDuration(cooldownSeconds() * 1000L));
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.diplomacy.gift.title",
                        Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);

        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_CHEST_MONEY)
                .name(plugin.getMessages().component("gui.diplomacy.gift.name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.gift.lore", player))
                .lore(plugin.getMessages().component("gui.chest.money.balance.lore", Map.of("amount", String.valueOf(sourceClan.chestMoney())), player))
                .lore(plugin.getMessages().components("gui.diplomacy.gift.limits", Map.of(
                        "max", String.valueOf(maxAmount()),
                        "cooldown", me.lovelace.loveclans.util.TimeUtil.formatDuration(
                                plugin.getConfig().getLong("clans.gifts.cooldown-seconds", 3600L) * 1000L)), player))
                .build());

        List<Long> presets = presets();

        for (int i = 0; i < PRESET_SLOTS.length && i < presets.size(); i++) {
            long amt = presets.get(i);
            String deny = giftDeny(amt);
            ItemBuilder b = ItemBuilder.head(deny == null ? ItemBuilder.HEAD_CHEST_MONEY : ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component("gui.diplomacy.gift.preset-name", Map.of("amount", String.valueOf(amt)), player))
                    .lore(plugin.getMessages().component("gui.diplomacy.gift.preset-lore", Map.of("amount", String.valueOf(amt)), player));
            if (deny != null) {
                b.lore(Component.empty());
                b.lore(plugin.getMessages().denyLore(deny, denyPlaceholders(), player));
            }
            inventory.setItem(PRESET_SLOTS[i], b.build());
        }

        inventory.setItem(SLOT_CUSTOM, ItemBuilder.head(ItemBuilder.HEAD_EXPERIENCE)
                .name(plugin.getMessages().component("gui.diplomacy.gift.custom-name", player))
                .lore(plugin.getMessages().component("gui.diplomacy.gift.custom-lore", player))
                .build());

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player clicker, int slot) {
        if (slot == SLOT_CLOSE) {
            clicker.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            plugin.getGuiManager().openDiplomacy(clicker, sourceClan, targetClan);
            return;
        }

        List<Long> presets = presets();

        for (int i = 0; i < PRESET_SLOTS.length && i < presets.size(); i++) {
            if (slot == PRESET_SLOTS[i]) {
                long amount = presets.get(i);
                String deny = giftDeny(amount);
                if (deny != null) {
                    plugin.getMessages().sendDeny(clicker, deny, denyPlaceholders());
                    return;
                }
                promptConfirm(amount);
                return;
            }
        }

        if (slot == SLOT_CUSTOM) {
            clicker.closeInventory();
            plugin.getMessages().send(clicker, "gui.diplomacy.gift.prompt-custom");
            plugin.expectChatInput(clicker.getUniqueId(), (input, isCancelled) -> {
                if (Boolean.TRUE.equals(isCancelled) || input == null || input.equalsIgnoreCase("cancel")) {
                    plugin.getMessages().send(clicker, "general.chat-input-cancelled");
                    return;
                }
                try {
                    long amount = Long.parseLong(input.trim());
                    if (amount <= 0) {
                        plugin.getMessages().send(clicker, "general.invalid-number");
                        return;
                    }
                    plugin.runSync(() -> {
                        String deny = giftDeny(amount);
                        if (deny != null) {
                            plugin.getMessages().sendDeny(clicker, deny, denyPlaceholders());
                            return;
                        }
                        promptConfirm(amount);
                    });
                } catch (NumberFormatException e) {
                    plugin.getMessages().send(clicker, "general.invalid-number");
                }
            });
        }
    }

    private void promptConfirm(long amount) {
        plugin.getGuiManager().openConfirm(player, sourceClan,
                plugin.getMessages().component("gui.confirm.gift.title",
                        Map.of("amount", String.valueOf(amount), "tag", targetClan.tag(), "color", targetClan.tagColor()), player),
                Component.empty(),
                () -> plugin.getClanGiftManager().sendGiftAsync(sourceClan, targetClan, amount, player.getUniqueId())
                        .thenRun(() -> plugin.runSync(() -> plugin.getGuiManager().openDiplomacy(player, sourceClan, targetClan)))
                        .exceptionally(ex -> {
                            plugin.runSync(() -> plugin.sendOperationError(player, ex));
                            return null;
                        }),
                () -> plugin.runSync(this::open)
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
