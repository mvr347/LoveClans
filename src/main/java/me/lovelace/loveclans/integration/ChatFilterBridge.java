package me.lovelace.loveclans.integration;

import org.bukkit.Bukkit;

/**
 * Reflection bridge into LoveChatFilter's {@code LoveChatFilterAPI} service, the same way LoveTweaks
 * reaches it: no compile-time dependency. Without the plugin, or with an older build that lacks
 * {@code containsAdvertising}, the missing answer is "no".
 */
public final class ChatFilterBridge implements NameCheck.Filter {
    private static final String API_CLASS = "me.lovelace.lovechatfilter.api.LoveChatFilterAPI";

    @Override
    public boolean isProfane(String text) {
        return ask("isProfane", text);
    }

    @Override
    public boolean containsAdvertising(String text) {
        return ask("containsAdvertising", text);
    }

    private boolean ask(String method, String text) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Object service = Bukkit.getServicesManager().load(api);
            if (service == null) return false;
            Object answer = api.getMethod(method, String.class).invoke(service, text);
            return answer instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
}
