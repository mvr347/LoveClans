package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanInvite;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerApplicationsMenu implements InventoryHolder {
    private static final String APPLICATION_TAG = "app:";
    private static final String INVITE_TAG = "invite:";
    // gui_gen 54-slot working zone is 18-44 only (three rows of 7) — row 1 (9-17) is always frame.
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LoveClansPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public PlayerApplicationsMenu(LoveClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        List<ClanApplication> applications = plugin.getClanManager().getAllClans().stream()
                .flatMap(clan -> plugin.getClanManager().getClanApplications(clan.id()).stream())
                .filter(app -> app.applicantId().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(ClanApplication::appliedAt).reversed())
                .toList();
        List<ClanInvite> invites = plugin.getClanManager().getPlayerInvites(player.getUniqueId());

        List<Object> combined = new ArrayList<>();
        combined.addAll(applications);
        combined.addAll(invites);

        this.inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.player-applications.title", player));

        GuiFrames.fillFrame54(inventory);

        int end = Math.min(combined.size(), CONTENT_SLOTS.length);
        for (int index = 0; index < end; index++) {
            Object entry = combined.get(index);
            int targetSlot = CONTENT_SLOTS[index];

            if (entry instanceof ClanApplication application) {
                plugin.getClanManager().getClanById(application.clanId()).ifPresent(targetClan -> {
                    Material emblemMaterial = targetClan.emblem().name().endsWith("_BANNER") ? targetClan.emblem() : Material.WHITE_BANNER;
                    ItemBuilder builder = ItemBuilder.of(emblemMaterial)
                            .name(plugin.getMessages().component("gui.player-applications.application-item.name",
                                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "name", targetClan.name()), player))
                            .lore(plugin.getMessages().component("gui.player-applications.application-item.lore", player));
                    builder.mutate(meta -> meta.getPersistentDataContainer()
                            .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING,
                                    APPLICATION_TAG + application.clanId()));
                    inventory.setItem(targetSlot, builder.build());
                });
            } else if (entry instanceof ClanInvite invite) {
                plugin.getClanManager().getClanById(invite.clanId()).ifPresent(targetClan -> {
                    ItemBuilder builder = ItemBuilder.head(ItemBuilder.HEAD_INVITE)
                            .name(plugin.getMessages().component("gui.player-applications.invite-item.name",
                                    Map.of("tag", targetClan.tag(), "color", targetClan.tagColor(), "name", targetClan.name()), player))
                            .lore(plugin.getMessages().component("gui.player-applications.invite-item.left-click-accept", player))
                            .lore(plugin.getMessages().component("gui.player-applications.invite-item.right-click-decline", player));
                    builder.mutate(meta -> meta.getPersistentDataContainer()
                            .set(plugin.getGuiManager().memberKey(), PersistentDataType.STRING,
                                    INVITE_TAG + invite.clanId()));
                    inventory.setItem(targetSlot, builder.build());
                });
            }
        }

        if (combined.isEmpty()) {
            inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.player-applications.empty.name", player))
                    .lore(plugin.getMessages().component("gui.player-applications.empty.lore", player))
                    .build());
        }

        inventory.setItem(53, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String rawId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rawId == null) return;

        if (rawId.startsWith(APPLICATION_TAG)) {
            UUID clanId = parseId(rawId.substring(APPLICATION_TAG.length()));
            if (clanId == null) return;
            plugin.getClanManager().rejectApplicationSelfAsync(clanId, player.getUniqueId())
                    .thenRun(() -> plugin.runSync(() -> {
                        plugin.getMessages().send(player, "gui.player-applications.cancelled");
                        open();
                    }))
                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            return;
        }

        if (rawId.startsWith(INVITE_TAG)) {
            UUID clanId = parseId(rawId.substring(INVITE_TAG.length()));
            if (clanId == null) return;
            plugin.getClanManager().getClanById(clanId).ifPresent(targetClan -> {
                if (event.isLeftClick()) {
                    plugin.getClanManager().acceptInviteAsync(player.getUniqueId(), targetClan.tag())
                            .thenAccept(joinedClan -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, "clan.joined", Map.of("tag", joinedClan.tag(), "color", joinedClan.tagColor()));
                                player.closeInventory();
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                } else if (event.isRightClick()) {
                    plugin.getClanManager().removeInvite(player.getUniqueId(), clanId);
                    plugin.getMessages().send(player, "gui.player-applications.invite-declined", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
                    open();
                }
            });
        }
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
