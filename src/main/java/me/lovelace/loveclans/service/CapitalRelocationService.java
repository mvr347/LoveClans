package me.lovelace.loveclans.service;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.util.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Moving the capital with a Relocation Seal. The seal is spent immediately, the current claim is destroyed and the capital
 * banner lands in the player's main hand, ready to be placed at the new spot. Only recognized clans may do it, only the
 * leader or a guardian, never during a conflict, and a clan cooldown stops it from becoming a way to dodge a siege.
 */
public final class CapitalRelocationService {
    public record Block(String key, Map<String, String> placeholders) {
    }

    private final LoveClansPlugin plugin;

    public CapitalRelocationService(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canManage(Clan clan, UUID actorId) {
        return clan.member(actorId).map(m -> m.rank() == ClanRank.LEADER || m.rank() == ClanRank.GUARDIAN).orElse(false);
    }

    public Optional<Block> check(Clan clan, UUID actorId) {
        if (!clan.hasCapital()) return Optional.of(new Block("gui.capital.no-house-lore", Map.of()));
        if (!canManage(clan, actorId)) return Optional.of(new Block("gui.capital.no-permission-lore", Map.of()));
        if (!clan.isRecognized()) return Optional.of(new Block("deny.relocation.not-recognized", Map.of()));
        if (plugin.getClanManager().inAnyConflict(clan.id())) return Optional.of(new Block("gui.capital.war-blocked", Map.of()));
        long cooldown = plugin.getModifierManager().getRelocateCooldownRemaining(clan.id());
        if (cooldown > 0) {
            return Optional.of(new Block("deny.relocation.cooldown", Map.of("time", TimeUtil.formatDuration(cooldown))));
        }
        return Optional.empty();
    }

    /**
     * Spends one seal from {@code sealStack} and moves the capital. The seal is returned if the move fails, so a refusal
     * from the storage or the claim hook never eats it.
     */
    public void relocate(Player player, Clan clan, ItemStack sealStack) {
        Optional<Block> block = check(clan, player.getUniqueId());
        if (block.isPresent()) {
            plugin.getMessages().sendDeny(player, block.get().key(), block.get().placeholders());
            return;
        }
        ItemStack refund = sealStack.clone();
        refund.setAmount(1);
        sealStack.setAmount(sealStack.getAmount() - 1);
        player.setItemOnCursor(sealStack.getAmount() <= 0 ? null : sealStack);

        long cooldownHours = plugin.getConfig().getLong("clans.capital.relocate-cooldown-hours", 72L);
        plugin.getClanManager().relocateCapitalTerritoryAsync(clan, player.getUniqueId(), true)
                .thenRun(() -> plugin.runSync(() -> {
                    plugin.getModifierManager().grantRelocateCooldown(clan.id(), cooldownHours);
                    player.closeInventory();
                    plugin.getMessages().send(player, "gui.capital.relocate.done");
                }))
                .exceptionally(t -> {
                    plugin.runSync(() -> {
                        if (!player.getInventory().addItem(refund).isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), refund);
                        }
                        plugin.sendOperationError(player, t);
                    });
                    return null;
                });
    }
}
