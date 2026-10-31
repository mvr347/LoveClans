package me.lovelace.loveclans.siege;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CampProfilesTest {
  @Test
  void bundledPhysicalProfilesMatchTheTestedLayoutForEveryState() throws Exception {
    try (var stream = getClass().getResourceAsStream("/siege-layouts.yml")) {
      assertNotNull(stream);
      var yaml =
          YamlConfiguration.loadConfiguration(
              new InputStreamReader(stream, StandardCharsets.UTF_8));
      for (int level = 0; level <= 4; level++) check(yaml, level, false);
      check(yaml, 0, true);
    }
  }

  private void check(YamlConfiguration yaml, int level, boolean broken) {
    Map<CampLayout.Offset, String> profile = new HashMap<>();
    for (var value : yaml.getMapList("profiles." + CampLayout.model(level, broken))) {
      List<?> p = (List<?>) value.get("offset");
      profile.put(
          new CampLayout.Offset(
              ((Number) p.get(0)).intValue(),
              ((Number) p.get(1)).intValue(),
              ((Number) p.get(2)).intValue()),
          value.get("block").toString());
    }
    assertEquals(CampLayout.blocks(level, broken), profile);
  }
}
