package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.service.GuiSounds;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.textures.HeadTextures;
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
        inventory.setItem(0, ItemBuilder.head(ItemBuilder.HEAD_MAIN_SETTINGS)
                .name(plugin.getMessages().component("gui.settings.title", Map.of("clan", clan.name(), "color", clan.tagColor()), player))
                .build());

        inventory.setItem(10, ItemBuilder.head(HeadTextures.HEAD_NAME)
                .name(plugin.getMessages().component("gui.settings.rename.name", player))
                .lore(plugin.getMessages().components("gui.settings.rename.lore", Map.of("current", clan.name(),
                        "min", String.valueOf(plugin.getConfig().getInt("clans.name.min-length", 4)),
                        "max", String.valueOf(plugin.getConfig().getInt("clans.name.max-length", 10))), player))
                .build());

        inventory.setItem(12, ItemBuilder.head(HeadTextures.HEAD_TAG)
                .name(plugin.getMessages().component("gui.settings.change-tag.name", player))
                .lore(plugin.getMessages().components("gui.settings.change-tag.lore", Map.of("current", clan.tag(),
                        "min", String.valueOf(plugin.getConfig().getInt("clans.tag.min-length", 3)),
                        "max", String.valueOf(plugin.getConfig().getInt("clans.tag.max-length", 6))), player))
                .build());

        Material emblemMaterial = clan.emblem().name().endsWith("_BANNER") ? clan.emblem() : Material.WHITE_BANNER;
        inventory.setItem(14, ItemBuilder.of(emblemMaterial)
                .name(plugin.getMessages().component("gui.settings.change-banner.name", player))
                .lore(plugin.getMessages().components("gui.settings.change-banner.lore", player))
                .build());

        inventory.setItem(16, ItemBuilder.head(HeadTextures.HEAD_COLOR)
                .name(plugin.getMessages().component("gui.settings.change-color.name", player))
                .lore(plugin.getMessages().components("gui.settings.change-color.lore",
                        Map.of("preview", clan.coloredTag()), player))
                .build());

        // Open/closed status
        if (clan.isOpen()) {
            inventory.setItem(19, ItemBuilder.head(HeadTextures.HEAD_OPEN)
                    .name(plugin.getMessages().component("gui.settings.status.open.name", player))
                    .lore(plugin.getMessages().components("gui.settings.status.open.lore", player))
                    .build());
        } else {
            inventory.setItem(19, ItemBuilder.head(HeadTextures.HEAD_CLOSED)
                    .name(plugin.getMessages().component("gui.settings.status.closed.name", player))
                    .lore(plugin.getMessages().components("gui.settings.status.closed.lore", player))
                    .build());
        }

        inventory.setItem(22, ItemBuilder.head(HeadTextures.HEAD_ROLES)
                .name(plugin.getMessages().component("gui.settings.roles.name", player))
                .lore(plugin.getMessages().components("gui.settings.roles.lore", player))
                .build());

        inventory.setItem(25, ItemBuilder.head(ItemBuilder.HEAD_BARRIER)
                .name(plugin.getMessages().component("gui.settings.disband.name", player))
                .lore(plugin.getMessages().components("gui.settings.disband.lore", player))
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
            case 10 -> askRename(player, clan);
            case 12 -> askTag(player, clan);
            case 14 -> {
                org.bukkit.inventory.ItemStack inHand = player.getInventory().getItemInMainHand();
                if (!inHand.getType().name().endsWith("_BANNER")) {
                    plugin.getMessages().sendDeny(player, "gui.settings.change-banner.no-banner-in-hand", Map.of());
                    return;
                }
                Material bannerMat = inHand.getType();
                plugin.getClanManager().changeClanEmblemAsync(clan, player.getUniqueId(), bannerMat)
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
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
                    plugin.getGuiManager().confirmEffects(player, clan.isOpen() ? "close-clan" : "open-clan", Map.of()),
                    () -> plugin.getClanManager().setClanOpenStatusAsync(clan, player.getUniqueId(), !clan.isOpen())
                            .thenAccept(updated -> plugin.runSync(() -> {
                                plugin.getSoundService().playGui(player, GuiSounds.Kind.TOGGLE);
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
                    plugin.getMessages().sendDeny(player, "general.no-permission", Map.of());
                    return;
                }
                plugin.getGuiManager().openConfirm(player, clan,
                        plugin.getMessages().component("gui.confirm.disband.title", Map.of("tag", clan.tag(), "color", clan.tagColor()), player),
                        plugin.getGuiManager().confirmEffects(player, "disband", Map.of("tag", clan.tag(), "color", clan.tagColor())),
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

    /** The new name comes from a dialog; the chat prompt is the fallback. Either way it ends at the same confirm screen. */
    private void askRename(Player player, Clan clan) {
        boolean shown = plugin.getClanDialogs().askName(player, clan,
                newName -> confirmRename(player, clan, newName),
                () -> open(player, clan));
        if (shown) return;
        player.closeInventory();
        plugin.getMessages().send(player, "gui.settings.rename.prompt");
        plugin.expectChatInput(player.getUniqueId(), (typed, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(() -> open(player, clan));
                return;
            }
            String newName = me.lovelace.loveclans.util.ClanNameRules.clean(typed);
            var problem = plugin.inputValidator().firstProblem(
                    me.lovelace.loveclans.util.ClanInputValidator.Field.NAME, newName, t -> false);
            if (problem.isPresent()) {
                plugin.getClanDialogs().sendProblem(player, problem.get());
                plugin.runSync(() -> open(player, clan));
                return;
            }
            confirmRename(player, clan, newName);
        });
    }

    private void confirmRename(Player player, Clan clan, String newName) {
        plugin.getGuiManager().openConfirm(player, clan,
                plugin.getMessages().component("gui.confirm.rename.title", Map.of("name", newName), player),
                plugin.getGuiManager().confirmEffects(player, "rename", Map.of("name", newName)),
                () -> plugin.getClanManager().renameClanAsync(clan, player.getUniqueId(), newName)
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                            plugin.getMessages().send(player, "gui.settings.rename.success", Map.of("name", updated.name()));
                            open(player, updated);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, clan))
        );
    }

    private void askTag(Player player, Clan clan) {
        boolean shown = plugin.getClanDialogs().askTag(player, clan,
                newTag -> confirmTag(player, clan, newTag),
                () -> open(player, clan));
        if (shown) return;
        player.closeInventory();
        plugin.getMessages().send(player, "gui.settings.change-tag.prompt");
        plugin.expectChatInput(player.getUniqueId(), (typed, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(() -> open(player, clan));
                return;
            }
            String newTag = me.lovelace.loveclans.util.ClanNameRules.clean(typed);
            var problem = plugin.inputValidator().firstProblem(
                    me.lovelace.loveclans.util.ClanInputValidator.Field.TAG, newTag,
                    tag -> plugin.getClanManager().getClanByTag(tag).filter(other -> !other.id().equals(clan.id())).isPresent());
            if (problem.isPresent()) {
                plugin.getClanDialogs().sendProblem(player, problem.get());
                plugin.runSync(() -> open(player, clan));
                return;
            }
            confirmTag(player, clan, newTag);
        });
    }

    private void confirmTag(Player player, Clan clan, String newTag) {
        plugin.getGuiManager().openConfirm(player, clan,
                plugin.getMessages().component("gui.confirm.change-tag.title", Map.of("tag", newTag), player),
                plugin.getGuiManager().confirmEffects(player, "change-tag", Map.of("tag", newTag)),
                () -> plugin.getClanManager().changeClanTagAsync(clan, player.getUniqueId(), newTag)
                        .thenAccept(updated -> plugin.runSync(() -> {
                            plugin.getSoundService().playGui(player, GuiSounds.Kind.SUCCESS);
                            plugin.getMessages().send(player, "gui.settings.change-tag.success", Map.of("tag", updated.tag()));
                            open(player, updated);
                        }))
                        .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; }),
                () -> plugin.runSync(() -> open(player, clan))
        );
    }
}
