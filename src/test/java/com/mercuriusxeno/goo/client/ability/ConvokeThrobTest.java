package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ConvokeStep;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The convoke blob pulses on its step's period: it tries on each multiple,
 * swells at the pulse and settles by the next, and its echo grows and fades
 * over the period (decision convoke-blob-throbs-until-a-mob-arrives).
 */
class ConvokeThrobTest {

    private static final int PERIOD = 20;
    private static final float EPSILON = 1e-4f;

    @Nested
    class Tries {

        @Test
        void onEachMultipleOfThePeriod() {
            assertTrue(ConvokeStep.triesOn(40, PERIOD));
            assertFalse(ConvokeStep.triesOn(41, PERIOD));
        }

        @Test
        void everyTickForAPeriodOfOne() {
            assertTrue(ConvokeStep.triesOn(41, 1));
        }
    }

    @Nested
    class Throb {

        @Test
        void swellsAtThePulse() {
            assertEquals(1f + ConvokeThrob.SWELL, ConvokeThrob.throb(PERIOD, 40f), EPSILON);
        }

        @Test
        void settlesBeforeTheNext() {
            assertTrue(ConvokeThrob.throb(PERIOD, 59.9f) < 1f + EPSILON);
        }

        @Test
        void standsStillWithNoConvoke() {
            assertEquals(1f, ConvokeThrob.throb(0, 40f), EPSILON);
        }
    }

    @Nested
    class Echo {

        @Test
        void startsAtTheShellFullyDrawn() {
            assertEquals(1f, ConvokeThrob.echoGrowth(PERIOD, 40f), EPSILON);
            assertEquals(1f, ConvokeThrob.echoStrength(PERIOD, 40f), EPSILON);
        }

        @Test
        void growsAndFadesHalfwayThrough() {
            assertEquals(2f, ConvokeThrob.echoGrowth(PERIOD, 50f), EPSILON);
            assertEquals(0.5f, ConvokeThrob.echoStrength(PERIOD, 50f), EPSILON);
        }
    }
}
