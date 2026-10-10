package com.mercuriusxeno.goo.client.ber.style;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pulse prism rests a dull red, glows a brighter red while energized and
 * strobes toward white on each beat (decisions
 * metronome-prism-pulses-at-the-learned-rate, relay-prism-carries-the-signal-through-air).
 */
class PulsePrismStyleTest {

    @Test
    void anEnergizedPrismGlowsADifferentRedThanAResting() {
        assertEquals(PulsePrismStyle.RESTING_RED, PulsePrismStyle.tintFor(false, 0));
        assertEquals(PulsePrismStyle.ENERGIZED_RED, PulsePrismStyle.tintFor(true, 0));
        assertNotEquals(PulsePrismStyle.tintFor(false, 0), PulsePrismStyle.tintFor(true, 0));
    }

    @Test
    void aBeatStrobesWhiteThenFadesBack() {
        assertEquals(1.0, PulsePrismStyle.strobeShare(0), 1e-9);
        assertEquals(PulsePrismStyle.STROBE_WHITE, PulsePrismStyle.tintFor(true, 1));
        assertTrue(PulsePrismStyle.strobeShare(PulsePrismStyle.STROBE_SECONDS / 2) < 1);
        assertEquals(0.0, PulsePrismStyle.strobeShare(PulsePrismStyle.STROBE_SECONDS), 1e-9);
    }

    @Test
    void noBeatYetStrobesNothing() {
        assertEquals(0.0, PulsePrismStyle.strobeShare(Double.MAX_VALUE), 1e-9);
    }
}
