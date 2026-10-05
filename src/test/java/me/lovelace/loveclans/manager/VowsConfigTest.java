package me.lovelace.loveclans.manager;

import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the shipped config.yml / lang.yml against the mistakes that fail silently at runtime. */
class VowsConfigTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = VowsConfigTest.class.getResourceAsStream(resource)) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> root, String dotted) {
        Object current = root;
        for (String part : dotted.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) return null;
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    @Test
    void ritualsAndSuccessionVotesAreDisabledByDefault() throws Exception {
        Map<String, Object> config = load("/config.yml");
        assertEquals(Boolean.FALSE, path(config, "mechanics.rituals.enabled"));
        assertEquals(Boolean.FALSE, path(config, "mechanics.succession.enabled"));
    }

    @Test
    void recognitionCostMatchesTheCodeDefault() throws Exception {
        Map<String, Object> config = load("/config.yml");
        long configured = MoneyParser.parse(String.valueOf(path(config, "clans.recognition.cost")), MoneyParser.STANDARD);
        assertEquals(ClanRecognitionService.DEFAULT_COST, configured);
        assertEquals(25 * 2_000L, configured, "25 gold coins");
    }

    @Test
    void marshalNpcAndDailyPoolAreGone() throws Exception {
        Map<String, Object> config = load("/config.yml");
        assertEquals(null, path(config, "clans.contracts.npc-id"));
        assertEquals(null, path(config, "clans.contracts.daily"));
        assertEquals(-1, path(config, "clans.guildmaster.npc-id"));
    }

    @Test
    void weeklyPoolIsLargeEnoughForThreeChoicesAndEveryEntryIsUsable() throws Exception {
        Map<String, Object> config = load("/config.yml");
        Object pool = path(config, "clans.contracts.weekly.pool");
        assertTrue(pool instanceof Map<?, ?>);
        Map<?, ?> entries = (Map<?, ?>) pool;
        assertTrue(entries.size() >= 3, "three offers need at least three weekly vows");
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            Map<?, ?> vow = (Map<?, ?>) entry.getValue();
            assertTrue(vow.get("name") instanceof String, entry.getKey() + " has no name");
            assertTrue(((Number) vow.get("reward-xp")).longValue() > 0, entry.getKey() + " has no reward");
            assertTrue(((Map<?, ?>) vow.get("objective")).get("type") instanceof String, entry.getKey() + " has no objective type");
        }
    }

    @Test
    void monthlyMultipliersArePositive() throws Exception {
        Map<String, Object> config = load("/config.yml");
        assertTrue(((Number) path(config, "clans.contracts.monthly.target-multiplier")).doubleValue() >= 1.0);
        assertTrue(((Number) path(config, "clans.contracts.monthly.reward-multiplier")).doubleValue() > 0.0);
        assertEquals(3, ((Number) path(config, "clans.contracts.weekly.choices")).intValue());
        assertEquals(3, ((Number) path(config, "clans.contracts.monthly.choices")).intValue());
    }

    /**
     * MessageService answers a missing key with the key itself, so a typo or a forgotten entry would show a raw
     * "gui.contracts.button.ready" to players instead of failing - list every key the new screens use.
     */
    @Test
    void everyKeyTheNewScreensUseExistsInLang() throws Exception {
        Map<String, Object> lang = load("/lang.yml");
        List<String> keys = List.of(
                "gui.contracts-title", "gui.contracts.board.title", "gui.contracts.board.lore",
                "gui.contracts.button.weekly-name", "gui.contracts.button.monthly-name", "gui.contracts.button.current",
                "gui.contracts.button.in-progress", "gui.contracts.button.ready", "gui.contracts.button.claimed",
                "gui.contracts.button.not-taken", "gui.contracts.button.choose", "gui.contracts.button.none-available",
                "gui.contracts.button.refresh", "gui.contracts.choice.weekly-title", "gui.contracts.choice.monthly-title",
                "gui.contracts.choice.info-title", "gui.contracts.choice.info-lore", "gui.contracts.info.claimed",
                "gui.contracts.item.name", "gui.contracts.item.reward-scaled", "gui.contracts.item.reward-points",
                "gui.contracts.item.expires", "gui.contracts.item.claim-hint", "gui.contracts.item.select",
                "gui.guildmaster.title", "gui.guildmaster.info.title", "gui.guildmaster.info.lore",
                "gui.guildmaster.button.vows.name", "gui.guildmaster.button.vows.lore",
                "gui.guildmaster.button.recognition.name", "gui.guildmaster.button.recognition.lore",
                "gui.guildmaster.button.clans.name", "gui.guildmaster.button.clans.lore",
                "gui.recognition.title", "gui.recognition.info-name", "gui.recognition.info-lore", "gui.recognition.not-enough",
                "recognition.owner-only", "recognition.already", "recognition.cannot-afford", "recognition.success",
                "recognition.failed-refunded", "ritual.disabled", "succession.disabled",
                "contract.unknown", "contract.none-available", "contract.already-active", "contract.none-active",
                "contract.not-completed", "contract.already-claimed", "contract.selected", "contract.reward-claimed",
                "contract.completed", "contract.failed", "admin.npc.unknown-type",
                "clan.not-in-clan", "clan.list.empty", "clan.creation-economy-unavailable", "general.no-permission",
                "gui.confirm.yes", "gui.confirm.no", "gui.back", "gui.close");
        for (String key : keys) {
            assertFalse(path(lang, key) == null, "lang.yml has no key " + key);
        }
    }
}
