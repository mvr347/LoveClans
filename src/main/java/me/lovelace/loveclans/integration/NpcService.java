package me.lovelace.loveclans.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import dev.lovelace.lovecore.api.provider.ProviderKind;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the NPC provider chosen by {@code integrations.npc-provider} ({@code global}, {@code citizens} or
 * {@code loveessential}).
 * The provider is looked up on every use because providers register late and the config can be reloaded. When the
 * chosen provider is missing the NPC feature is simply off: one warning per distinct problem, never an exception.
 */
public final class NpcService {

    public static final String CONFIG_PATH = "integrations.npc-provider";
    /** Provider used when LoveCore cannot answer: what the plugin did before the option existed. */
    public static final String DEFAULT_PROVIDER = "citizens";
    /** Config value that follows {@code integrations.provider} of LoveCore. */
    public static final String FOLLOW_GLOBAL = "global";

    private final Supplier<? extends ConfigurationSection> config;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;
    private volatile String lastWarning;

    public NpcService(Supplier<? extends ConfigurationSection> config, Consumer<String> warn) {
        this(config, id -> LoveCore.provider(NpcProvider.class, id), warn);
    }

    public NpcService(Supplier<? extends ConfigurationSection> config,
                      Function<String, Optional<NpcProvider>> lookup, Consumer<String> warn) {
        this.config = config;
        this.lookup = lookup;
        this.warn = warn;
    }

    /**
     * Provider id from the config: trimmed, lower-case. {@code global} (the default), absent or blank follows
     * {@code integrations.provider} of LoveCore; an explicit id wins. Without LoveCore it is {@code citizens}.
     */
    public static String providerId(ConfigurationSection config) {
        String raw = config.getString(CONFIG_PATH, FOLLOW_GLOBAL);
        try {
            return LoveCore.providerId(ProviderKind.NPC, raw);
        } catch (LinkageError coreMissingOrOld) {
            if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase(FOLLOW_GLOBAL)) {
                return DEFAULT_PROVIDER;
            }
            return raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    public String providerId() {
        return providerId(config.get());
    }

    /** The configured provider, or empty (with a one-time warning) when it is not registered. */
    public Optional<NpcProvider> provider() {
        String id = providerId();
        Optional<NpcProvider> found;
        try {
            found = lookup.apply(id);
        } catch (LinkageError | RuntimeException exception) {
            warnOnce(id + "/api", "LoveCore API недоступен (" + exception + "): NPC Гильдмастера и Основателя отключены.");
            return Optional.empty();
        }
        if (found.isEmpty()) {
            warnOnce(id + "/missing", "NPC-провайдер '" + id + "' (integrations.npc-provider) не зарегистрирован в LoveCore: "
                    + "NPC Гильдмастера и Основателя отключены. Установите/включите нужный плагин или смените провайдера.");
            return Optional.empty();
        }
        lastWarning = null;
        return found;
    }

    /** True when the clicked NPC belongs to the configured provider and is the one bound to this slot. */
    public boolean isBound(NpcHandle npc, NpcBindings.Slot slot) {
        if (npc == null) {
            return false;
        }
        String bound = NpcBindings.read(config.get(), slot);
        if (bound == null) {
            return false;
        }
        return providerId().equalsIgnoreCase(npc.providerId()) && NpcBindings.sameKey(npc.key(), bound);
    }

    /** Warns right after startup if bindings exist but their provider is not available. */
    public void checkAtStartup() {
        if (!NpcBindings.boundSlots(config.get()).isEmpty()) {
            provider();
        }
    }

    private void warnOnce(String state, String message) {
        if (!state.equals(lastWarning)) {
            lastWarning = state;
            warn.accept(message);
        }
    }
}
