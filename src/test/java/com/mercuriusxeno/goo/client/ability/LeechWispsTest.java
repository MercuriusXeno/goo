package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Lifetap's splash count and Drain's trickle and burst: the trickle
 * sets off evenly from close around the drained mob's chest, a tight stream
 * (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
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
        private static final int DRAWS = 2000;

        @Test
        void aStrikeStreamsMoreWispsThanBefore() {
            assertTrue(LeechWisps.WISPS_PER_TRICKLE > FORMER_TRICKLE);
        }

        @Test
        void aStrikeBurstsWispsOnTheVictim() {
            assertTrue(LeechWisps.WISPS_PER_BURST > 0);
        }

        @Test
        void aTrickleSetsOffEvenlyAcrossTheStrike() {
            int previous = -1;
            for (int wisp = 0; wisp < LeechWisps.WISPS_PER_TRICKLE; wisp++) {
                int delay = LeechWisps.streamDelay(wisp);
                assertTrue(delay >= previous && delay - previous <= 1, "wisp " + wisp + " at " + delay);
                assertTrue(delay < LeechWisps.TRICKLE_SPREAD, "wisp " + wisp + " at " + delay);
                previous = delay;
            }
            assertEquals(LeechWisps.TRICKLE_SPREAD - 1, previous);
        }

        @Test
        void aTrickleLeavesCloseAroundTheChest() {
            Vec3 chest = new Vec3(3, 70.2, -8);
            RandomSource random = RandomSource.create(11L);
            for (int draw = 0; draw < DRAWS; draw++) {
                Vec3 at = LeechWisps.streamStart(chest, random);
                assertTrue(at.distanceTo(chest) <= LeechWisps.STREAM_SPREAD, at::toString);
            }
        }
    }
}
