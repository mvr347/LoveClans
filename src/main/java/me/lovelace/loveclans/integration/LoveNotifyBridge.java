package me.lovelace.loveclans.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Мост к LoveNotify (LoveCore). LoveClans не подключает lovecore-api на этапе компиляции ради
 * одного интерфейса, поэтому, как и в LoveBehavior/LoveShop, интеграция сделана через reflection
 * поверх Bukkit ServicesManager — включая сам вложенный enum Channel (тоже неизвестен на этапе
 * компиляции). Если LoveCore/LoveNotify недоступен — канал считается разрешённым (прежнее
 * поведение "показывать всегда").
 */
public final class LoveNotifyBridge {

    private final JavaPlugin plugin;

    public LoveNotifyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code channelName} — "ACTION_BAR" или "TITLE" (имена констант LoveNotify.Channel). */
    public boolean isChannelEnabled(UUID uuid, String channelName) {
        Object api = findLoveNotifyApi();
        if (api == null) {
            return true;
        }
        try {
            Class<?> channelClass = Class.forName("dev.lovelace.lovecore.api.notify.LoveNotify$Channel");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object channelValue = Enum.valueOf((Class<Enum>) channelClass, channelName);
            Method method = api.getClass().getMethod("isChannelEnabled", UUID.class, channelClass);
            Object result = method.invoke(api, uuid, channelValue);
            return !(result instanceof Boolean bool) || bool;
        } catch (Exception e) {
            plugin.getLogger().fine("Не удалось вызвать LoveNotify#isChannelEnabled: " + e.getMessage());
            return true;
        }
    }

    private Object findLoveNotifyApi() {
        Plugin loveCore = Bukkit.getPluginManager().getPlugin("LoveCore");
        if (loveCore == null || !loveCore.isEnabled()) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("dev.lovelace.lovecore.api.notify.LoveNotify");
            Collection<RegisteredServiceProvider<?>> registrations = Bukkit.getServicesManager().getRegistrations(loveCore);
            for (RegisteredServiceProvider<?> reg : registrations) {
                if (apiClass.isInstance(reg.getProvider())) {
                    return reg.getProvider();
                }
            }
        } catch (ClassNotFoundException e) {
            // LoveCore установлен, но версия старее LoveNotify — считаем недоступным.
        }
        return null;
    }
}
