package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Lifetap's splash count and Drain's trickle and burst counts.
 */
class LeechWispsTest {

    @Nested
    class Splashes {

        @Test
        void eachPointHealedSplashesOnce() {
            assertEquals(3, LeechWisps.splashesFor(3.2f));
        }

        @Test
        void aSliverOfHealingStillSplashes() {
            assertEquals(1, LeechWisps.splashesFor(0.2f));
        }

        @Test
        void aHugeHealSplashesUpToTheCap() {
            assertEquals(LeechWisps.MOST_SPLASHES, LeechWisps.splashesFor(40f));
        }
    }

    @Nested
    class Trickle {

        /** The wisps a trickle launched before Drain's stream thickened. */
        private static final int FORMER_TRICKLE = 5;

        @Test
        void aStrikeStreamsMoreWispsThanBefore() {
            assertTrue(LeechWisps.WISPS_PER_TRICKLE > FORMER_TRICKLE);
        }

        @Test
        void aStrikeBurstsWispsOnTheVictim() {
            assertTrue(LeechWisps.WISPS_PER_BURST > 0);
        }
    }
}
