package me.lovelace.loveclans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemsAdderHookTest {
    @Test
    void isAvailableReturnsFalseWhenNotLoaded() {
        assertFalse(ItemsAdderHook.isAvailable());
    }

    @Test
    void createCustomStackReturnsNullGracefullyWhenNotLoaded() {
        assertNull(ItemsAdderHook.createCustomStack(null));
        assertNull(ItemsAdderHook.createCustomStack(""));
        assertNull(ItemsAdderHook.createCustomStack("itemsadder:custom_scroll"));
    }

    @Test
    void isCustomStackReturnsFalseWhenNotLoaded() {
        assertFalse(ItemsAdderHook.isCustomStack(null, "itemsadder:custom_scroll"));
        assertFalse(ItemsAdderHook.isCustomStack(null, null));
    }
}
