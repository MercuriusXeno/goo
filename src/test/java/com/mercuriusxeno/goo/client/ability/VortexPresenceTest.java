package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drain's vortex forms out from the hand and thickens in when the hold
 * starts, and thins away rather than vanishing when it ends
 * (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
 */
class VortexPresenceTest {

    private static final double TOLERANCE = 1e-9;
    private static final double START = 100;

    @Test
    void nothingShowsBeforeAnyHold() {
        VortexPresence presence = new VortexPresence();
        assertEquals(0, presence.update(false, START).presence(), TOLERANCE);
    }

    @Test
    void aHoldThickensAndFormsOverItsFirstTicks() {
        VortexPresence presence = new VortexPresence();
        VortexPresence.Shown first = presence.update(true, START);
        VortexPresence.Shown midway = presence.update(true, START + VortexPresence.FADE_IN_TICKS / 2);
        VortexPresence.Shown settled = presence.update(true, START + VortexPresence.FADE_IN_TICKS);
        assertEquals(0, first.presence(), TOLERANCE);
        assertEquals(0, first.formed(), TOLERANCE);
        assertEquals(0.5, midway.presence(), TOLERANCE);
        assertTrue(midway.formed() > 0 && midway.formed() < 1, midway::toString);
        assertEquals(1, settled.presence(), TOLERANCE);
        assertEquals(1, settled.formed(), TOLERANCE);
    }

    @Test
    void aReleaseThinsAwayOverTheFadeOut() {
        VortexPresence presence = new VortexPresence();
        presence.update(true, START);
        double released = START + VortexPresence.FADE_IN_TICKS;
        presence.update(false, released);
        VortexPresence.Shown midway = presence.update(false, released + VortexPresence.FADE_OUT_TICKS / 2);
        VortexPresence.Shown gone = presence.update(false, released + VortexPresence.FADE_OUT_TICKS);
        assertEquals(0.5, midway.presence(), TOLERANCE);
        assertEquals(1, midway.formed(), TOLERANCE);
        assertEquals(0, gone.presence(), TOLERANCE);
    }

    @Test
    void aHoldResumedMidFadeCarriesOnFromWhereItThinnedTo() {
        VortexPresence presence = new VortexPresence();
        presence.update(true, START);
        double released = START + VortexPresence.FADE_IN_TICKS;
        presence.update(false, released);
        double resumed = released + VortexPresence.FADE_OUT_TICKS / 2;
        presence.update(false, resumed);
        assertEquals(0.5, presence.update(true, resumed).presence(), TOLERANCE);
    }
}
