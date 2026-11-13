package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.SwarmMotes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeking gnat leaves the glove fanned up to 45 degrees off the line the
 * player fires and is pulled toward its prey every tick, its momentum carrying it on along the
 * aim, so its path slides off the aim and curves into the prey rather than
 * snapping onto it, and it closes on the prey (decision
 * decay-gnats-degrade-each-block-once). The flight is stepped here as the
 * particle steps it: steer, then move by the velocity.
 */
class GnatSteeringTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = Vec3.ZERO;
    private static final Vec3 AIM = new Vec3(1, 0, 0);
    /** Four blocks out along the aim and two to its side, about 27 degrees off. */
    private static final Vec3 PREY = new Vec3(4, 0, 2);
    /** Decay's cone, apex to rim, which the swarm fans across. */
    private static final double FAN_DEGREES = 90;
    /** The widest a gnat launches off the firing line, half the fan. */
    private static final double WIDEST_LAUNCH_DEGREES = 45;
    private static final int TURNING_TICKS = 4;
    private static final int FLIGHT_TICKS = 40;
    /** Within this of the prey's center a gnat has reached the mob it bites. */
    private static final double ON_THE_PREY = 0.5;

    @Nested
    class Launch {

        @Test
        void theWidestLaunchLeavesFortyFiveDegreesOffTheFiringLine() {
            assertEquals(WIDEST_LAUNCH_DEGREES,
                    degreesBetween(AIM, GnatSteering.launch(AIM, FAN_DEGREES, 1, 0)), DELTA);
        }

        @Test
        void aGnatLeavesAtItsCruisingSpeed() {
            assertEquals(GnatSteering.CRUISE, GnatSteering.launch(AIM, FAN_DEGREES, 1, 0).length(), DELTA);
        }
    }

    @Nested
    class Seeking {

        private final Vec3 downTheLine = GnatSteering.launch(AIM, FAN_DEGREES, 0, 0);

        @Test
        void thePreyPullsTheGnatFromItsFirstTick() {
            Vec3 pulled = GnatSteering.steer(downTheLine, GLOVE, PREY);
            assertTrue(degreesBetween(pulled, PREY) < degreesBetween(downTheLine, PREY),
                    "the first tick left the gnat heading " + degreesBetween(pulled, PREY) + " degrees off the prey");
        }

        @Test
        void itsMomentumCarriesItOnAlongTheAimAtFirst() {
            Vec3 pulled = GnatSteering.steer(downTheLine, GLOVE, PREY);
            assertTrue(degreesBetween(pulled, AIM) < degreesBetween(pulled, PREY),
                    "after one tick the gnat heads " + degreesBetween(pulled, AIM) + " degrees off the aim and "
                            + degreesBetween(pulled, PREY) + " off the prey");
        }

        @Test
        void itCurvesTowardItsPreyTickOverTick() {
            Vec3 at = GLOVE;
            Vec3 velocity = downTheLine;
            double offBy = degreesBetween(velocity, PREY.subtract(at));
            for (int tick = 0; tick < TURNING_TICKS; tick++) {
                velocity = GnatSteering.steer(velocity, at, PREY);
                at = at.add(velocity);
                double now = degreesBetween(velocity, PREY.subtract(at));
                assertTrue(now < offBy, "tick " + tick + " heads " + now + " degrees off the prey, was " + offBy);
                offBy = now;
            }
        }

        @Test
        void aGnatClosesOnItsPrey() {
            Vec3 at = GLOVE;
            Vec3 velocity = downTheLine;
            for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
                velocity = GnatSteering.steer(velocity, at, PREY);
                at = at.add(velocity);
            }
            assertTrue(at.distanceTo(PREY) < ON_THE_PREY, "after the flight the gnat rests at " + at);
        }
    }

    /**
     * The swarm converges on a block on the floor: even a gnat fanned the
     * full 45 degrees down off the aim, toward the floor, is turned onto the
     * block before it strikes the floor more than a block short, from a glove
     * held 1 to 1.6 blocks over the floor at 2 to 6 blocks out.
     */
    @Nested
    class ConvergingOnAFloorBlock {

        private static final double[] DISTANCES = {2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6};
        private static final double[] GLOVE_HEIGHTS = {1.0, 1.3, 1.6};
        /** The farthest short of its target the worst-launched gnat may strike the floor. */
        private static final double WIDEST_MISS = 1.0;
        private static final double HALF_A_BLOCK = 0.5;
        private static final int MOST_TICKS = 60;

        @Test
        void aGnatFannedDownTowardTheFloorStillLandsWithinABlockOfItsTarget() {
            for (double distance : DISTANCES) {
                for (double height : GLOVE_HEIGHTS) {
                    Vec3 glove = new Vec3(0, height, 0);
                    Vec3 target = new Vec3(distance, 0, 0);
                    Vec3 velocity = fannedDown(target.subtract(glove));
                    Vec3 at = glove;
                    for (int tick = 0; tick < MOST_TICKS && at.y > 0; tick++) {
                        velocity = GnatSteering.steer(velocity, at, target);
                        at = at.add(velocity);
                    }
                    double short_ = distance - at.x;
                    assertTrue(short_ <= WIDEST_MISS, "from " + height + " up, " + distance + " out, the gnat struck the"
                            + " floor " + short_ + " blocks short");
                }
            }
        }

        @Test
        void theBiteWaitsNoLessThanTheGnatsTakeToArrive() {
            for (double distance : DISTANCES) {
                Vec3 glove = new Vec3(0, 1.3, 0);
                Vec3 target = new Vec3(distance, 0, 0);
                Vec3 velocity = GnatSteering.launch(target.subtract(glove), FAN_DEGREES, 0, 0);
                Vec3 at = glove;
                int tick = 0;
                while (at.distanceTo(target) >= HALF_A_BLOCK && tick < MOST_TICKS) {
                    velocity = GnatSteering.steer(velocity, at, target);
                    at = at.add(velocity);
                    tick++;
                }
                int wait = (int) Math.ceil(glove.distanceTo(target) / SwarmMotes.GNAT_SPEED);
                assertTrue(wait >= tick, distance + " out, the gnats take " + tick + " ticks, the bite waits " + wait);
            }
        }

        /**
         * A launch at the fan's rim straight down off the aim.
         *
         * @param aim the heading from the glove onto the target
         * @return the launch velocity
         */
        private Vec3 fannedDown(Vec3 aim) {
            Vec3 forward = aim.normalize();
            Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 down = side.cross(forward).normalize().scale(-1);
            double tilt = Math.toRadians(FAN_DEGREES / 2);
            return forward.scale(Math.cos(tilt)).add(down.scale(Math.sin(tilt))).normalize().scale(GnatSteering.CRUISE);
        }
    }

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.min(1, a.normalize().dot(b.normalize()))));
    }
}
