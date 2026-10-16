package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A break particle flies straight to the hand: the block bursts, its
 * particles kicking outward from it and settling back into the cloud, each
 * waits there until its turn in the drain comes, then flies along the line
 * from where it stands to the glove at the stream's base pace, and is done at
 * the glove; the cloud is empty as the drain ends
 * (decision unmake-drink-is-a-stream-of-break-particles).
 */
class DrinkRideTest {

    private static final double ON_THE_LINE = 1e-6;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long LANDS = 100;
    private static final long DRAINED = 140;
    private static final long PICKED = LANDS - SiphonRule.INJECT_TICKS;
    private static final double TICK = 1;
    private static final int MOTES = 48;
    private static final long SEED = 7L;
    private static final BlockPos POS = new BlockPos(7, 2, 3);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(POS, Vec3.atCenterOf(POS), 1, PICKED, LANDS,
            DRAINED);
    /** A particle whose turn comes once its pop has settled, so its flight leaves from its place in the cloud. */
    private static final DrinkRide.Mote SETTLED = new DrinkRide.Mote(new Vec3(7.2, 2.7, 3.4), new Vec3(-0.3, 0.2, 0.1),
            LANDS, LANDS + DrinkRide.POP_TICKS + DrinkRide.SETTLE_TICKS + TICK);

    private static List<DrinkRide.Mote> motes() {
        return DrinkRide.motesOf(BLOCK, MOTES, RandomSource.create(SEED));
    }

    @Nested
    class Flight {

        @Test
        void aParticleLiesOnTheLineFromItsCloudPlaceToTheGloveAtThePacesDistance() {
            DrinkRide.Mote mote = SETTLED;
            double span = mote.cloud().distanceTo(GLOVE);
            int steps = 0;
            for (double now = mote.release(); now < mote.release() + span / DrinkStream.FLOW; now += TICK) {
                Vec3 at = DrinkRide.pointOf(mote, GLOVE, now);
                assertNotNull(at, "a particle short of the glove at " + now);
                double flown = DrinkStream.FLOW * (now - mote.release());
                assertEquals(flown, mote.cloud().distanceTo(at), ON_THE_LINE);
                assertEquals(span - flown, at.distanceTo(GLOVE), ON_THE_LINE);
                steps++;
            }
            assertTrue(steps > 1, "the flight took " + steps + " steps");
        }

        @Test
        void aParticleFollowsTheGloveAsItMoves() {
            DrinkRide.Mote mote = SETTLED;
            Vec3 moved = GLOVE.add(0, 1, 0);
            double now = mote.release() + TICK;
            Vec3 at = DrinkRide.pointOf(mote, moved, now);
            assertNotNull(at);
            double span = mote.cloud().distanceTo(moved);
            assertEquals(span, mote.cloud().distanceTo(at) + at.distanceTo(moved), ON_THE_LINE);
        }

        @Test
        void aParticleIsDoneOnceItsDistanceReachesTheGlove() {
            DrinkRide.Mote mote = SETTLED;
            double span = mote.cloud().distanceTo(GLOVE);
            assertFalse(DrinkRide.done(mote.cloud(), GLOVE, span - ON_THE_LINE * 2));
            assertTrue(DrinkRide.done(mote.cloud(), GLOVE, span));
            assertNull(DrinkRide.pointOf(mote, GLOVE, mote.release() + span / DrinkStream.FLOW + TICK));
        }
    }

    @Nested
    class Burst {

        @Test
        void aWaitingParticleKicksOutwardFromTheBlockAndSettlesBackIntoTheCloud() {
            for (DrinkRide.Mote mote : motes()) {
                DrinkRide.Mote waiting = new DrinkRide.Mote(mote.cloud(), mote.kick(), LANDS, DRAINED);
                double home = mote.cloud().distanceTo(BLOCK.center());
                Vec3 popped = DrinkRide.pointOf(waiting, GLOVE, LANDS + DrinkRide.POP_TICKS);
                assertNotNull(popped);
                assertTrue(popped.distanceTo(BLOCK.center()) > home + ON_THE_LINE,
                        "a particle at " + popped + " did not kick out from " + mote.cloud());
                Vec3 settled = DrinkRide.pointOf(waiting, GLOVE, LANDS + DrinkRide.POP_TICKS
                        + DrinkRide.SETTLE_TICKS);
                assertEquals(mote.cloud(), settled);
            }
        }
    }

    @Nested
    class Cloud {

        @Test
        void everyParticleWaitsAtTheBlockBeforeTheSquareLandsAndHasLeftItAsTheDrainEnds() {
            List<DrinkRide.Mote> motes = motes();
            assertEquals(MOTES, motes.size());
            for (DrinkRide.Mote mote : motes) {
                assertEquals(0, DrinkRide.distanceOf(mote.release(), LANDS - TICK));
                assertEquals(mote.cloud(), DrinkRide.pointOf(mote, GLOVE, LANDS - TICK));
                double drained = DrinkRide.distanceOf(mote.release(), DRAINED);
                assertTrue(drained > 0, "a particle released at " + mote.release() + " still at the block");
            }
        }
    }
}
