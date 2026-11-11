package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's vortex runs long from the glove down the eye line Bore drills, out
 * to its reach, one block wide along the line
 * (decisions diagnose-and-correct-bores-small-distant-vortex, bore-breaks-a-15-degree-cone).
 */
class BoreVortexTest {

    /** rock_bore.json's delivery range. */
    private static final double REACH = 4;
    private static final double HALF_A_BLOCK = 0.5;
    private static final double SLACK = 1e-9;
    private static final Vec3 EYE = Vec3.ZERO;
    /** Yaw 0 looks along +Z. */
    private static final float YAW = 0f;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    /** A first-person glove: right of the eye (-X when facing +Z), below it and ahead of it. */
    private static final Vec3 HAND = new Vec3(-0.35, -0.3, 0.5);
    /** A long vortex spans this much of the view, from the glove to the crosshair at least. */
    private static final double LONG_SPAN_DEGREES = 30;
    private static final double AT_THE_GLOVE = 0.05;

    private static ConeSections.Volume volume() {
        return BoreVortex.volume(EYE, HAND, LOOK, YAW, REACH);
    }

    private static Vec3 pointAlong(ConeSections.Volume volume, double distance) {
        return volume.apex().add(volume.axis().scale(distance));
    }

    @Nested
    class Length {

        @Test
        void theVortexStartsAtTheGlove() {
            ConeSections.Volume volume = volume();
            assertEquals(0, pointAlong(volume, volume.near()).distanceTo(HAND), AT_THE_GLOVE);
        }

        @Test
        void theVortexEndsOnTheEyeLineAtTheReach() {
            ConeSections.Volume volume = volume();
            assertEquals(0, pointAlong(volume, volume.range()).distanceTo(EYE.add(LOOK.scale(REACH))), SLACK);
        }

        @Test
        void theVortexSpansALongStretchOfTheViewFromTheEye() {
            ConeSections.Volume volume = volume();
            Vec3 start = pointAlong(volume, volume.near()).subtract(EYE).normalize();
            Vec3 end = pointAlong(volume, volume.range()).subtract(EYE).normalize();
            double spanDegrees = Math.toDegrees(Math.acos(start.dot(end)));
            assertTrue(spanDegrees > LONG_SPAN_DEGREES, "the vortex spans " + spanDegrees + " degrees of the view");
        }
    }

    @Nested
    class Width {

        @Test
        void theRadiusIsHalfABlockFromOneBlockOutToTheReach() {
            for (double distance = 1; distance <= REACH; distance += HALF_A_BLOCK) {
                assertEquals(HALF_A_BLOCK, BoreVortex.radiusAt(distance), SLACK, "at " + distance);
            }
        }

        @Test
        void theVortexLeavesTheGloveNarrowerThanTheLineItDrills() {
            assertTrue(BoreVortex.radiusAt(0) < HALF_A_BLOCK);
        }
    }
}
