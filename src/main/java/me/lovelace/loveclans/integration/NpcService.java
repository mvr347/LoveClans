package me.lovelace.loveclans.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import dev.lovelace.lovecore.api.provider.Providers;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the NPC provider, which is always LoveEssential (2026-11-21: the provider switch was removed).
 * The provider is looked up on every use because it registers late. When it is missing the NPC feature is
 * simply off: one warning per distinct problem, never an exception.
 */
public final class NpcService {

    /** The only supported NPC provider. */
    public static final String PROVIDER_ID = Providers.LOVE_ESSENTIAL;
    /** Leftover key of the removed provider switch: read only to report that its value is ignored. */
    public static final String LEGACY_PROVIDER_PATH = "integrations.npc-provider";

    private final Supplier<? extends ConfigurationSection> config;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Consumer<String> warn;
    private volatile String lastWarning;
    private final java.util.function.BooleanSupplier warningsReady;
    private volatile boolean seenReady;

    public NpcService(Supplier<? extends ConfigurationSection> config, Consumer<String> warn) {
        this(config, id -> LoveCore.provider(NpcProvider.class, id), warn, startupGrace());
    }

    public NpcService(Supplier<? extends ConfigurationSection> config,
                      Function<String, Optional<NpcProvider>> lookup, Consumer<String> warn) {
        this(config, lookup, warn, () -> true);
    }

    public NpcService(Supplier<? extends ConfigurationSection> config,
                      Function<String, Optional<NpcProvider>> lookup, Consumer<String> warn,
                      java.util.function.BooleanSupplier warningsReady) {
        this.warningsReady = warningsReady;
        this.config = config;
        this.lookup = lookup;
        this.warn = warn;
    }

    private static java.util.function.BooleanSupplier startupGrace() {
        int started = org.bukkit.Bukkit.getServer() == null ? 0 : org.bukkit.Bukkit.getCurrentTick();
        return () -> org.bukkit.Bukkit.getServer() == null || org.bukkit.Bukkit.getCurrentTick() - started >= 200;
    }

    public String providerId() {
        return PROVIDER_ID;
    }

    /** A leftover {@code integrations.npc-provider} value that is ignored, or {@code null} when there is none. */
    public static String ignoredProviderValue(ConfigurationSection config) {
        String raw = config.getString(LEGACY_PROVIDER_PATH, null);
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("global") || value.equalsIgnoreCase(PROVIDER_ID)) return null;
        return value;
    }

    /** LoveEssential's provider, or empty (with a one-time warning) when it is not registered. */
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
            if (!seenReady && !warningsReady.getAsBoolean()) return Optional.empty();
            warnOnce(id + "/missing", "NPC-провайдер '" + id + "' не зарегистрирован в LoveCore: "
                    + "NPC Гильдмастера и Основателя отключены. Установите/включите LoveEssential.");
            return Optional.empty();
        }
        lastWarning = null;
        seenReady = true;
        return found;
    }

    /** True when the clicked NPC belongs to LoveEssential and is the one bound to this slot. */
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
        String ignored = ignoredProviderValue(config.get());
        if (ignored != null) {
            warn.accept("integrations.npc-provider: значение '" + ignored + "' игнорируется, NPC берутся только из "
                    + "LoveEssential; удалите ключ из config.yml.");
        }
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
