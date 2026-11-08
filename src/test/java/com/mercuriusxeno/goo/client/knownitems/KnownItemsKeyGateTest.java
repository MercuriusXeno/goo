package com.mercuriusxeno.goo.client.knownitems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G opens the known items overlay only with an empty main hand, no glove held and no screen open
 * (decisions g-opens-the-known-items-menu-empty-handed, menu-places-a-ghost-item-in-the-empty-hand).
 */
class KnownItemsKeyGateTest {

    @Test
    void emptyHandWithNoGloveAndNoScreenOpensTheOverlay() {
        assertTrue(KnownItemsKeyGate.pressOpensOverlay(true, false, false));
    }

    @Test
    void heldGloveLeavesThePressToTheRadial() {
        assertFalse(KnownItemsKeyGate.pressOpensOverlay(true, true, false));
    }

    @Test
    void openScreenOpensNothing() {
        assertFalse(KnownItemsKeyGate.pressOpensOverlay(true, false, true));
    }

    @Test
    void itemInTheMainHandOpensNothing() {
        assertFalse(KnownItemsKeyGate.pressOpensOverlay(false, false, false));
    }
}
