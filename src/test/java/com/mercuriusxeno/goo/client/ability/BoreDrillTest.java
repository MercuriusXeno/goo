package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drawn vortex is held out in front: it starts ahead of the eye and a
 * little below the line of sight with a wide mouth, meets the line of sight
 * at Bore's reach, and narrows to the point radius Bore breaks with at its
 * far end, never wider than a block
 * (decisions bore-shows-a-big-drill-from-the-glove, bore-breaks-what-is-in-the-vortex).
 */
class BoreDrillTest {

    /** rock_bore.json's delivery range. */
    private static final double REACH = 4;
    /** rock_bore.json's point radius. */
    private static final double POINT_RADIUS = 0.1;
    private static final double SLACK = 1e-9;
    private static final double HALF_A_BLOCK = 0.5;
    private static final Vec3 EYE = new Vec3(3.2, 70.62, -8.4);
    /** Yaw 0 looks along +Z, level. */
    private static final float YAW = 0f;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final int SAMPLES = 64;

    private static ConeSections.Volume vortex(double pointRadius) {
        return BoreDrill.volume(EYE, LOOK, YAW, REACH, pointRadius);
    }

    private static Vec3 pointAlong(ConeSections.Volume volume, double distance) {
        return volume.apex().add(volume.axis().scale(distance));
    }

    @Nested
    class Ends {

        @Test
        void itStartsAheadOfTheEyeAndBelowTheLineOfSight() {
            Vec3 start = pointAlong(vortex(POINT_RADIUS), 0);
            assertEquals(BoreDrill.HELD_AHEAD, start.subtract(EYE).dot(LOOK), SLACK);
            assertEquals(EYE.y - BoreDrill.HELD_BELOW, start.y, SLACK);
        }

        @Test
        void itMeetsTheLineOfSightAtTheReach() {
            ConeSections.Volume vortex = vortex(POINT_RADIUS);
            assertEquals(0, pointAlong(vortex, vortex.range()).distanceTo(EYE.add(LOOK.scale(REACH))), SLACK);
        }
    }

    /** The vortex builds up when the bore starts and disperses after it ends, rather than switching. */
    @Nested
    class Presence {

        @Test
        void itBuildsUpOverTheFadeInWhileHeld() {
            assertEquals(0.5, BoreDrill.presenceAfter(0, true, BoreDrill.FADE_IN_TICKS / 2), SLACK);
            assertEquals(1, BoreDrill.presenceAfter(0.5, true, BoreDrill.FADE_IN_TICKS), SLACK);
        }

        @Test
        void itDispersesOverTheFadeOutOnceReleased() {
            assertEquals(0.5, BoreDrill.presenceAfter(1, false, BoreDrill.FADE_OUT_TICKS / 2), SLACK);
            assertEquals(0, BoreDrill.presenceAfter(0.5, false, BoreDrill.FADE_OUT_TICKS), SLACK);
        }
    }

    @Nested
    class Radius {

        @Test
        void itsMouthIsWideAndItsFarEndAsWideAsThePointBoreBreaks() {
            for (double pointRadius : new double[] {POINT_RADIUS, 0.2}) {
                ConeSections.Volume vortex = vortex(pointRadius);
                assertEquals(BoreDrill.MOUTH_RADIUS, vortex.radiusAt().applyAsDouble(0), SLACK);
                assertEquals(pointRadius, vortex.radiusAt().applyAsDouble(vortex.range()), SLACK);
            }
        }

        @Test
        void itNarrowsFromItsMouthToItsFarEndAndIsNeverWiderThanABlock() {
            ConeSections.Volume vortex = vortex(POINT_RADIUS);
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
