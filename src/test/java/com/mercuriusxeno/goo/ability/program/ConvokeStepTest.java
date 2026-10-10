package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A convoke tries once each period its JSON names
 * (decision convoke-blob-throbs-until-a-mob-arrives).
 */
class ConvokeStepTest {

    private static final int PERIOD = 20;

    @Test
    void triesOnEachMultipleOfThePeriod() {
        assertTrue(ConvokeStep.triesOn(40, PERIOD));
        assertFalse(ConvokeStep.triesOn(41, PERIOD));
    }

    @Test
    void triesEveryTickForAPeriodOfOne() {
        assertTrue(ConvokeStep.triesOn(41, 1));
    }
}
