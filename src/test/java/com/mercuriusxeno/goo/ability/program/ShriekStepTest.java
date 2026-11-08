package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The Shriek blob shrieks once, on its first tick, calling every hostile in
 * reach then; every later tick only counts toward its lifetime, and the step
 * ends once its lifetime has passed
 * (decision shriek-blob-cries-once-and-calls-earshot).
 */
class ShriekStepTest {

    private static final double REACH = 16;
    private static final int LASTS = 600;
    private static final ShriekStep STEP = new ShriekStep(Expr.literal(REACH), Expr.literal(LASTS));

    @Test
    void theFirstTickShrieksAcrossTheReach() {
        ShriekHost host = mock(ShriekHost.class);
        STEP.tick(new StepContext(host, 0, 0));
        verify(host).shriek(REACH);
    }

    @Test
    void noLaterTickShrieks() {
        ShriekHost host = mock(ShriekHost.class);
        for (int tick = 1; tick < LASTS; tick++) {
            STEP.tick(new StepContext(host, tick, tick));
        }
        verify(host, never()).shriek(anyDouble());
    }

    @Test
    void theStepEndsOnceItsLifetimeHasPassed() {
        ShriekHost host = mock(ShriekHost.class);
        assertFalse(STEP.tick(new StepContext(host, LASTS - 2, LASTS - 2)));
        assertTrue(STEP.tick(new StepContext(host, LASTS - 1, LASTS - 1)));
    }
}
