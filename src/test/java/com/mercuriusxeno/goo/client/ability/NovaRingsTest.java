package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nova's ring bursts out fast: it has spread to its reach within its few
 * spread ticks, far sooner than frost's burnout drifts out, then fades.
 */
class NovaRingsTest {

    private static final float EPSILON = 1e-6f;
    private static final float SPREAD_DONE = (float) NovaRings.SPREAD_TICKS / NovaRings.DURATION_TICKS;

    @Test
    void theRingHasSpreadWithinItsSpreadTicks() {
        assertEquals(1f, NovaRings.spread(SPREAD_DONE), EPSILON);
        assertTrue(NovaRings.SPREAD_TICKS < FrostExplosionVisual.SPREAD_TICKS);
    }

    @Test
    void theFogHoldsWhileTheRingSpreadsThenFades() {
        assertEquals(1f, NovaRings.fog(SPREAD_DONE), EPSILON);
        assertEquals(0f, NovaRings.fog(1f), EPSILON);
    }
}
