package me.lovelace.loveclans.gui;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ClanBannerCreationMenu implements InventoryHolder {

    private final LoveClansPlugin plugin;
    private final Player player;
    private final Location bannerLocation;
    private Inventory inventory;
    private String name = "";
    private String tag = "";
    private boolean open = true;

    public ClanBannerCreationMenu(LoveClansPlugin plugin, Player player, Location bannerLocation) {
        this.plugin = plugin;
        this.player = player;
        this.bannerLocation = bannerLocation;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                plugin.getMessages().component("gui.banner-create.title", player));

        GuiFrames.fillFrame27(inventory);
        inventory.setItem(25, GuiFrames.glassPane()); // footer is one full row: glass next to Close

        // Slot 0: Capital banner icon explaining the procedure
        inventory.setItem(0, ItemBuilder.of(Material.RED_BANNER)
                .name(plugin.getMessages().component("gui.banner-create.info.name", player))
                .lore(plugin.getMessages().components("gui.banner-create.info.lore", player))
                .build());

        inventory.setItem(10, ClanCreateMenu.nameButton(plugin, player, name));
        inventory.setItem(12, ClanCreateMenu.tagButton(plugin, player, tag));
        inventory.setItem(14, ClanCreateMenu.typeButton(plugin, player, open));
        inventory.setItem(16, ClanCreateMenu.createButton(plugin, player, name, tag, "gui.banner-create.ready", "gui.banner-create.action"));

        inventory.setItem(26, ItemBuilder.head(ItemBuilder.HEAD_CLOSE)
                .name(plugin.getMessages().component("gui.close", player))
                .build());

        player.openInventory(inventory);
    }

    public void handleInventoryClick(int slot) {
        switch (slot) {
            case 10 -> promptName();
            case 12 -> promptTag();
            case 14 -> { open = !open; open(); }
            case 16 -> tryCreate();
            case 26 -> player.closeInventory();
            default -> {}
        }
    }

    private void promptName() {
        player.closeInventory();
        plugin.getMessages().send(player, "gui.create.name.prompt");
        plugin.expectChatInput(player.getUniqueId(), (input, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(this::open);
                return;
            }
            String trimmed = input.trim();
            int min = plugin.getConfig().getInt("clans.name.min-length", 4);
            int max = plugin.getConfig().getInt("clans.name.max-length", 10);
            if (trimmed.length() < min || trimmed.length() > max) {
                plugin.getMessages().send(player, "clan.invalid-name",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                plugin.runSync(this::open);
                return;
            }
            this.name = trimmed;
            plugin.runSync(this::open);
        });
    }

    private void promptTag() {
        player.closeInventory();
        plugin.getMessages().send(player, "gui.create.tag.prompt");
        plugin.expectChatInput(player.getUniqueId(), (input, isCancelled) -> {
            if (isCancelled) {
                plugin.runSync(this::open);
                return;
            }
            String trimmed = input.trim();
            int min = plugin.getConfig().getInt("clans.tag.min-length", 3);
            int max = plugin.getConfig().getInt("clans.tag.max-length", 6);
            String pattern = plugin.getConfig().getString("clans.tag.pattern", "^[A-Za-z0-9_]+$");
            if (trimmed.length() < min || trimmed.length() > max || !trimmed.matches(pattern)) {
                plugin.getMessages().send(player, "clan.invalid-tag",
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
                plugin.runSync(this::open);
                return;
            }
            this.tag = trimmed;
            plugin.runSync(this::open);
        });
    }

    private void tryCreate() {
        if (name.isEmpty() || tag.isEmpty()) return;
        player.closeInventory();

        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.banner.already-in-clan");
            return;
        }

        var cooldownOpt = plugin.getClanManager().getCreationCooldownRemaining(player.getUniqueId());
        if (cooldownOpt.isPresent()) {
            plugin.getMessages().send(player, "clan.creation-cooldown",
                    Map.of("time", me.lovelace.loveclans.util.DurationFormat.format(cooldownOpt.get())));
            return;
        }

        // Сохраняем введенные данные в сессию основания клана (Фаза DATA_READY)
        plugin.getClanManager().startFoundationData(player.getUniqueId(), name, tag, open);
        plugin.getMessages().send(player, "clan.banner.setup-done");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
