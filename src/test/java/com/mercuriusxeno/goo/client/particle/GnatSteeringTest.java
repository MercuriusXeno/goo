package com.mercuriusxeno.goo.client.particle;

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
            assertEquals(WIDEST_LAUNCH_DEGREES, degreesBetween(AIM, GnatSteering.launch(AIM, FAN_DEGREES, 1, 0)), DELTA);
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

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.min(1, a.normalize().dot(b.normalize()))));
    }
}
