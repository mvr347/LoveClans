package me.lovelace.loveclans.integration;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcServiceTest {

    private static NpcProvider fakeProvider() {
        return (NpcProvider) Proxy.newProxyInstance(NpcProvider.class.getClassLoader(),
                new Class<?>[]{NpcProvider.class}, (proxy, method, args) -> method.getName().equals("byKey") ? Optional.empty() : null);
    }

    private static NpcHandle handle(String providerId, String key) {
        return (NpcHandle) Proxy.newProxyInstance(NpcHandle.class.getClassLoader(), new Class<?>[]{NpcHandle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "providerId" -> providerId;
                    case "key" -> key;
                    default -> null;
                });
    }

    private static YamlConfiguration config(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void startupGraceDoesNotHideALaterProviderLoss() throws Exception {
        var c = config("");
        boolean[] graceDone = {false}, present = {false};
        List<String> warnings = new ArrayList<>();
        var service = new NpcService(() -> c, id -> present[0] ? Optional.of(fakeProvider()) : Optional.empty(),
                warnings::add, () -> graceDone[0]);
        assertTrue(service.provider().isEmpty());
        assertTrue(warnings.isEmpty());
        present[0] = true;
        assertTrue(service.provider().isPresent());
        present[0] = false;
        service.provider();
        assertEquals(1, warnings.size());
        graceDone[0] = true;
        service.provider();
        assertEquals(1, warnings.size());
    }

    @Test
    void providerIsAlwaysLoveEssential() throws Exception {
        var empty = config("");
        assertEquals("loveessential", new NpcService(() -> empty, id -> Optional.empty(), message -> { }).providerId());
        assertEquals("loveessential", NpcService.PROVIDER_ID);
    }

    @Test
    void leftoverProviderValuesAreReportedButNeverUsed() throws Exception {
        assertEquals("citizens", NpcService.ignoredProviderValue(config("integrations:\n  npc-provider: citizens\n")));
        assertEquals("cmi", NpcService.ignoredProviderValue(config("integrations:\n  npc-provider: ' cmi '\n")));
        assertNull(NpcService.ignoredProviderValue(config("integrations:\n  npc-provider: global\n")));
        assertNull(NpcService.ignoredProviderValue(config("integrations:\n  npc-provider: ' LoveEssential '\n")));
        assertNull(NpcService.ignoredProviderValue(config("integrations:\n  npc-provider: '  '\n")));
        assertNull(NpcService.ignoredProviderValue(config("")));
    }

    @Test
    void startupCheckReportsALeftoverProviderValueOnce() throws Exception {
        var config = config("integrations:\n  npc-provider: citizens\nclans:\n  banner:\n    npc-key: '5'\n");
        List<String> warnings = new ArrayList<>();
        var service = new NpcService(() -> config, id -> Optional.of(fakeProvider()), warnings::add);
        service.checkAtStartup();
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("citizens") && warnings.get(0).contains("игнорируется"), warnings.get(0));
    }

    @Test
    void onlyLoveEssentialIsAsked() throws Exception {
        var config = config("integrations:\n  npc-provider: citizens\n");
        List<String> asked = new ArrayList<>();
        var service = new NpcService(() -> config, id -> {
            asked.add(id);
            return Optional.of(fakeProvider());
        }, message -> { });
        assertTrue(service.provider().isPresent());
        assertEquals(List.of("loveessential"), asked, "the old value must not pick another provider");
    }

    @Test
    void missingProviderWarnsOnceAndDoesNotFallBackToAnotherProvider() throws Exception {
        var config = config("");
        List<String> warnings = new ArrayList<>();
        List<String> asked = new ArrayList<>();
        var service = new NpcService(() -> config, id -> {
            asked.add(id);
            return Optional.empty();
        }, warnings::add);
        assertTrue(service.provider().isEmpty());
        assertTrue(service.provider().isEmpty());
        assertEquals(List.of("loveessential", "loveessential"), asked);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("loveessential"));
    }

    @Test
    void warnsAgainAfterTheProviderCameAndWent() throws Exception {
        var config = config("");
        List<String> warnings = new ArrayList<>();
        boolean[] present = {false};
        var service = new NpcService(() -> config, id -> present[0] ? Optional.of(fakeProvider()) : Optional.empty(),
                warnings::add);
        service.provider();
        present[0] = true;
        assertTrue(service.provider().isPresent());
        present[0] = false;
        service.provider();
        assertEquals(2, warnings.size(), "a provider that disappeared again is worth a new warning");
    }

    @Test
    void lookupFailureIsReportedNotThrown() throws Exception {
        var config = config("");
        List<String> warnings = new ArrayList<>();
        var service = new NpcService(() -> config, id -> {
            throw new NoClassDefFoundError("dev/lovelace/lovecore/api/LoveCore");
        }, warnings::add);
        assertTrue(service.provider().isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void clicksOfOtherProvidersAndOtherKeysAreIgnored() throws Exception {
        var config = config("clans:\n  guildmaster:\n    npc-key: '12'\n  banner:\n    npc-id: 5\n");
        var service = new NpcService(() -> config, id -> Optional.empty(), message -> { });
        assertTrue(service.isBound(handle("loveessential", "12"), NpcBindings.Slot.GUILDMASTER));
        assertFalse(service.isBound(handle("loveessential", "13"), NpcBindings.Slot.GUILDMASTER));
        assertFalse(service.isBound(handle("other", "12"), NpcBindings.Slot.GUILDMASTER),
                "the same key from another provider must not open the menu");
        assertFalse(service.isBound(handle("loveessential", "12"), NpcBindings.Slot.BANNER));
        assertTrue(service.isBound(handle("loveessential", "5"), NpcBindings.Slot.BANNER), "legacy numeric npc-id still binds");
        assertFalse(service.isBound(null, NpcBindings.Slot.BANNER));
    }

    @Test
    void extKeysBindToLoveEssentialNpcs() throws Exception {
        var config = config("clans:\n  banner:\n    npc-key: ext-0badf00d\n");
        var service = new NpcService(() -> config, id -> Optional.empty(), message -> { });
        assertTrue(service.isBound(handle("loveessential", "ext-0badf00d"), NpcBindings.Slot.BANNER));
        assertFalse(service.isBound(handle("other", "ext-0badf00d"), NpcBindings.Slot.BANNER));
    }
}
