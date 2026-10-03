package me.lovelace.loveclans.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoinFormatTest {

    @Test
    void parsePlainNumberAndDenominationText() {
        assertEquals(1500L, CoinFormat.parse("1500"));
        assertEquals(350L, CoinFormat.parse(" 3i 50c "));
        assertEquals(2_000L, CoinFormat.parse("1g"));
        assertThrows(IllegalArgumentException.class, () -> CoinFormat.parse("abc"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void configMoneyKeysParse() throws Exception {
        Map<String, Object> cfg;
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            cfg = new Yaml().load(in);
        }
        Map<String, Object> clans = (Map<String, Object>) cfg.get("clans");
        Map<String, Object> tax = (Map<String, Object>) ((Map<String, Object>) clans.get("chest")).get("tax");
        assertEquals(5_000L, MoneyParser.parse(String.valueOf(tax.get("base-amount")), MoneyParser.STANDARD));
        Map<String, Object> banner = (Map<String, Object>) clans.get("banner");
        assertEquals(6_000L, MoneyParser.parse(String.valueOf(banner.get("cost")), MoneyParser.STANDARD));
        Map<String, Object> siege = (Map<String, Object>) cfg.get("siege");
        Map<String, Object> fort = (Map<String, Object>) siege.get("fortification");
        assertEquals(1_000L, MoneyParser.parse(String.valueOf(fort.get("cost")), MoneyParser.STANDARD));
        Map<String, Object> economy = (Map<String, Object>) cfg.get("economy");
        assertTrue(((Number) ((Map<String, Object>) economy.get("migration")).get("factor")).doubleValue() > 0);
    }
}
