package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeking gnat leaves the glove along the line the player fires, flies
 * straight down it for its first ticks, then veers tick over tick toward its
 * prey off the aim and closes on it, so it reads as homing rather than
 * auto-aim (decision decay-gnats-degrade-each-block-once). The flight is
 * stepped here as the particle steps it: steer by the gnat's age, then move
 * by the velocity.
 */
class GnatSteeringTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = Vec3.ZERO;
    private static final Vec3 AIM = new Vec3(1, 0, 0);
    /** Four blocks out along the aim and two to its side, about 27 degrees off. */
    private static final Vec3 PREY = new Vec3(4, 0, 2);
    /** The widest a gnat launches off the firing line. */
    private static final double WIDEST_LAUNCH_DEGREES = 5;
    private static final int TURNING_TICKS = 4;
    private static final int FLIGHT_TICKS = 40;
    /** Within this of the prey's center a gnat has reached the mob it bites. */
    private static final double ON_THE_PREY = 0.5;
    /** The first veering tick's turn stays under this, so the path bends rather than snapping onto the prey. */
    private static final double MOST_FIRST_TURN_DEGREES = 20;

    @Nested
    class Launch {

        @Test
        void theWidestLaunchLeavesFiveDegreesOffTheFiringLine() {
            assertEquals(WIDEST_LAUNCH_DEGREES, degreesBetween(AIM, GnatSteering.launch(AIM, 1, 0)), DELTA);
        }

        @Test
        void aGnatLeavesAtItsCruisingSpeed() {
            assertEquals(GnatSteering.CRUISE, GnatSteering.launch(AIM, 1, 0).length(), DELTA);
        }
    }

    @Nested
    class Seeking {

        private final Vec3 downTheLine = GnatSteering.launch(AIM, 0, 0);

        @Test
        void aGnatFliesStraightDownTheFiringLineForItsFirstTicks() {
            Vec3 at = GLOVE;
            Vec3 velocity = downTheLine;
            for (int age = 0; age < GnatSteering.STRAIGHT_TICKS; age++) {
                velocity = GnatSteering.steer(velocity, at, PREY, age);
                at = at.add(velocity);
                assertEquals(0, degreesBetween(AIM, velocity), DELTA, "age " + age + " turned off the line");
            }
        }

        @Test
        void thenItVeersTowardItsPreyTickOverTick() {
            Vec3 at = downTheLine.scale(GnatSteering.STRAIGHT_TICKS);
            Vec3 velocity = downTheLine;
            double offBy = degreesBetween(velocity, PREY.subtract(at));
            for (int age = GnatSteering.STRAIGHT_TICKS; age < GnatSteering.STRAIGHT_TICKS + TURNING_TICKS; age++) {
                velocity = GnatSteering.steer(velocity, at, PREY, age);
                at = at.add(velocity);
                double now = degreesBetween(velocity, PREY.subtract(at));
                assertTrue(now < offBy, "age " + age + " heads " + now + " degrees off the prey, was " + offBy);
                offBy = now;
            }
        }

        @Test
        void theFirstVeerBendsThePathRatherThanSnappingOntoThePrey() {
            Vec3 at = downTheLine.scale(GnatSteering.STRAIGHT_TICKS);
            Vec3 turned = GnatSteering.steer(downTheLine, at, PREY, GnatSteering.STRAIGHT_TICKS);
            double turn = degreesBetween(downTheLine, turned);
            assertTrue(turn > 0 && turn < MOST_FIRST_TURN_DEGREES, "the first veer turned " + turn + " degrees");
        }

        @Test
        void aGnatClosesOnItsPrey() {
            Vec3 at = GLOVE;
            Vec3 velocity = downTheLine;
            for (int age = 0; age < FLIGHT_TICKS; age++) {
                velocity = GnatSteering.steer(velocity, at, PREY, age);
                at = at.add(velocity);
            }
            assertTrue(at.distanceTo(PREY) < ON_THE_PREY, "after the flight the gnat rests at " + at);
        }
    }

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.min(1, a.normalize().dot(b.normalize()))));
    }
}
