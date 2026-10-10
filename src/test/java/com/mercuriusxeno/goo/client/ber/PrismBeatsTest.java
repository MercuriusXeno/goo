package com.mercuriusxeno.goo.client.ber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A prism's beat is the frame its power starts, however many frames the
 * power stands (decision metronome-prism-pulses-at-the-learned-rate).
 */
class PrismBeatsTest {

    private static final double EPSILON = 1e-9;

    @Test
    void powerStartingIsABeat() {
        PrismBeats.Seen seen = PrismBeats.Seen.NEVER.next(true, 10);
        assertEquals(0, seen.since(10), EPSILON);
        assertEquals(0.5, seen.since(10.5), EPSILON);
    }

    @Test
    void powerStandingOverFramesBeatsOnce() {
        PrismBeats.Seen seen = PrismBeats.Seen.NEVER.next(true, 10).next(true, 10.02).next(true, 10.04);
        assertEquals(0.04, seen.since(10.04), EPSILON);
    }

    @Test
    void theNextPowerAfterAGapIsTheNextBeat() {
        PrismBeats.Seen seen = PrismBeats.Seen.NEVER.next(true, 10).next(false, 10.1).next(true, 11);
        assertEquals(0, seen.since(11), EPSILON);
    }

    @Test
    void aPrismNeverPoweredHasNoBeat() {
        assertEquals(Double.MAX_VALUE, PrismBeats.Seen.NEVER.next(false, 10).since(10), EPSILON);
    }
}
