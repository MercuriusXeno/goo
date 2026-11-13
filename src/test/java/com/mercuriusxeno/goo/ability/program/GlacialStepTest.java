package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glacial prism pulses its frost ring as its combo starts and every few
 * seconds after, and not between; its freeze pass, which puts out the fire
 * in its radius, runs as its combo starts and every second after.
 */
class GlacialStepTest {

    private static final int TICKS_A_SECOND = 20;

    @Test
    void theRingPulsesAsTheComboStarts() {
        assertTrue(GlacialStep.pulsesOn(0));
    }

    @Test
    void theRingPulsesEveryFewSeconds() {
        assertTrue(GlacialStep.pulsesOn(GlacialStep.PULSE_EVERY_TICKS));
        assertTrue(GlacialStep.pulsesOn(2 * GlacialStep.PULSE_EVERY_TICKS));
    }

    @Test
    void theRingRestsBetweenPulses() {
        assertFalse(GlacialStep.pulsesOn(1));
        assertFalse(GlacialStep.pulsesOn(GlacialStep.PULSE_EVERY_TICKS - 1));
    }

    // decision frost-reach-extinguishes-fire-and-burning-mobs
    @Test
    void theFreezePassRunsAsTheComboStarts() {
        assertTrue(GlacialStep.freezesOn(0));
    }

    // decision frost-reach-extinguishes-fire-and-burning-mobs
    @Test
    void theFreezePassRunsEverySecond() {
        assertTrue(GlacialStep.freezesOn(TICKS_A_SECOND));
        assertTrue(GlacialStep.freezesOn(2 * TICKS_A_SECOND));
    }
}
