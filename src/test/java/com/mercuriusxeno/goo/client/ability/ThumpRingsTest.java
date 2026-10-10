package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A thump's rings leave the blob small, expand and fade, and one pulse read
 * on many frames thumps once (decision thumper-blob-pulses-periodically-then-fades).
 */
class ThumpRingsTest {

    private static final double EPSILON = 1e-9;

    @Test
    void aRingExpandsFromTheBlobAndFadesAsItGoes() {
        assertEquals(ThumpRings.START_RADIUS, ThumpRings.radiusAt(0), EPSILON);
        assertEquals(ThumpRings.END_RADIUS, ThumpRings.radiusAt(1), EPSILON);
        assertEquals(1f, ThumpRings.opacity(0));
        assertTrue(ThumpRings.opacity(0.6) < ThumpRings.opacity(0.2));
        assertEquals(0f, ThumpRings.opacity(1));
    }

    @Test
    void aTrailingRingIsUnseenBeforeItStarts() {
        assertEquals(0f, ThumpRings.opacity(-0.1));
    }

    @Test
    void onePulseReadOnManyFramesThumpsOnce() {
        assertFalse(ThumpRings.startsAnew(0.05));
        assertTrue(ThumpRings.startsAnew(ThumpRings.LIFETIME_SECONDS));
    }
}
