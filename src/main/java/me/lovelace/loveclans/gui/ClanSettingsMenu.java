package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class ClanSettingsMenu {
    private final LoveClansPlugin plugin;

    public ClanSettingsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.SETTINGS, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder, 36,
                plugin.getMessages().component("gui.settings.title", Map.of("clan", clan.name(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        fillFrame(inventory);

        inventory.setItem(10, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTY3ZDgxM2FlN2ZmZTViZTk1MWE0ZjQxZjJhYTYxOWE1ZTM4OTRlODVlYTVkNDk4NmY4NDk0OWM2M2Q3NjcyZSJ9fX0=")
                .name(plugin.getMessages().component("gui.settings.rename.name", player))
                .lore(plugin.getMessages().component("gui.settings.rename.lore", player))
                .build());

        inventory.setItem(12, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFiYzJiY2ZiMmJkMzc1OWU2YjFlODZmYzdhNzk1ODVlMTEyN2RkMzU3ZmMyMDI4OTNmOWRlMjQxYmM5ZTUzMCJ9fX0=")
                .name(plugin.getMessages().component("gui.settings.change-tag.name", player))
                .lore(plugin.getMessages().component("gui.settings.change-tag.lore", player))
                .build());

        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
        inventory.setItem(14, ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.settings.change-banner.name", player))
                .lore(plugin.getMessages().component("gui.settings.change-banner.lore", player))
                .build());

        inventory.setItem(16, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGIyNDllODhhZmEzMGZjODM3YjgyMTczYTMwNDgzNDU4ZDRlOWEzM2M3ZWMyNWU1NTEzODdlOGU1NGEwMThhZSJ9fX0=")
                .name(plugin.getMessages().component("gui.settings.change-color.name", player))
                .lore(plugin.getMessages().component("gui.settings.change-color.lore",
                        Map.of("preview", clan.coloredTag()), player))
                .build());

        // Open/closed status
        if (clan.isOpen()) {
            inventory.setItem(19, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQ4YmI0ZTQ0MzVjMmMyMWQ3ZjYxODNiMzhhMmI3MzcyNjUzZjM1NDBiZTAyMjU5ZGQ0N2JmNTI0OTJkZTY2OSJ9fX0=")
                    .name(plugin.getMessages().component("gui.settings.status.open.name", player))
                    .lore(plugin.getMessages().component("gui.settings.status.open.lore", player))
                    .build());
        } else {
            inventory.setItem(19, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmJmNDZiZjM5ZGZjNzE4ZTdlYTMxZGI0MzQ3N2ZjNmI3ZGNhNTg4ZmUwYTc4OTFkNDgxYzVkZGE5ZTE2ZjUyMCJ9fX0=")
                    .name(plugin.getMessages().component("gui.settings.status.closed.name", player))
                    .lore(plugin.getMessages().component("gui.settings.status.closed.lore", player))
                    .build());
        }

        inventory.setItem(22, ItemBuilder.head("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVmODcyZTMxYTQzZWU4YTY1Y2FjY2Y3M2I5NDJjOTdmMmNmODJjYzdjYmRhN2M5NzUyODc0MDliYzhlMjQxNCJ9fX0=")
                .name(plugin.getMessages().component("gui.settings.roles.name", player))
                .lore(plugin.getMessages().component("gui.settings.roles.lore", player))
                .build());

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component("gui.settings.disband.name", player))
                .lore(plugin.getMessages().component("gui.settings.disband.lore", player))
                .build());

        inventory.setItem(34, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(35, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(Player player, Clan clan, int slot) {
        if (slot == 35) {
            player.closeInventory();
            return;
        }
        if (slot == 34) {
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), me.lovelace.loveclans.model.ClanPermission.SETTINGS)) {
            plugin.getMessages().send(player, "general.no-permission");
            return;
        }
        switch (slot) {
            case 10 -> {
                player.closeInventory();
                plugin.getMessages().send(player, "gui.settings.rename.prompt");
                plugin.expectChatInput(player.getUniqueId(), (newName, isCancelled) -> {
                    if (isCancelled) {
                        plugin.runSync(() -> open(player, clan));
                        return;
                    }
                    int min = plugin.getConfig().getInt("clans.name.min-length", 4);
                    int max = plugin.getConfig().getInt("clans.name.max-length", 10);
                    if (newName.trim().length() < min || newName.trim().length() > max) {
                        plugin.getMessages().send(player, "clan.invalid-name",
                                Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                        plugin.runSync(() -> open(player, clan));
                        return;
                    }
                    plugin.getGuiManager().openConfirm(player, clan,
                            plugin.getMessages().component("gui.confirm.rename.title", player), Component.empty(),
                            () -> plugin.getClanManager().renameClanAsync(clan, player.getUniqueId(), newName.trim())
                                    .thenAccept(updated -> plugin.runSync(() -> {
                                        plugin.getMessages().send(player, "gui.settings.rename.success", Map.of("name", updated.name()));
                                        open(player, updated);
                                    }))
                                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                            () -> plugin.runSync(() -> open(player, clan))
                    );
                });
            }
            case 12 -> {
                player.closeInventory();
                plugin.getMessages().send(player, "gui.settings.change-tag.prompt");
                plugin.expectChatInput(player.getUniqueId(), (newTag, isCancelled) -> {
                    if (isCancelled) {
                        plugin.runSync(() -> open(player, clan));
                        return;
                    }
                    int min = plugin.getConfig().getInt("clans.tag.min-length", 3);
                    int max = plugin.getConfig().getInt("clans.tag.max-length", 6);
                    String pattern = plugin.getConfig().getString("clans.tag.pattern", "^[A-Za-z0-9_]+$");
                    if (newTag.trim().length() < min || newTag.trim().length() > max || !newTag.trim().matches(pattern)) {
                        plugin.getMessages().send(player, "clan.invalid-tag",
                                Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                        plugin.runSync(() -> open(player, clan));
                        return;
                    }
                    plugin.getGuiManager().openConfirm(player, clan,
                            plugin.getMessages().component("gui.confirm.change-tag.title", player), Component.empty(),
                            () -> plugin.getClanManager().changeClanTagAsync(clan, player.getUniqueId(), newTag.trim())
                                    .thenAccept(updated -> plugin.runSync(() -> {
                                        plugin.getMessages().send(player, "gui.settings.change-tag.success", Map.of("tag", updated.tag()));
                                        open(player, updated);
                                    }))
                                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                            () -> plugin.runSync(() -> open(player, clan))
                    );
                });
            }
            case 14 -> {
                org.bukkit.inventory.ItemStack inHand = player.getInventory().getItemInMainHand();
                if (!inHand.getType().name().endsWith("_BANNER")) {
                    plugin.getMessages().send(player, "gui.settings.change-banner.no-banner-in-hand");
                    return;
                }
                Material bannerMat = inHand.getType();
                plugin.getClanManager().changeClanEmblemAsync(clan, player.getUniqueId(), bannerMat)
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "gui.settings.change-banner.success");
                            open(player, updated);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
            }
            case 16 -> plugin.getGuiManager().openColorPicker(player, clan);
            case 19 -> plugin.getGuiManager().openConfirm(player, clan,
                    clan.isOpen()
                            ? plugin.getMessages().component("gui.confirm.close-clan.title", player)
                            : plugin.getMessages().component("gui.confirm.open-clan.title", player),
                    Component.empty(),
                    () -> plugin.getClanManager().setClanOpenStatusAsync(clan, player.getUniqueId(), !clan.isOpen())
                            .thenAccept(updated -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, updated.isOpen()
                                        ? "gui.settings.status.open.success" : "gui.settings.status.closed.success");
                                open(player, updated);
                            }))
                            .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                    () -> plugin.runSync(() -> open(player, clan))
            );
            case 22 -> plugin.getGuiManager().openRoleSettings(player, clan);
            case 25 -> {
                boolean isLeader = clan.member(player.getUniqueId())
                        .map(m -> m.rank() == me.lovelace.loveclans.model.ClanRank.LEADER)
                        .orElse(false);
                if (!isLeader) {
                    plugin.getMessages().send(player, "general.no-permission");
                    return;
                }
                plugin.getGuiManager().openConfirm(player, clan,
                        plugin.getMessages().component("gui.confirm.disband.title", player), Component.empty(),
                        () -> {
                            player.closeInventory();
                            plugin.getClanManager().disbandClanAsync(clan, player.getUniqueId())
                                    .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.disbanded")))
                                    .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
                        },
                        () -> plugin.runSync(() -> open(player, clan))
                );
            }
            default -> {}
        }
    }

    /** Rule 8: rows 9-17 and 18-26 host content (rename/tag/banner/color/status/roles) and must
     *  stay glass-free where unused. Only the header row (0-8) and footer row (27-35) are frame. */
    private void fillFrame(Inventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
        for (int slot = 27; slot <= 35; slot++) {
            inventory.setItem(slot, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
    }
}