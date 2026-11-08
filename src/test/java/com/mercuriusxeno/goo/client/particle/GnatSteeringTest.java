package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A seeking gnat launched along the aim, with its prey off the aim, turns its
 * velocity toward the prey tick over tick, bending its path rather than
 * snapping onto the prey, and closes on it (decision
 * decay-gnats-degrade-each-block-once). The flight is stepped here as the
 * particle steps it: steer, then move by the velocity.
 */
class GnatSteeringTest {

    private static final Vec3 GLOVE = Vec3.ZERO;
    /** Four blocks out along the aim and two to its side, about 27 degrees off. */
    private static final Vec3 PREY = new Vec3(4, 0, 2);
    private static final Vec3 ALONG_THE_AIM = new Vec3(GnatSteering.CRUISE, 0, 0);
    private static final int TURNING_TICKS = 4;
    private static final int FLIGHT_TICKS = 40;
    /** Within this of the prey's center a gnat has reached the mob it bites. */
    private static final double ON_THE_PREY = 0.5;
    /** The first tick's turn stays under this, so the path bends rather than snapping onto the prey. */
    private static final double MOST_FIRST_TURN_DEGREES = 20;

    @Test
    void aGnatOffTheAimTurnsTowardItsPreyTickOverTick() {
        Vec3 at = GLOVE;
        Vec3 velocity = ALONG_THE_AIM;
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
        Vec3 turned = GnatSteering.steer(ALONG_THE_AIM, GLOVE, PREY);
        double turn = degreesBetween(ALONG_THE_AIM, turned);
        assertTrue(turn > 0 && turn < MOST_FIRST_TURN_DEGREES, "the first tick turned " + turn + " degrees");
    }

    @Test
    void aGnatOffTheAimClosesOnItsPrey() {
        Vec3 at = GLOVE;
        Vec3 velocity = ALONG_THE_AIM;
        for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
            velocity = GnatSteering.steer(velocity, at, PREY);
            at = at.add(velocity);
        }
        assertTrue(at.distanceTo(PREY) < ON_THE_PREY, "after the flight the gnat rests at " + at);
    }

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.min(1, a.normalize().dot(b.normalize()))));
    }
}
