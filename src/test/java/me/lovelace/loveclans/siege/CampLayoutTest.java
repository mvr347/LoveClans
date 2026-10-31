package me.lovelace.loveclans.siege;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CampLayoutTest {
  @Test
  void allFourAnchorsKeepTheCampfireAtTheObjective() {
    var expected =
        List.of(
            new CampLayout.Offset(0, 0, 1),
            new CampLayout.Offset(-1, 0, 0),
            new CampLayout.Offset(0, 0, -1),
            new CampLayout.Offset(1, 0, 0));
    int index = 0;
    for (var direction : CampLayout.Direction.values()) {
      var a = CampLayout.anchor(direction);
      assertEquals(expected.get(index++), a);
      assertEquals(
          new CampLayout.Offset(0, 0, 0),
          a.plus(direction.rotate(new CampLayout.Offset(0, 0, -1))));
    }
  }

  @Test
  void independentPropsMatchTheArchiveContractInAllDirections() {
    var flags =
        List.of(
            new CampLayout.Offset(1, 0, 2),
            new CampLayout.Offset(-2, 0, 1),
            new CampLayout.Offset(-1, 0, -2),
            new CampLayout.Offset(2, 0, -1));
    var barrels =
        List.of(
            new CampLayout.Offset(-1, 0, -1),
            new CampLayout.Offset(1, 0, -1),
            new CampLayout.Offset(1, 0, 1),
            new CampLayout.Offset(-1, 0, 1));
    int i = 0;
    for (var d : CampLayout.Direction.values()) {
      assertEquals(
          flags.get(i), CampLayout.anchor(d).plus(d.rotate(new CampLayout.Offset(1, 0, 1))));
      assertEquals(
          barrels.get(i++), CampLayout.anchor(d).plus(d.rotate(new CampLayout.Offset(-1, 0, -2))));
    }
  }

  @Test
  void destroyedTowerUsesTheSharedRuinsWithoutAFunctionalBarrelOrLadder() {
    var blocks = CampLayout.blocks(4, true);
    assertTrue(blocks.values().stream().anyMatch(s -> s.contains("lit=false")));
    assertFalse(
        blocks.values().stream().anyMatch(s -> s.contains("barrel") || s.contains("ladder")));
    assertEquals("siege_camp_destroyed", CampLayout.model(4, true));
  }

  @Test
  void towerHasContinuousLadderLandingAndTwoBlocksOfHeadroom() {
    var blocks = CampLayout.blocks(4, false);
    for (int y = 0; y <= 6; y++)
      assertTrue(blocks.get(new CampLayout.Offset(0, y, 2)).contains("ladder"));
    // At the centre of each inhabited room, 1.8-tall players do not intersect the next floor.
    assertTrue(blocks.get(new CampLayout.Offset(1, 1, 0)).contains("type=bottom"));
    assertFalse(blocks.containsKey(new CampLayout.Offset(1, 2, 0)));
    assertTrue(blocks.get(new CampLayout.Offset(1, 3, 0)).contains("type=top"));
    assertFalse(blocks.containsKey(new CampLayout.Offset(1, 4, 0)));
    assertFalse(blocks.containsKey(new CampLayout.Offset(1, 5, 0)));
    assertTrue(blocks.get(new CampLayout.Offset(1, 6, 0)).contains("type=bottom"));
    assertTrue(blocks.get(new CampLayout.Offset(0, 6, -3)).contains("slab"));
  }

  @Test
  void everyPhysicalStateFitsTheReservedVolumeAndCanBeRotatedWithoutLosingCells() {
    for (int level = 0; level <= 4; level++)
      for (boolean broken : List.of(false, true)) {
        var cells = CampLayout.blocks(level, broken);
        for (var d : CampLayout.Direction.values())
          assertEquals(cells.size(), cells.keySet().stream().map(d::rotate).distinct().count());
        assertTrue(
            cells.keySet().stream()
                .allMatch(
                    p ->
                        Math.abs(p.x()) <= 3
                            && p.z() >= -4
                            && p.z() <= 3
                            && p.y() >= 0
                            && p.y() < 9));
      }
  }
}
