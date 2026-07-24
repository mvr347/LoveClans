package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanApplication;
import me.lovelace.loveclans.model.ClanInvite;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanApplicationsMenu {
    private static final String INVITE_TAG = "invite:";

    private final LoveClansPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private final Map<UUID, Integer> pageByPlayer = new ConcurrentHashMap<>();

    public ClanApplicationsMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan clan) {
        open(player, clan, pageByPlayer.getOrDefault(player.getUniqueId(), 0));
    }

    private void open(Player player, Clan clan, int requestedPage) {
        List<ClanApplication> applications = plugin.getClanManager().getClanApplications(clan.id()).stream()
                .sorted(Comparator.comparingLong(ClanApplication::appliedAt).reversed())
                .toList();
        List<ClanInvite> invites = plugin.getClanManager().getClanInvites(clan.id());

        List<Object> combined = new ArrayList<>();
        combined.addAll(applications);
        combined.addAll(invites);

        int totalItems = combined.size();
        int invSize = computeSize(totalItems);
        int[] contentSlots = computeContentSlots(invSize);
        int perPage = contentSlots.length;
        int maxPage = perPage == 0 ? 0 : Math.max(0, (totalItems - 1) / perPage);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        pageByPlayer.put(player.getUniqueId(), page);

        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.APPLICATIONS, clan.id());
        Inventory inventory = Bukkit.createInventory(
                holder,
                invSize,
                plugin.getMessages().component("gui.applications.title",
                        Map.of("clan", clan.name(), "color", clan.tagColor()), player));
        holder.setInventory(inventory);

        fillGlass(inventory);

        int start = page * perPage;
        int end = Math.min(start + perPage, totalItems);
        for (int index = start; index < end; index++) {
            Object entry = combined.get(index);
            int targetSlot = contentSlots[index - start];
            if (entry instanceof ClanApplication application) {
                OfflinePlayer applicant = Bukkit.getOfflinePlayer(application.applicantId());
                String applicantName = applicant.getName() == null ? applicant.getUniqueId().toString() : applicant.getName();

                inventory.setItem(targetSlot, ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(plugin.getMessages().component("gui.applications.applicant-item.name",
                                Map.of("player", applicantName), player))
                        .lore(plugin.getMessages().component("gui.applications.applicant-item.applied-at",
                                Map.of("date", dateFormat.format(new Date(application.appliedAt()))), player))
                        .lore(plugin.getMessages().component("gui.applications.applicant-item.left-click-accept", player))
                        .lore(plugin.getMessages().component("gui.applications.applicant-item.right-click-reject", player))
                        .mutate(meta -> {
                            if (meta instanceof SkullMeta skullMeta) {
                                skullMeta.setOwningPlayer(applicant);
                            }
                            meta.getPersistentDataContainer().set(
                                    plugin.getGuiManager().memberKey(),
                                    PersistentDataType.STRING,
                                    application.applicantId().toString()
                            );
                        })
                        .build());
            } else if (entry instanceof ClanInvite invite) {
                OfflinePlayer invited = Bukkit.getOfflinePlayer(invite.invitedPlayer());
                String invitedName = invited.getName() == null ? invite.invitedPlayer().toString() : invited.getName();

                inventory.setItem(targetSlot, ItemBuilder.of(Material.PAPER)
                        .name(plugin.getMessages().component("gui.applications.invite-item.name",
                                Map.of("player", invitedName), player))
                        .lore(plugin.getMessages().component("gui.applications.invite-item.status", player))
                        .mutate(meta -> meta.getPersistentDataContainer().set(
                                plugin.getGuiManager().memberKey(),
                                PersistentDataType.STRING,
                                INVITE_TAG + invite.invitedPlayer()))
                        .build());
            }
        }

        if (combined.isEmpty()) {
            int middleRow = (invSize / 9) / 2;
            int emptySlot = middleRow * 9 + 4;
            inventory.setItem(emptySlot, ItemBuilder.head(ItemBuilder.HEAD_NO_PLAYERS_EMPTY)
                    .name(plugin.getMessages().component("gui.applications.empty.name", player))
                    .lore(plugin.getMessages().component("gui.applications.empty.lore", player))
                    .build());
        }

        // Pagination only ever occurs once the menu has grown to its full 54-slot size (see computeSize);
        // slots 36/44 are the standard pagination slots for a 54-slot menu.
        if (page > 0) {
            inventory.setItem(36, ItemBuilder.head(ItemBuilder.HEAD_PREVIOUS)
                    .name(plugin.getMessages().component("gui.previous-page", player))
                    .build());
        }
        if (page < maxPage) {
            inventory.setItem(44, ItemBuilder.head(ItemBuilder.HEAD_NEXT)
                    .name(plugin.getMessages().component("gui.next-page", player))
                    .build());
        }

        inventory.setItem(invSize - 2, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(invSize - 1, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, Clan clan) {
        int slot = event.getRawSlot();
        int invSize = event.getView().getTopInventory().getSize();
        int backSlot = invSize - 2;
        int closeSlot = invSize - 1;

        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }
        if (slot == backSlot) {
            pageByPlayer.remove(player.getUniqueId());
            plugin.getGuiManager().openMain(player, clan);
            return;
        }
        if (slot == 36) {
            open(player, clan, pageByPlayer.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }
        if (slot == 44) {
            open(player, clan, pageByPlayer.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        String rawId = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(
                plugin.getGuiManager().memberKey(), PersistentDataType.STRING);
        if (rawId == null || rawId.startsWith(INVITE_TAG)) return;

        UUID applicantId;
        try {
            applicantId = UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        if (event.isLeftClick()) {
            plugin.getClanManager().acceptApplicationAsync(clan, player.getUniqueId(), applicantId)
                    .thenAccept(updatedClan -> plugin.runSync(() -> {
                        OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantId);
                        String name = applicant.getName() == null ? applicant.getUniqueId().toString() : applicant.getName();
                        plugin.getMessages().send(player, "gui.applications.accepted", Map.of("player", name));
                        Player target = Bukkit.getPlayer(applicantId);
                        if (target != null) {
                            plugin.getMessages().send(target, "clan.joined", Map.of("tag", updatedClan.tag()));
                        }
                        open(player, updatedClan);
                    }))
                    .exceptionally(throwable -> {
                        plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                        return null;
                    });
            return;
        }

        if (event.isRightClick()) {
            plugin.getClanManager().rejectApplicationAsync(clan, player.getUniqueId(), applicantId)
                    .thenRun(() -> plugin.runSync(() -> {
                        OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantId);
                        String name = applicant.getName() == null ? applicant.getUniqueId().toString() : applicant.getName();
                        plugin.getMessages().send(player, "gui.applications.rejected", Map.of("player", name));
                        open(player, clan);
                    }))
                    .exceptionally(throwable -> {
                        plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                        return null;
                    });
        }
    }

    public void clearPlayer(UUID playerId) {
        pageByPlayer.remove(playerId);
    }

    // 36-slot would reserve the same single 7-item content row as 27-slot once row 1 (9-17) is
    // correctly treated as frame (rule 4.1) instead of content, so it offers no extra capacity -
    // skipped in favor of jumping straight to 45 (14 items across two working-zone rows).
    private static int computeSize(int totalItems) {
        if (totalItems <= 7) return 27;
        if (totalItems <= 14) return 45;
        return 54;
    }

    /**
     * gui_gen: in a 27-slot menu, 9-17 IS the content zone (rule 4). In anything bigger, 9-17 is
     * always frame (rule 4.1) and the working zone starts at 18 - so content rows start at slot 9
     * only for the exact 27-slot case, and at slot 18 for every larger size this menu scales into.
     */
    private static int[] computeContentSlots(int invSize) {
        List<Integer> slots = new ArrayList<>();
        int contentRowCount = invSize == 27 ? 1 : (invSize / 9) - 3;
        int firstRowStart = invSize == 27 ? 9 : 18;
        for (int row = 0; row < contentRowCount; row++) {
            int rowStart = firstRowStart + row * 9;
            for (int col = 1; col <= 7; col++) {
                slots.add(rowStart + col);
            }
        }
        return slots.stream().mapToInt(i -> i).toArray();
    }

    /** gui_gen: 27-slot uses fillFrame27 (9-17 is content there). Anything bigger scales the
     *  54-slot frame (header, row 1 always-glass, footer) truncated to the menu's actual size. */
    private void fillGlass(Inventory inventory) {
        int size = inventory.getSize();
        if (size == 27) {
            GuiFrames.fillFrame27(inventory);
            return;
        }
        for (int slot = 1; slot <= 8; slot++) inventory.setItem(slot, GuiFrames.glassPane());
        for (int slot = 9; slot <= 17; slot++) inventory.setItem(slot, GuiFrames.glassPane());
        for (int slot = size - 9; slot < size; slot++) inventory.setItem(slot, GuiFrames.glassPane());
    }
}
