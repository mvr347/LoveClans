package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Клановый спавн и клановая территория (gui_gen v2.1, 27 слотов): две кнопки в рабочей зоне -
 * «Перенести территорию» и «Свернуть территорию». Слот 0 - стекло. Знамя здесь не выдаётся: новое знамя
 * клан получает при основании, а утерянный баннер восстанавливается кликом по кнопке в главном меню.
 */
public class ClanCapitalManagementMenu implements InventoryHolder {
    static final int SLOT_RELOCATE = 12;
    static final int SLOT_DISBAND = 14;
    private static final int SLOT_BACK = 25;
    private static final int SLOT_CLOSE = 26;

    private final LoveClansPlugin plugin;
    private final Clan clan;
    private final Player player;
    private Inventory inventory;

    public ClanCapitalManagementMenu(LoveClansPlugin plugin, Clan clan, Player player) {
        this.plugin = plugin;
        this.clan = clan;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().component("gui.capital.title",
                Map.of("tag", clan.tag(), "color", clan.tagColor()), player));

        GuiFrames.fillFrame27(inventory);
        inventory.setItem(0, GuiFrames.glassPane());

        inventory.setItem(SLOT_RELOCATE, actionItem(ItemBuilder.HEAD_RELOCATE_TERRITORY,
                "gui.capital.relocate-territory.name", "gui.capital.relocate-territory.lore"));
        inventory.setItem(SLOT_DISBAND, actionItem(ItemBuilder.HEAD_COLLAPSE,
                "gui.capital.disband.name", "gui.capital.disband.lore"));

        inventory.setItem(SLOT_BACK, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    /** Active button, or an inactive one with the reason: no territory / no right / war. */
    private ItemStack actionItem(String head, String nameKey, String loreKey) {
        String blockedKey = blockedReasonKey();
        if (blockedKey != null) {
            return ItemBuilder.head(ItemBuilder.HEAD_INACTIVE)
                    .name(plugin.getMessages().component(nameKey, player))
                    .lore(plugin.getMessages().component(blockedKey, player))
                    .build();
        }
        return ItemBuilder.head(head)
                .name(plugin.getMessages().component(nameKey, player))
                .lore(plugin.getMessages().component(loreKey, player))
                .build();
    }

    private boolean canManage() {
        return clan.member(player.getUniqueId())
                .map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN).orElse(false);
    }

    /** Lang key explaining why the buttons are unavailable, or null when they work. */
    private String blockedReasonKey() {
        if (!clan.hasCapital()) return "gui.capital.no-house-lore";
        if (!canManage()) return "gui.capital.no-permission-lore";
        if (plugin.getClanManager().inAnyConflict(clan.id())) return "gui.capital.war-blocked";
        return null;
    }

    public void handleInventoryClick(Player clicker, int slot) {
        switch (slot) {
            case SLOT_BACK -> plugin.getGuiManager().openMain(clicker, clan);
            case SLOT_CLOSE -> clicker.closeInventory();
            case SLOT_RELOCATE -> {
                if (!allowed(clicker)) return;
                clicker.closeInventory();
                plugin.getMessages().sendChatConfirmPrompt(clicker, "gui.capital.relocate-territory.confirm-prompt", Map.of(),
                        () -> plugin.getClanManager().relocateCapitalTerritoryAsync(clan, clicker.getUniqueId())
                                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(clicker, "gui.capital.relocate-territory.action")))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.getMessages().send(clicker, "general.chat-input-cancelled"));
            }
            case SLOT_DISBAND -> {
                if (!allowed(clicker)) return;
                plugin.getGuiManager().openConfirm(clicker, clan,
                        plugin.getMessages().component("gui.confirm.disband-capital.title", clicker), Component.empty(),
                        () -> plugin.getClanManager().relocateCapitalTerritoryAsync(clan, clicker.getUniqueId())
                                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(clicker, "gui.capital.disband.action")))
                                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(clicker, t)); return null; }),
                        () -> plugin.runSync(this::open)
                );
            }
            default -> { }
        }
    }

    /** State may have changed since the screen was built, so the click re-checks what the button promised. */
    private boolean allowed(Player clicker) {
        if (!clan.hasCapital()) {
            plugin.getMessages().send(clicker, "gui.capital.no-house");
            return false;
        }
        if (!canManage()) {
            plugin.getMessages().send(clicker, "gui.capital.no-permission");
            return false;
        }
        if (plugin.getClanManager().inAnyConflict(clan.id())) {
            plugin.getMessages().send(clicker, "gui.capital.war-blocked");
            return false;
        }
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
