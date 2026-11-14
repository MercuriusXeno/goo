package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's blob eases from its lifted rest by the glove to the place that holds
 * the drill on the crosshair: the drill's length back from its point, on a
 * line its angle off the sight line, toward the rest's side and below the
 * crosshair; for a near point it stands no nearer the eye than the rest, the
 * drill shortening and its angle holding
 * (decisions bore-drill-is-two-blocks-held-at-an-angle-off-the-sight-line,
 * bore-blob-eases-to-place-the-drill-at-the-crosshair,
 * bore-drill-shortens-for-a-near-block-and-holds-its-angle).
 */
class BoreBlobPlaceTest {

    private static final double SLACK = 1e-6;
    private static final double LENGTH = 2;
    private static final double ANGLE = 22.5;
    private static final Vec3 EYE = new Vec3(3.5, 70.62, -8.5);
    /** A level view looking along +Z: yaw 0. */
    private static final float PITCH = 0f;
    private static final float YAW = 0f;
    private static final Vec3 LOOK = Vec3.directionFromRotation(PITCH, YAW);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 restFor(boolean gloveRight) {
        return SpraySource.inView(EYE, PITCH, YAW, gloveRight);
    }

    private static double degreesOffTheLook(Vec3 place, Vec3 point) {
        return Math.toDegrees(Math.acos(point.subtract(place).normalize().dot(LOOK)));
    }

    /** The place's offset from the sight line, square to the look. */
    private static Vec3 offTheSight(Vec3 place) {
        Vec3 fromEye = place.subtract(EYE);
        return fromEye.subtract(LOOK.scale(fromEye.dot(LOOK)));
    }

    @Nested
    class AFarPoint {

        private final Vec3 point = EYE.add(LOOK.scale(4));

        private void assertHeldOnTheCrosshair(boolean gloveRight) {
            Vec3 rest = restFor(gloveRight);
            Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point, rest, LENGTH, ANGLE);
            assertEquals(LENGTH, place.distanceTo(point), SLACK);
            assertEquals(ANGLE, degreesOffTheLook(place, point), SLACK);
            assertTrue(offTheSight(place).dot(offTheSight(rest)) > 0, "the place stands off the rest's side");
            assertTrue(offTheSight(place).dot(UP) < 0, "the place stands no lower than the look");
        }

        @Test
        void itStandsTheDrillsLengthBackAtItsAngleForAGloveInTheRightHand() {
            assertHeldOnTheCrosshair(true);
        }

        @Test
        void itStandsTheDrillsLengthBackAtItsAngleForAGloveInTheLeftHand() {
            assertHeldOnTheCrosshair(false);
        }

        @Test
        void itFollowsWhateverLengthAndAngleTheStepCarries() {
            Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point.add(LOOK.scale(2)), restFor(true), 3, 10);
            assertEquals(3, place.distanceTo(point.add(LOOK.scale(2))), SLACK);
            assertEquals(10, degreesOffTheLook(place, point.add(LOOK.scale(2))), SLACK);
        }
    }

    @Test
    void forANearPointItStandsAsFarAlongTheLookAsTheRestAndTheDrillShortensAtItsAngle() {
        Vec3 rest = restFor(true);
        Vec3 point = EYE.add(LOOK.scale(1.2));
        Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point, rest, LENGTH, ANGLE);
        assertEquals(rest.subtract(EYE).dot(LOOK), place.subtract(EYE).dot(LOOK), SLACK);
        assertTrue(place.distanceTo(point) < LENGTH, "the drill runs " + place.distanceTo(point) + " long");
        assertEquals(ANGLE, degreesOffTheLook(place, point), SLACK);
    }

    @Nested
    class Easing {

        /** One frame at sixty frames a second, in ticks. */
        private static final double FRAME_TICKS = 20.0 / 60;

        private final Vec3 drawn = new Vec3(1, 64, 1);
        private final Vec3 bound = drawn.add(0, 0, 1);

        @Test
        void oneFrameMovesTheBlobPartOfTheWay() {
            Vec3 eased = BoreBlobPlace.easedToward(drawn, bound, FRAME_TICKS);
            assertTrue(eased.z > drawn.z && eased.z < bound.z, "one frame moved the blob to " + eased);
        }

        @Test
        void aSecondOfFramesBringsTheBlobThere() {
            Vec3 eased = drawn;
            for (int frame = 0; frame < 60; frame++) {
                eased = BoreBlobPlace.easedToward(eased, bound, FRAME_TICKS);
            }
            assertEquals(0, eased.distanceTo(bound), 0.01);
        }
    }
}
