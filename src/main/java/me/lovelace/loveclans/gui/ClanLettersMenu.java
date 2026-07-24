package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.diplomacy.ClanLetter;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Переписка между двумя кланами (§5.4). Не ограничена конфликтами/эмбарго/блокадой - только
 * во время войны/осады/набега недоступна (см. ClanDiplomacyMenu, откуда открывается это меню).
 * Простой список без пагинации (до 28 последних писем, framed content grid) - полноценная вкладка
 * дипломатии появится в рамках отдельной задачи по переделке UI (§6).
 */
public final class ClanLettersMenu {
    private static final int WRITE_SLOT = 4;
    // gui_gen 54-slot working zone is 18-44 only (three rows) — row 1 (9-17) is always frame,
    // never content, unlike the 27-slot menu's 9-17 content zone. Don't confuse the two.
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int BACK_SLOT = 52;
    private static final int CLOSE_SLOT = 53;

    private final LoveClansPlugin plugin;

    public ClanLettersMenu(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Clan sourceClan, Clan targetClan) {
        plugin.getDiplomacyManager().getLettersAsync(sourceClan.id(), targetClan.id()).thenAccept(letters ->
                plugin.runSync(() -> render(player, sourceClan, targetClan, new ArrayList<>(letters)))
        ).exceptionally(t -> {
            plugin.runSync(() -> plugin.sendOperationError(player, t));
            return null;
        });
    }

    private void render(Player player, Clan sourceClan, Clan targetClan, List<ClanLetter> letters) {
        ClanMenuHolder holder = new ClanMenuHolder(ClanMenuType.LETTERS, targetClan.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.getMessages().component("gui.letters.title", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player));
        holder.setInventory(inventory);

        GuiFrames.fillFrame54(inventory);

        inventory.setItem(WRITE_SLOT, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(plugin.getMessages().component("gui.letters.write.name", player))
                .lore(plugin.getMessages().component("gui.letters.write.lore", player))
                .build());

        if (letters.isEmpty()) {
            inventory.setItem(CONTENT_SLOTS[CONTENT_SLOTS.length / 2], ItemBuilder.of(Material.PAPER)
                    .name(plugin.getMessages().component("diplomacy.letter.empty-list", player))
                    .build());
        } else {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM HH:mm");
            for (int i = 0; i < letters.size() && i < CONTENT_SLOTS.length; i++) {
                ClanLetter letter = letters.get(i);
                boolean fromUs = letter.fromClanId().equals(sourceClan.id());
                String preview = letter.message().length() > 30 ? letter.message().substring(0, 30) + "..." : letter.message();
                ItemBuilder item = ItemBuilder.of(fromUs ? Material.PAPER : (letter.read() ? Material.MAP : Material.FILLED_MAP))
                        .name(plugin.getMessages().component(fromUs ? "gui.letters.item.sent-name" : "gui.letters.item.received-name",
                                Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()), player))
                        .lore(plugin.getMessages().component("gui.letters.item.preview", Map.of("text", preview), player))
                        .lore(plugin.getMessages().component("gui.letters.item.date", Map.of("date", format.format(new Date(letter.createdAt()))), player));
                inventory.setItem(CONTENT_SLOTS[i], item.build());
            }
        }

        inventory.setItem(BACK_SLOT, ItemBuilder.head(ItemBuilder.HEAD_BACK)
                .name(plugin.getMessages().component("gui.back", player))
                .build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);

        // Mark unread received letters as read now that they've been shown.
        for (ClanLetter letter : letters) {
            if (!letter.read() && letter.toClanId().equals(sourceClan.id())) {
                plugin.getDiplomacyManager().markLetterReadAsync(letter.id());
            }
        }
    }

    public void handleInventoryClick(Player player, Clan targetClan, int slot) {
        var sourceClanOpt = plugin.getClanManager().getPlayerClan(player.getUniqueId());
        if (sourceClanOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Clan sourceClan = sourceClanOpt.get();

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            plugin.getGuiManager().openDiplomacy(player, sourceClan, targetClan);
            return;
        }
        if (slot == WRITE_SLOT) {
            player.closeInventory();
            plugin.getMessages().send(player, "diplomacy.letter.prompt");
            plugin.expectChatInput(player.getUniqueId(), (message, cancelled) -> {
                if (cancelled) {
                    plugin.runSync(() -> open(player, sourceClan, targetClan));
                    return;
                }
                plugin.getDiplomacyManager().sendLetterAsync(sourceClan, player.getUniqueId(), targetClan, message)
                        .thenAccept(letter -> plugin.runSync(() -> {
                            plugin.getMessages().send(player, "diplomacy.letter.sent", Map.of("tag", targetClan.tag(), "color", targetClan.tagColor()));
                            open(player, sourceClan, targetClan);
                        }))
                        .exceptionally(t -> {
                            plugin.runSync(() -> {
                                plugin.sendOperationError(player, t);
                                open(player, sourceClan, targetClan);
                            });
                            return null;
                        });
            });
        }
    }
}
