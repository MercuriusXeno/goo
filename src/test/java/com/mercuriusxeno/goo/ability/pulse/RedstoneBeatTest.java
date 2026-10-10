package com.mercuriusxeno.goo.ability.pulse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A metronome learns its interval from the last two signals it starts
 * hearing, and two new signals set another
 * (decision metronome-prism-pulses-at-the-learned-rate).
 */
class RedstoneBeatTest {

    private static RedstoneBeat pulled(RedstoneBeat beat, long on, long off) {
        return beat.hear(true, on).hear(false, off);
    }

    @Test
    void oneSignalLearnsNothing() {
        RedstoneBeat beat = pulled(RedstoneBeat.SILENT, 100, 104);
        assertFalse(beat.learned());
        assertFalse(beat.pulsesAt(120));
    }

    @Test
    void twoSignalsSetTheIntervalCountedFromTheLast() {
        RedstoneBeat beat = pulled(pulled(RedstoneBeat.SILENT, 100, 104), 120, 124);
        assertEquals(20, beat.interval());
        assertFalse(beat.pulsesAt(120));
        assertFalse(beat.pulsesAt(130));
        assertTrue(beat.pulsesAt(140));
        assertTrue(beat.pulsesAt(160));
    }

    @Test
    void aHeldSignalIsOneSignal() {
        RedstoneBeat on = RedstoneBeat.SILENT.hear(true, 100);
        assertSame(on, on.hear(true, 110));
    }

    @Test
    void twoNewSignalsSetAnotherInterval() {
        RedstoneBeat twenty = pulled(pulled(RedstoneBeat.SILENT, 100, 104), 120, 124);
        RedstoneBeat ten = pulled(pulled(twenty, 200, 203), 210, 213);
        assertEquals(10, ten.interval());
        assertTrue(ten.pulsesAt(220));
        assertFalse(ten.pulsesAt(225));
    }
}
