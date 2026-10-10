package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thumper gives power on its first tick and once each period after, one
 * tick at a time (decision thumper-blob-pulses-periodically-then-fades).
 */
class EmitPowerStepTest {

    @Test
    void theFirstTickPulses() {
        assertTrue(EmitPowerStep.pulsesOn(0, 40));
    }

    @Test
    void eachPulseIsOneTickOncePerPeriod() {
        assertFalse(EmitPowerStep.pulsesOn(1, 40));
        assertFalse(EmitPowerStep.pulsesOn(39, 40));
        assertTrue(EmitPowerStep.pulsesOn(40, 40));
        assertEquals(10, IntStream.range(0, 400).filter(tick -> EmitPowerStep.pulsesOn(tick, 40)).count());
    }

    @Test
    void aPeriodBelowOnePulsesEveryTick() {
        assertTrue(EmitPowerStep.pulsesOn(3, 0));
    }
}
