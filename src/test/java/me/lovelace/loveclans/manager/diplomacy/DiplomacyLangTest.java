package me.lovelace.loveclans.manager.diplomacy;

import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions.Action;
import me.lovelace.loveclans.manager.diplomacy.DiplomacyActions.Context;
import me.lovelace.loveclans.model.diplomacy.DiplomacyTier;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The diplomacy menu builds many lang keys dynamically, so GuiLangKeysTest cannot see them: check them here. */
class DiplomacyLangTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> lang() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream in = DiplomacyLangTest.class.getResourceAsStream("/lang.yml")) {
            return new Yaml(options).load(in);
        }
    }

    private static boolean exists(Map<String, Object> root, String dotted) {
        Object current = root;
        for (String part : dotted.split("[.]")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) return false;
            current = map.get(part);
        }
        return true;
    }

    @Test
    void everyDynamicDiplomacyKeyExists() throws Exception {
        Map<String, Object> lang = lang();
        List<String> keys = new ArrayList<>();
        String act = "gui.diplomacy.act.";
        for (String base : List.of("gift", "trade", "trade-with-pact", "improve", "insult", "embargo", "embargo-active",
                "blockade", "blockade-active", "blockade-blocked", "conflict", "conflict-active")) {
            keys.add(act + base + ".name");
            keys.add(act + base + ".lore");
        }
        for (String pact : List.of("nap", "trade-pact", "alliance")) {
            for (String suffix : List.of("", "-pending", "-incoming", "-active")) {
                keys.add(act + pact + suffix + ".name");
                keys.add(act + pact + suffix + ".lore");
            }
        }
        for (DiplomacyTier tier : DiplomacyTier.values()) {
            String name = tier.name().toLowerCase();
            keys.add("gui.diplomacy.state.tier-" + name);
            keys.add("diplomacy.tier." + name);
        }
        keys.addAll(List.of("gui.diplomacy.state.name", "gui.diplomacy.state.lore", "gui.diplomacy.state.next",
                "gui.diplomacy.state.next-allied", "gui.diplomacy.state.prev", "gui.diplomacy.state.none",
                "gui.diplomacy.state.embargo", "gui.diplomacy.state.blockade", "gui.diplomacy.unavailable",
                "gui.confirm.break-pact.title", "gui.confirm.break-pact.lore", "diplomacy.tier-improved",
                "diplomacy.tier-worsened", "diplomacy.pact.auto-broken", "diplomacy.pact.score-too-low",
                "diplomacy.improve.sent", "diplomacy.improve.received", "diplomacy.improve.cooldown",
                "diplomacy.improve.not-enough-money", "diplomacy.insult.sent", "diplomacy.insult.received",
                "diplomacy.insult.cooldown", "diplomacy.deny.cooldown"));

        // Every reason the action matrix can return is a chat/lore message
        for (Action action : Action.values()) {
            for (DiplomacyTier tier : DiplomacyTier.values()) {
                for (boolean pact : new boolean[]{false, true}) {
                    String key = DiplomacyActions.denyKey(action, new Context(tier, tier.minScore(), pact, pact, pact), Integer.MAX_VALUE);
                    if (key != null) keys.add(key);
                }
            }
        }

        List<String> missing = keys.stream().filter(key -> !exists(lang, key)).distinct().toList();
        assertTrue(missing.isEmpty(), "keys missing from lang.yml: " + missing);
    }
}
