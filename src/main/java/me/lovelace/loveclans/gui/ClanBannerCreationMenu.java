package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.textures.HeadTextures;
import me.lovelace.loveclans.util.ClanItemFactory;
import me.lovelace.loveclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

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

        // Slot 0: Capital banner icon explaining the procedure
        inventory.setItem(0, ItemBuilder.of(Material.RED_BANNER)
                .name(plugin.getMessages().component("gui.banner-create.info.name", player))
                .lore(plugin.getMessages().components("gui.banner-create.info.lore", player))
                .build());

        String nameDisplay = name.isEmpty() ? plugin.getMessages().raw("gui.create.name.not-set") : name;
        inventory.setItem(10, ItemBuilder.head(HeadTextures.HEAD_NAME)
                .name(plugin.getMessages().component("gui.create.name.button", player))
                .lore(plugin.getMessages().component("gui.create.name.lore", Map.of("value", nameDisplay), player))
                .build());

        String tagDisplay = tag.isEmpty() ? plugin.getMessages().raw("gui.create.tag.not-set") : tag;
        inventory.setItem(12, ItemBuilder.head(HeadTextures.HEAD_TAG)
                .name(plugin.getMessages().component("gui.create.tag.button", player))
                .lore(plugin.getMessages().component("gui.create.tag.lore", Map.of("value", tagDisplay), player))
                .build());

        inventory.setItem(14, ItemBuilder.head(open ? HeadTextures.HEAD_OPEN : HeadTextures.HEAD_CLOSED)
                .name(plugin.getMessages().component(open ? "gui.create.type.open" : "gui.create.type.closed", player))
                .lore(plugin.getMessages().components("gui.create.type.lore", player))
                .build());

        boolean ready = !name.isEmpty() && !tag.isEmpty();
        inventory.setItem(16, ItemBuilder.head(ready ? ItemBuilder.HEAD_DELETE_YES : ItemBuilder.HEAD_INACTIVE)
                .name(plugin.getMessages().component(ready ? "gui.banner-create.ready" : "gui.create.create.not-ready", player))
                .build());

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

        // Забираем одно знамя создания из инвентаря игрока
        boolean removed = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && plugin.getClanManager().getClanItemFactory().isClanCreationBanner(item)) {
                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    player.getInventory().remove(item);
                }
                removed = true;
                break;
            }
        }

        if (!removed) {
            plugin.getMessages().send(player, "clan.banner.no-banner-in-hand");
            return;
        }

        plugin.getClanManager().createClanAsync(name, tag, player.getUniqueId(), open, false)
                .thenAccept(created -> plugin.runSync(() -> {
                    // Ставим физический баннер на место
                    Block block = bannerLocation.getBlock();
                    block.setType(Material.RED_BANNER);
                    BlockState state = block.getState();
                    if (state instanceof TileState tileState) {
                        PersistentDataContainer blockPdc = tileState.getPersistentDataContainer();
                        blockPdc.set(ClanItemFactory.BANNER_TYPE_KEY, PersistentDataType.STRING, "CAPITAL");
                        blockPdc.set(ClanItemFactory.CLAN_ID_KEY, PersistentDataType.STRING, created.id().toString());
                        tileState.update(true, false);
                    }

                    // Объявляем столичную территорию вокруг установленного знамени
                    plugin.getClanManager().claimTerritoryAsync(created, bannerLocation, player, "CAPITAL")
                            .thenRun(() -> plugin.runSync(() -> {
                                plugin.getMessages().send(player, "gui.banner-create.success",
                                        Map.of("tag", created.tag(), "name", created.name()));
                                plugin.getGuiManager().openMain(player, created);
                            }))
                            .exceptionally(t -> {
                                plugin.runSync(() -> plugin.sendOperationError(player, t));
                                return null;
                            });
                }))
                .exceptionally(t -> {
                    plugin.runSync(() -> {
                        // Возвращаем знамя при ошибке
                        player.getInventory().addItem(plugin.getClanManager().getClanItemFactory().createClanCreationBanner());
                        plugin.sendOperationError(player, t);
                    });
                    return null;
                });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
