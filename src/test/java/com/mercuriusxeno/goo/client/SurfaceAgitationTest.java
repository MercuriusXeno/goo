package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the surface agitation tracker: a moving level or a pouring stream
 * raises the ripple, a held level decays it back to rest, and the ceiling
 * bounds it.
 */
class SurfaceAgitationTest {

    private static final float REST = RenderContext.RESTING_RIPPLE_AMPLITUDE;
    private static final float CEILING = REST + SurfaceAgitation.AGITATION_CEILING;
    private static final int SETTLE_TICKS = 400;

    @Test
    void firstSightOfALevelAnswersRest() {
        assertEquals(REST, new SurfaceAgitation().tick(0.5f, 0f, 10L));
    }

    @Test
    void aLevelStepRaisesTheAmplitudeAboveRest() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0.2f, 0f, 1L);
        assertTrue(agitation.tick(0.21f, 0f, 2L) > REST);
    }

    @Test
    void aFallingLevelRaisesTheAmplitudeToo() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0.5f, 0f, 1L);
        assertTrue(agitation.tick(0.49f, 0f, 2L) > REST);
    }

    @Test
    void aPouringStreamRaisesTheAmplitudeAtAHeldLevel() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0.5f, 0f, 1L);
        assertTrue(agitation.tick(0.5f, 20f, 2L) > REST);
    }

    @Test
    void aHeldLevelDecaysStrictlyBackToRest() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0f, 0f, 0L);
        float previous = agitation.tick(0.3f, 0f, 1L);
        long tick = 2L;
        while (previous > REST && tick < SETTLE_TICKS) {
            float next = agitation.tick(0.3f, 0f, tick++);
            assertTrue(next < previous, "amplitude rose at tick " + tick);
            previous = next;
        }
        assertEquals(REST, previous);
    }

    @Test
    void aRunOfLargeStepsNeverExceedsTheCeiling() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        for (long tick = 0; tick < 100; tick++) {
            float fill = tick % 2 == 0 ? 0f : 1f;
            float amplitude = agitation.tick(fill, 10_000f, tick);
            assertTrue(amplitude <= CEILING, "amplitude " + amplitude + " at tick " + tick);
        }
        assertEquals(CEILING, agitation.amplitude(), 1e-6f);
    }

    @Test
    void framesWithinOneTickAnswerTheSameAmplitude() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0f, 0f, 1L);
        float first = agitation.tick(0.2f, 0f, 2L);
        assertEquals(first, agitation.tick(0.4f, 50f, 2L));
    }

    @Test
    void aLongGapSettlesTheSurface() {
        SurfaceAgitation agitation = new SurfaceAgitation();
        agitation.tick(0f, 0f, 1L);
        agitation.tick(0.5f, 0f, 2L);
        assertEquals(REST, agitation.tick(0.5f, 0f, 2L + SurfaceAgitation.MAX_CATCH_UP_TICKS));
    }
}
