package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeking gnat leaves the glove slow and sprayed wide around the aim, then
 * speeds up and turns its velocity toward its prey tick over tick, bending its
 * path rather than snapping onto the prey, and closes on it (decision
 * decay-gnats-degrade-each-block-once). The flight is stepped here as the
 * particle steps it: steer, then move by the velocity.
 */
class GnatSteeringTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = Vec3.ZERO;
    private static final Vec3 AIM = new Vec3(1, 0, 0);
    /** Four blocks out along the aim and two to its side, about 27 degrees off. */
    private static final Vec3 PREY = new Vec3(4, 0, 2);
    /** The widest a gnat launches off the aim, half the 90-degree spray. */
    private static final double WIDEST_LAUNCH_DEGREES = 45;
    /** Turned so the widest launch leans away from the prey, the longest way round. */
    private static final double AWAY_FROM_THE_PREY = 0.75;
    private static final int TURNING_TICKS = 4;
    private static final int SPEEDING_TICKS = 8;
    private static final int FLIGHT_TICKS = 40;
    /** Within this of the prey's center a gnat has reached the mob it bites. */
    private static final double ON_THE_PREY = 0.5;
    /** The first tick's turn stays under this, so the path bends rather than snapping onto the prey. */
    private static final double MOST_FIRST_TURN_DEGREES = 20;

    @Nested
    class Launch {

        @Test
        void theWidestLaunchLeavesFortyFiveDegreesOffTheAim() {
            Vec3 launch = GnatSteering.launch(AIM, 1, 0);
            assertEquals(WIDEST_LAUNCH_DEGREES, degreesBetween(AIM, launch), DELTA);
        }

        @Test
        void aLaunchLeavesSlowerThanAGnatCruises() {
            assertTrue(GnatSteering.launch(AIM, 1, 0).length() < GnatSteering.CRUISE);
        }
    }

    @Nested
    class Seeking {

        private final Vec3 wideLaunch = GnatSteering.launch(AIM, 1, AWAY_FROM_THE_PREY);

        @Test
        void aGnatOffTheAimTurnsTowardItsPreyTickOverTick() {
            Vec3 at = GLOVE;
            Vec3 velocity = wideLaunch;
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
        void theFirstTickBendsThePathRatherThanSnappingOntoThePrey() {
            Vec3 turned = GnatSteering.steer(wideLaunch, GLOVE, PREY);
            double turn = degreesBetween(wideLaunch, turned);
            assertTrue(turn > 0 && turn < MOST_FIRST_TURN_DEGREES, "the first tick turned " + turn + " degrees");
        }

        @Test
        void aGnatGainsSpeedAsItSeeks() {
            Vec3 at = GLOVE;
            Vec3 velocity = wideLaunch;
            double speed = velocity.length();
            for (int tick = 0; tick < SPEEDING_TICKS; tick++) {
                velocity = GnatSteering.steer(velocity, at, PREY);
                at = at.add(velocity);
                assertTrue(velocity.length() > speed, "tick " + tick + " flies " + velocity.length()
                        + " blocks a tick, was " + speed);
                speed = velocity.length();
            }
        }

        @Test
        void aGnatOffTheAimClosesOnItsPrey() {
            Vec3 at = GLOVE;
            Vec3 velocity = wideLaunch;
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
