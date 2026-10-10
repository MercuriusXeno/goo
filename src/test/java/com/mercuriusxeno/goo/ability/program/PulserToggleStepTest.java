package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pulser toggles on the hold's first tick and every period after, without
 * end (decision pulser-toggles-rapidly-while-held).
 */
class PulserToggleStepTest {

    @Test
    void theFirstHeldTickToggles() {
        assertTrue(PulserToggleStep.togglesOn(1, 4));
    }

    @Test
    void togglesRepeatOnceEachPeriodWithoutEnd() {
        assertEquals(10, IntStream.rangeClosed(1, 40).filter(held -> PulserToggleStep.togglesOn(held, 4)).count());
        assertTrue(PulserToggleStep.togglesOn(4001, 4));
    }

    @Test
    void theTicksBetweenTogglesToggleNothing() {
        assertFalse(PulserToggleStep.togglesOn(2, 4));
        assertFalse(PulserToggleStep.togglesOn(4, 4));
        assertTrue(PulserToggleStep.togglesOn(5, 4));
    }

    @Test
    void aPeriodBelowOneTogglesEveryTick() {
        assertTrue(PulserToggleStep.togglesOn(2, 0));
        assertTrue(PulserToggleStep.togglesOn(3, 1));
    }

    @Test
    void noHoldTogglesNothing() {
        assertFalse(PulserToggleStep.togglesOn(0, 4));
    }
}
