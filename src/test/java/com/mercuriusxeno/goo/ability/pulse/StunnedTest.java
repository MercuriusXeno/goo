package com.mercuriusxeno.goo.ability.pulse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a Zap stun's wake: a fresh stun remembers the AI it found, a
 * second stun keeps the first one's memory and the later wake, and the wake
 * falls on its game time.
 * zap-ticks-the-device-and-stuns
 */
class StunnedTest {

    private static final long FIRST_WAKE = 160L;
    private static final long EARLIER_WAKE = 120L;
    private static final long LATER_WAKE = 200L;

    @Nested
    class Renewed {

        @Test
        void aFreshStunRemembersTheAiItFound() {
            assertEquals(new Stunned(FIRST_WAKE, true), Stunned.renewed(null, FIRST_WAKE, true));
        }

        @Test
        void aSecondStunKeepsTheFirstStunsMemoryOfTheAi() {
            Stunned first = new Stunned(FIRST_WAKE, false);
            assertFalse(Stunned.renewed(first, LATER_WAKE, true).wasNoAi());
        }

        @Test
        void aSecondStunKeepsTheLaterWake() {
            Stunned first = new Stunned(FIRST_WAKE, false);
            assertEquals(LATER_WAKE, Stunned.renewed(first, LATER_WAKE, true).wakesAt());
            assertEquals(FIRST_WAKE, Stunned.renewed(first, EARLIER_WAKE, true).wakesAt());
        }
    }

    @Nested
    class WokeBy {

        @Test
        void theStunHoldsUntilItsWake() {
            assertFalse(new Stunned(FIRST_WAKE, false).wokeBy(FIRST_WAKE - 1));
        }

        @Test
        void theStunEndsAtItsWake() {
            assertTrue(new Stunned(FIRST_WAKE, false).wokeBy(FIRST_WAKE));
        }
    }
}
