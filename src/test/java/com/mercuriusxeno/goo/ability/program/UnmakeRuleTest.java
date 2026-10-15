package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An unmake takes the unstable crucible's own time for what it melts, divided
 * by its speed (decision unmake-waves-dissolve-by-crucible-cost).
 */
class UnmakeRuleTest {

    /** The unstable fuel's melt exponent: an item melts in ceil(mB ^ 0.5) ticks. */
    private static final double UNSTABLE_EXPONENT = 0.5;
    /** The unstable crucible's ticks for cobblestone, ceil(sqrt(1152)). */
    private static final int COBBLESTONE_TICKS = 34;

    @Test
    void anUnmakeTakesTheUnstableCruciblesTime() {
        assertEquals(COBBLESTONE_TICKS, UnmakeRule.workToUnmake(1152, UNSTABLE_EXPONENT, 1));
        assertEquals(136, UnmakeRule.workToUnmake(18432, UNSTABLE_EXPONENT, 1));
    }

    @Test
    void aFasterUnmakeTakesItsShareOfTheTime() {
        assertEquals(17, UnmakeRule.workToUnmake(1152, UNSTABLE_EXPONENT, 2));
    }

    @Test
    void nothingTakesLessThanOneTick() {
        assertEquals(1, UnmakeRule.workToUnmake(1, UNSTABLE_EXPONENT, 100));
    }
}
