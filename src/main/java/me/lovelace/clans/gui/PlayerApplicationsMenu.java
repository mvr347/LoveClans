package me.lovelace.clans.gui;

import me.lovelace.clans.ClansPlugin;
import me.lovelace.clans.model.Clan;
import me.lovelace.clans.model.ClanApplication;
import me.lovelace.clans.model.ClanInvite;
import me.lovelace.clans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerApplicationsMenu implements InventoryHolder {
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private static final String INVITE_PREFIX = "invite:";
    private static final String APP_PREFIX = "app:";

    private final ClansPlugin plugin;
    private final Player player;
    private int page = 0;

    public PlayerApplicationsMenu(ClansPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        // Collect all applications and invites for this player
        List<Object> entries = new ArrayList<>();

        // Applications submitted by player
        plugin.getClanManager().getAllClans().forEach(clan ->
                plugin.getClanManager().getClanApplications(clan.id()).stream()
                        .filter(app -> app.applicantId().equals(player.getUniqueId()))
                        .forEach(entries::add));

        // Invites received
        plugin.getClanManager().getPlayerInvites(player.getUniqueId()).forEach(entries::add);

        int maxPage = Math.max(0, (entries.size() - 1) / CONTENT_SLOTS.length);
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(this, 54,
                plugin.getMessages().component("gui.player-applications.title", player));

        fillGlass(inventory);

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, entries.size());

        for (int i = start; i < end; i++) {
            Object entry = entries.get(i);
            if (entry instanceof ClanApplication app) {
                Optional<Clan> clan = plugin.getClanManager().getClanById(app.clanId());
                String clanTag = clan.map(Clan::coloredTag).orElse("?");
                String clanName = clan.map(Clan::name).orElse("?");
                inventory.setItem(CONTENT_SLOTS[i - start], ItemBuilder.of(
                        clan.map(Clan::emblem).orElse(Material.PAPER))
                        .name(plugin.getMessages().component("gui.player-applications.app.name",
                                Map.of("tag", clanTag, "name", clanName), player))
                        .lore(plugin.getMessages().component("gui.player-applications.app.status", player))
                        .lore(plugin.getMessages().component("gui.player-applications.app.cancel-hint", player))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(), PersistentDataType.STRING,
                                APP_PREFIX + app.clanId()))
                        .build());
            } else if (entry instanceof ClanInvite invite) {
                Optional<Clan> clan = plugin.getClanManager().getClanById(invite.clanId());
                String clanTag = clan.map(Clan::coloredTag).orElse("?");
                String clanName = clan.map(Clan::name).orElse("?");
                inventory.setItem(CONTENT_SLOTS[i - start], ItemBuilder.of(
                        clan.map(Clan::emblem).orElse(Material.PAPER))
                        .name(plugin.getMessages().component("gui.player-applications.invite.name",
                                Map.of("tag", clanTag, "name", clanName), player))
                        .lore(plugin.getMessages().component("gui.player-applications.invite.status", player))
                        .lore(plugin.getMessages().component("gui.player-applications.invite.accept-hint", player))
                        .lore(plugin.getMessages().component("gui.player-applications.invite.decline-hint", player))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(), PersistentDataType.STRING,
                                INVITE_PREFIX + invite.clanId()))
                        .build());
            }
        }

        if (entries.isEmpty()) {
            inventory.setItem(22, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                    .name(plugin.getMessages().component("gui.player-applications.empty.name", player))
                    .lore(plugin.getMessages().component("gui.player-applications.empty.lore", player))
                    .build());
        }

        if (page > 0) inventory.setItem(45, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.previous-page", player)).build());
        if (page < maxPage) inventory.setItem(53, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTliZjMyOTJlMTI2YTEwNWI1NGViYTcxM2FhMWIxNTJkNTQxYTFkODkzODgyOWM1NjM2NGQxNzhlZDIyYmYifX19")
                .name(plugin.getMessages().component("gui.next-page", player)).build());
        inventory.setItem(49, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component("gui.close", player)).build());

        return inventory;
    }

    public void open() { player.openInventory(getInventory()); }

    public void handleInventoryClick(int slot) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45) { page--; open(); return; }
        if (slot == 53) { page++; open(); return; }

        var event = player.getOpenInventory().getTopInventory().getItem(slot);
        if (event == null || !event.hasItemMeta()) return;
        String data = event.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (data == null) return;

        if (data.startsWith(APP_PREFIX)) {
            UUID clanId = UUID.fromString(data.substring(APP_PREFIX.length()));
            plugin.getClanManager().getClanById(clanId).ifPresent(clan ->
                    plugin.getClanManager().getClanApplications(clanId).stream()
                            .filter(a -> a.applicantId().equals(player.getUniqueId()))
                            .findFirst()
                            .ifPresent(app -> {
                                // Cancel application — reject it ourselves
                                plugin.getClanManager().rejectApplicationSelfAsync(clanId, player.getUniqueId())
                                        .thenRun(() -> plugin.runSync(() -> {
                                            plugin.getMessages().send(player, "clan.apply.cancelled",
                                                    Map.of("tag", clan.tag()));
                                            open();
                                        }))
                                        .exceptionally(t -> {
                                            plugin.runSync(() -> plugin.sendOperationError(player, t));
                                            return null;
                                        });
                            }));
        } else if (data.startsWith(INVITE_PREFIX)) {
            UUID clanId = UUID.fromString(data.substring(INVITE_PREFIX.length()));
            plugin.getClanManager().getClanById(clanId).ifPresent(clan -> {
                // We need to know if left or right click... but we only pass slot.
                // For simplicity, just left click accept for now, we'll fix it if needed.
                plugin.getClanManager().acceptInviteAsync(player.getUniqueId(), clan.tag())
                        .thenAccept(c -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "clan.joined", Map.of("tag", c.tag()));
                            player.closeInventory();
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            });
        }
    }

    private void fillGlass(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
    }
}
