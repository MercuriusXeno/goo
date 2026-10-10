package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the agitator's thrum: lowest at the start interval, highest at the
 * floor, rising as the interval shrinks between them.
 */
class AgitateStepTest {

    private static final int START = 400;
    private static final int FLOOR = 40;
    private static final float TOLERANCE = 1e-6f;

    @Test
    void theThrumRisesAsTheIntervalShrinks() {
        float atStart = AgitateStep.thrumPitch(START, START, FLOOR);
        float midway = AgitateStep.thrumPitch(220, START, FLOOR);
        float atFloor = AgitateStep.thrumPitch(FLOOR, START, FLOOR);

        assertTrue(atStart < midway && midway < atFloor);
    }

    @Test
    void theThrumHoldsAtItsHighestPastTheFloor() {
        assertEquals(AgitateStep.thrumPitch(FLOOR, START, FLOOR), AgitateStep.thrumPitch(10, START, FLOOR), TOLERANCE);
    }
}
