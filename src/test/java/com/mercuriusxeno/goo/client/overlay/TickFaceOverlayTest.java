package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.client.overlay.TickFaceOverlay.MAX_RINGS_PER_TICK;
import static com.mercuriusxeno.goo.client.overlay.TickFaceOverlay.RINGS_PER_MACHINE_TICK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tick's squares march in step with the machine's ticks a game tick, its
 * own and the extra, capped at the rate the shader reads
 * (decision tick-channel-marches-squares-on-the-face).
 */
class TickFaceOverlayTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void noExtraTicksMarchesAtTheMachinesOwnPace() {
        assertEquals(RINGS_PER_MACHINE_TICK, TickFaceOverlay.marchRate(0), EPSILON);
    }

    @Test
    void eachExtraTickQuickensTheMarch() {
        assertEquals(5 * RINGS_PER_MACHINE_TICK, TickFaceOverlay.marchRate(4), EPSILON);
    }

    @Test
    void theMarchCapsAtTheShadersTopRate() {
        assertEquals(MAX_RINGS_PER_TICK, TickFaceOverlay.marchRate(1000), EPSILON);
    }
}
