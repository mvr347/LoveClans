package me.lovelace.loveclans.manager.stateorder;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateOrderPickerTest {

    @Test
    void categoryNeverRepeatsThePreviousOne() {
        Random random = new Random(1);
        for (StateOrderCategory previous : StateOrderCategory.values()) {
            for (int i = 0; i < 200; i++) {
                assertNotEquals(previous, StateOrderPicker.pickCategory(previous, random));
            }
        }
    }

    @Test
    void everyOtherCategoryIsReachable() {
        Random random = new Random(2);
        var seen = new HashSet<StateOrderCategory>();
        for (int i = 0; i < 200; i++) seen.add(StateOrderPicker.pickCategory(StateOrderCategory.FOOD, random));
        assertEquals(java.util.Set.of(StateOrderCategory.BUILDING, StateOrderCategory.RAW), seen);
        assertTrue(StateOrderPicker.pickCategory(null, random) != null);
    }

    @Test
    void materialsAreDistinctAndBounded() {
        Random random = new Random(3);
        List<String> pool = List.of("A", "B", "C", "D", "E", "F", "A");
        for (int i = 0; i < 100; i++) {
            List<String> picked = StateOrderPicker.pickMaterials(pool, 4, random);
            assertEquals(4, picked.size());
            assertEquals(4, new HashSet<>(picked).size());
            assertTrue(pool.containsAll(picked));
        }
        assertEquals(2, StateOrderPicker.pickMaterials(List.of("X", "Y"), 4, random).size());
        assertEquals(0, StateOrderPicker.pickMaterials(List.of(), 4, random).size());
    }
}
