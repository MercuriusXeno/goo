package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A beat's rings leave the prism small, expand and fade, and one pulse read
 * on many frames rings once (decision metronome-prism-pulses-at-the-learned-rate).
 */
class BeatRingsTest {

    private static final double EPSILON = 1e-9;

    @Test
    void aRingExpandsFromThePrismAndFadesAsItGoes() {
        assertEquals(BeatRings.START_RADIUS, BeatRings.radiusAt(0), EPSILON);
        assertEquals(BeatRings.END_RADIUS, BeatRings.radiusAt(1), EPSILON);
        assertEquals(1f, BeatRings.opacity(0));
        assertTrue(BeatRings.opacity(0.6) < BeatRings.opacity(0.2));
        assertEquals(0f, BeatRings.opacity(1));
    }

    @Test
    void aTrailingRingIsUnseenBeforeItStarts() {
        assertEquals(0f, BeatRings.opacity(-0.1));
    }

    @Test
    void onePulseReadOnManyFramesRingsOnce() {
        assertFalse(BeatRings.startsAnew(0.05));
        assertTrue(BeatRings.startsAnew(BeatRings.LIFETIME_SECONDS));
    }
}
