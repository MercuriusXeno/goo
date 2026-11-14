package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's blob eases from its lifted rest by the glove to the place that holds
 * the drill's cross section on the crosshair at the depth point: the drill's
 * length less its past length back from that point, on a line its angle off
 * the sight line, toward the rest's side and below the crosshair, with the
 * drill's tip its past length beyond the point; for a near point it stands no
 * nearer the eye than the rest, the drill shortening from the blob's end
 * (decisions bore-drill-is-two-blocks-held-at-an-angle-off-the-sight-line,
 * bore-blob-eases-to-place-the-drill-at-the-crosshair,
 * bore-drill-shortens-for-a-near-block-and-holds-its-angle,
 * bore-cross-section-is-where-the-angled-drill-meets-the-look-plane).
 */
class BoreBlobPlaceTest {

    private static final double SLACK = 1e-6;
    private static final BoreDrillShape SHIPPED = BoreDrillShape.SHIPPED;
    private static final Vec3 EYE = new Vec3(3.5, 70.62, -8.5);
    /** A level view looking along +Z: yaw 0. */
    private static final float PITCH = 0f;
    private static final float YAW = 0f;
    private static final Vec3 LOOK = Vec3.directionFromRotation(PITCH, YAW);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 restFor(boolean gloveRight) {
        return SpraySource.inView(EYE, PITCH, YAW, gloveRight);
    }

    private static double degreesOffTheLook(Vec3 from, Vec3 to) {
        return Math.toDegrees(Math.acos(to.subtract(from).normalize().dot(LOOK)));
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
            Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point, rest, SHIPPED);
            Vec3 tip = BoreDrill.tipOf(place, point, SHIPPED);
            assertEquals(1, place.distanceTo(point), SLACK);
            assertEquals(1, point.distanceTo(tip), SLACK);
            assertEquals(1, tip.subtract(point).normalize().dot(point.subtract(place).normalize()), SLACK);
            assertEquals(15, degreesOffTheLook(place, point), SLACK);
            assertTrue(offTheSight(place).dot(offTheSight(rest)) > 0, "the place stands off the rest's side");
            assertTrue(offTheSight(place).dot(UP) < 0, "the place stands no lower than the look");
        }

        @Test
        void theBlobStandsOneBackAndTheTipOnePastForAGloveInTheRightHand() {
            assertHeldOnTheCrosshair(true);
        }

        @Test
        void theBlobStandsOneBackAndTheTipOnePastForAGloveInTheLeftHand() {
            assertHeldOnTheCrosshair(false);
        }

        @Test
        void itFollowsWhateverDrillTheStepCarries() {
            Vec3 farther = point.add(LOOK.scale(2));
            Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, farther, restFor(true), new BoreDrillShape(0.7, 3, 10, 1));
            assertEquals(2, place.distanceTo(farther), SLACK);
            assertEquals(10, degreesOffTheLook(place, farther), SLACK);
        }
    }

    @Test
    void forANearPointTheBlobStandsAsFarAlongTheLookAsTheRestAndTheTipAndAngleHold() {
        Vec3 rest = restFor(true);
        Vec3 point = EYE.add(LOOK.scale(1.2));
        Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point, rest, SHIPPED);
        Vec3 tip = BoreDrill.tipOf(place, point, SHIPPED);
        assertEquals(rest.subtract(EYE).dot(LOOK), place.subtract(EYE).dot(LOOK), SLACK);
        assertTrue(place.distanceTo(point) < 1, "the drill runs " + place.distanceTo(point) + " back");
        assertEquals(1, point.distanceTo(tip), SLACK);
        assertEquals(15, degreesOffTheLook(place, tip), SLACK);
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
