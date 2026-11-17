package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Leech goo's splash count and Drain's trickle and burst: the trickle
 * sets off evenly from close around the drained mob's chest, a tight stream
 * (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
 */
class LeechWispsTest {

    /**
     * A living thing sheds a hex wisp while it stands in Leech goo each time
     * the shed interval hex_leech.json ships comes due on its tick count.
     * hex-wisps-drift-off-mobs-standing-in-leech-goo
     */
    @Nested
    class Shed {

        private static final int INTERVAL = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
                .orElseThrow().wispShedTicks();
        /** A tick count the interval comes due on, a few intervals into the thing's life. */
        private static final int DUE_TICK = 3 * INTERVAL;

        @Test
        void aMobInTheGooShedsOnADueTick() {
            assertTrue(LeechWisps.sheds(true, DUE_TICK, INTERVAL));
        }

        @Test
        void theSameMobShedsNoneOnATickBetween() {
            assertFalse(LeechWisps.sheds(true, DUE_TICK + 1, INTERVAL));
        }

        @Test
        void aMobOutsideTheGooShedsNoneOnADueTick() {
            assertFalse(LeechWisps.sheds(false, DUE_TICK, INTERVAL));
        }
    }

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
