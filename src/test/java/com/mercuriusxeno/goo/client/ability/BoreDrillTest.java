package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drawn vortex is held out in front: it starts ahead of the eye and a
 * little below the line of sight with a wide mouth, meets the line of sight
 * at Bore's reach, and at each end is as wide as the vortex Bore breaks at
 * that depth, narrowing to its far end and never wider than a block
 * (decisions bore-shows-a-big-drill-from-the-glove, bore-breaks-what-is-in-the-vortex).
 */
class BoreDrillTest {

    /** rock_bore.json's delivery range. */
    private static final double REACH = 4;
    /** rock_bore.json's vortex radii at its near and far ends. */
    private static final double NEAR = 0.45;
    private static final double FAR = 0.3;
    private static final double SLACK = 1e-9;
    private static final double HALF_A_BLOCK = 0.5;
    private static final Vec3 EYE = new Vec3(3.2, 70.62, -8.4);
    /** Yaw 0 looks along +Z, level. */
    private static final float YAW = 0f;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final int SAMPLES = 64;

    private static ConeSections.Volume vortex(double near, double far) {
        return BoreDrill.volume(EYE, LOOK, YAW, REACH, near, far);
    }

    private static Vec3 pointAlong(ConeSections.Volume volume, double distance) {
        return volume.apex().add(volume.axis().scale(distance));
    }

    /** The broken vortex's radius at a depth along the look, between the near and far radii. */
    private static double brokenRadiusAt(double depth, double near, double far) {
        return near + (far - near) * depth / REACH;
    }

    @Nested
    class Ends {

        @Test
        void itStartsAheadOfTheEyeAndBelowTheLineOfSight() {
            Vec3 start = pointAlong(vortex(NEAR, FAR), 0);
            assertEquals(BoreDrill.HELD_AHEAD, start.subtract(EYE).dot(LOOK), SLACK);
            assertEquals(EYE.y - BoreDrill.HELD_BELOW, start.y, SLACK);
        }

        @Test
        void itMeetsTheLineOfSightAtTheReach() {
            ConeSections.Volume vortex = vortex(NEAR, FAR);
            assertEquals(0, pointAlong(vortex, vortex.range()).distanceTo(EYE.add(LOOK.scale(REACH))), SLACK);
        }
    }

    @Nested
    class Radius {

        @Test
        void eachEndIsAsWideAsTheBrokenVortexAtItsDepth() {
            for (double[] radii : new double[][] {{NEAR, FAR}, {0.2, 0.3}}) {
                ConeSections.Volume vortex = vortex(radii[0], radii[1]);
                assertEquals(brokenRadiusAt(BoreDrill.HELD_AHEAD, radii[0], radii[1]),
                        vortex.radiusAt().applyAsDouble(0), SLACK);
                assertEquals(radii[1], vortex.radiusAt().applyAsDouble(vortex.range()), SLACK);
            }
        }

        @Test
        void itNarrowsFromItsMouthToItsFarEndAndIsNeverWiderThanABlock() {
            ConeSections.Volume vortex = vortex(NEAR, FAR);
            double previous = Double.MAX_VALUE;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double distance = vortex.range() * sample / SAMPLES;
                double radius = vortex.radiusAt().applyAsDouble(distance);
                assertTrue(radius <= previous, "the vortex widens to " + radius + " at " + distance);
                assertTrue(radius < HALF_A_BLOCK, "the vortex is " + 2 * radius + " wide at " + distance);
                previous = radius;
            }
        }
    }
}
