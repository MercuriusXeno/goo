package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cadence Reap's held channel swings at: a blade on the hold's
 * first tick and on every tick the cadence names after it.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class SlashStepTest {

    private static final int EVERY = 3;

    @Test
    void theHoldsFirstTickSwings() {
        assertTrue(SlashStep.swingsOn(1, EVERY));
    }

    @Test
    void theTicksBetweenSwingsSwingNothing() {
        assertFalse(SlashStep.swingsOn(2, EVERY));
        assertFalse(SlashStep.swingsOn(3, EVERY));
    }

    @Test
    void everyCadenceTickAfterTheFirstSwings() {
        assertTrue(SlashStep.swingsOn(1 + EVERY, EVERY));
        assertTrue(SlashStep.swingsOn(1 + 2 * EVERY, EVERY));
    }

    @Test
    void aCadenceOfOneSwingsEveryTick() {
        assertTrue(SlashStep.swingsOn(2, 1));
    }
}
