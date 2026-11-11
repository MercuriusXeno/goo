package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.LeechPayload;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Lifetap's splash count, Drain's trickle and burst counts, and the
 * tether a Drain trickle stands from its victim to the caster until a strike
 * interval passes with no further payload.
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

    @Nested
    class Tethers {

        private static final int VICTIM = 7;
        private static final int CASTER = 3;
        private static final long STRUCK_AT = 100L;

        private final DrainTethers tethers = new DrainTethers();

        private LeechPayload trickle() {
            return new LeechPayload(VICTIM, CASTER, 1f, false);
        }

        @Test
        void aTrickledVictimIsTetheredUntilItsLapse() {
            tethers.record(trickle(), STRUCK_AT);
            long lapse = STRUCK_AT + DrainTethers.TETHER_LAPSE_TICKS;
            assertTrue(tethers.holds(VICTIM, lapse - 1));
            assertFalse(tethers.holds(VICTIM, lapse));
        }

        @Test
        void aSecondPayloadRenewsTheTether() {
            tethers.record(trickle(), STRUCK_AT);
            long renewedAt = STRUCK_AT + DrainTethers.TETHER_LAPSE_TICKS - 1;
            tethers.record(trickle(), renewedAt);
            assertTrue(tethers.holds(VICTIM, renewedAt + DrainTethers.TETHER_LAPSE_TICKS - 1));
        }

        @Test
        void aLifetapSplashStandsNoTether() {
            tethers.record(new LeechPayload(VICTIM, CASTER, 1f, true), STRUCK_AT);
            assertFalse(tethers.holds(VICTIM, STRUCK_AT));
        }
    }
}
