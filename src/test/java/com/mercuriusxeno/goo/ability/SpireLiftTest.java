package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spire's lifted ground refills with deepslate below Y 0 and stone from Y 0 up (decision spire-rips-walls-and-platforms). */
class SpireLiftTest {

    @Test
    void groundBelowYZeroRefillsWithDeepslate() {
        assertTrue(SpireLift.refillsDeepslateAt(-1));
    }

    @Test
    void groundAtYZeroRefillsWithStone() {
        assertFalse(SpireLift.refillsDeepslateAt(0));
    }
}
