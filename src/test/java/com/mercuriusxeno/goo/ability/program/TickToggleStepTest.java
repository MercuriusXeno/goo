package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tick toggles on the hold's first tick and every period after, without
 * end (decision pulser-toggles-rapidly-while-held).
 */
class TickToggleStepTest {

    @Test
    void theFirstHeldTickToggles() {
        assertTrue(TickToggleStep.togglesOn(1, 4));
    }

    @Test
    void togglesRepeatOnceEachPeriodWithoutEnd() {
        assertEquals(10, IntStream.rangeClosed(1, 40).filter(held -> TickToggleStep.togglesOn(held, 4)).count());
        assertTrue(TickToggleStep.togglesOn(4001, 4));
    }

    @Test
    void theTicksBetweenTogglesToggleNothing() {
        assertFalse(TickToggleStep.togglesOn(2, 4));
        assertFalse(TickToggleStep.togglesOn(4, 4));
        assertTrue(TickToggleStep.togglesOn(5, 4));
    }

    @Test
    void aPeriodBelowOneTogglesEveryTick() {
        assertTrue(TickToggleStep.togglesOn(2, 0));
        assertTrue(TickToggleStep.togglesOn(3, 1));
    }

    @Test
    void noHoldTogglesNothing() {
        assertFalse(TickToggleStep.togglesOn(0, 4));
    }
}
