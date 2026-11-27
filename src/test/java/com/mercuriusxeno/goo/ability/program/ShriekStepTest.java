package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The Shriek blob shrieks on its landing tick, calling every hostile in
 * reach then for the ticks the step lasts, and the step ends on that tick
 * (decision shriek-blob-cries-once-and-calls-earshot).
 */
class ShriekStepTest {

    private static final double REACH = 16;
    private static final int LASTS = 600;
    private static final ShriekStep STEP = new ShriekStep(Expr.literal(REACH), Expr.literal(LASTS));

    @Test
    void theLandingTickShrieksAcrossTheReachForTheStepsLasts() {
        ShriekHost host = mock(ShriekHost.class);
        STEP.tick(new StepContext(host, 0, 0));
        verify(host).shriek(REACH, LASTS);
    }

    @Test
    void theStepEndsOnTheLandingTick() {
        assertTrue(STEP.tick(new StepContext(mock(ShriekHost.class), 0, 0)));
    }
}
