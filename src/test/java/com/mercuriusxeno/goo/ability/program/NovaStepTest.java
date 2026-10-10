package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nova's crowd rule: each mob in the ring keeps nearly all of the freeze
 * however many stand in it.
 */
class NovaStepTest {

    private static final float CROWD = 0.05f;
    private static final float EPSILON = 1e-6f;

    @Test
    void aLoneMobTakesTheWholeFreeze() {
        assertEquals(1f, NovaStep.crowdShare(CROWD, 1));
    }

    @Test
    void eachFurtherMobThinsTheFreezeOnlySlightly() {
        assertEquals(1f / 1.05f, NovaStep.crowdShare(CROWD, 2), EPSILON);
        assertTrue(NovaStep.crowdShare(CROWD, 5) > 0.8f);
    }

    @Test
    void anEmptyRingThinsNothing() {
        assertEquals(1f, NovaStep.crowdShare(CROWD, 0));
    }
}
