package com.mercuriusxeno.goo.ability.reap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When each kind of plant is ripe for Reap: a crop and cocoa at their full
 * age, a sweet berry bush from age two, a cave vine while it bears berries
 * (decision reap-breeze-harvests-and-replants).
 */
class RipeKindTest {

    @Test
    void aCropIsRipeOnlyAtItsFullAge() {
        assertFalse(RipeKind.CROP.ripe(6, 7, false));
        assertTrue(RipeKind.CROP.ripe(7, 7, false));
    }

    @Test
    void cocoaIsRipeOnlyAtItsFullAge() {
        assertFalse(RipeKind.COCOA.ripe(1, 2, false));
        assertTrue(RipeKind.COCOA.ripe(2, 2, false));
    }

    @Test
    void aSweetBerryBushIsRipeFromAgeTwo() {
        assertFalse(RipeKind.SWEET_BERRIES.ripe(1, 3, false));
        assertTrue(RipeKind.SWEET_BERRIES.ripe(2, 3, false));
        assertTrue(RipeKind.SWEET_BERRIES.ripe(3, 3, false));
    }

    @Test
    void aCaveVineIsRipeWhileItBearsBerries() {
        assertFalse(RipeKind.GLOW_BERRIES.ripe(0, 0, false));
        assertTrue(RipeKind.GLOW_BERRIES.ripe(0, 0, true));
    }
}
