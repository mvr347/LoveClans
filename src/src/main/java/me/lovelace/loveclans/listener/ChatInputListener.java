package me.lovelace.loveclans.listener;

import me.lovelace.loveclans.LoveClansPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ChatInputListener implements Listener {
    private final LoveClansPlugin plugin;

    public ChatInputListener(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check for pending claim cancellation first
        if (plugin.getClanManager().hasPendingClaim(player.getUniqueId())) {
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("отмена")) {
                event.setCancelled(true);
                plugin.runSync(() -> {
                    plugin.getClanManager().cancelPendingClaim(player.getUniqueId());
                    // Message is already sent by cancelPendingClaim
                });
                return; // Handled the cancellation
            }
        }

        // If not a pending claim cancellation, proceed with generic chat input listeners
        plugin.getChatInputListener(player.getUniqueId()).ifPresent(callback -> {
            event.setCancelled(true);
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("отмена")) {
                plugin.runSync(() -> {
                    callback.accept(null, true);
                    plugin.getMessages().send(player, "general.chat-input-cancelled");
                });
            } else {
                plugin.runSync(() -> callback.accept(message, false));
            }
        });
    }
}