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
            assertEquals(22.5, degreesOffTheLook(place, point), SLACK);
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
        assertEquals(22.5, degreesOffTheLook(place, tip), SLACK);
    }

    /**
     * Operator, 2026-11-17: looking down at a certain point the blob leaps left
     * and down, pitching back up it leaps too high over the palm and floats
     * back down, and it twitches like that facing near straight up too.
     * decision diagnose-and-correct-bore-behaving-weirdly-close-to-things
     */
    @Nested
    class NearStraightUpOrDown {

        /** One frame at sixty frames a second, in ticks. */
        private static final double FRAME_TICKS = 20.0 / 60;
        /** A tenth of a degree a frame, a slow turn of the head. */
        private static final float PITCH_STEP = 0.1f;
        /** Most a blob 1.6 blocks from the eye moves in a frame turned a tenth of a degree, eased or bound. */
        private static final double MOST_IN_A_FRAME = 0.05;
        private static final float YAW_OFF_THE_AXES = 45f;
        private static final double FLOOR_BELOW_THE_EYE = 1.62;

        /**
         * The farthest the drawn blob moves in one frame while the view
         * pitches from one angle to another at a tenth of a degree a frame,
         * aimed at a floor or ceiling the eye height away.
         */
        private double mostMovedInAFrame(int casterId, float fromPitch, float toPitch) {
            Vec3 eye = new Vec3(0.5, 65.62, 0.5);
            int frames = Math.round(Math.abs(toPitch - fromPitch) / PITCH_STEP);
            float step = Math.signum(toPitch - fromPitch) * PITCH_STEP;
            Vec3 lastDrawn = null;
            double most = 0;
            for (int frame = 0; frame <= frames; frame++) {
                float pitch = fromPitch + frame * step;
                Vec3 look = Vec3.directionFromRotation(pitch, YAW_OFF_THE_AXES);
                Vec3 point = eye.add(look.scale(FLOOR_BELOW_THE_EYE / Math.abs(look.y)));
                Vec3 rest = SpraySource.inView(eye, pitch, YAW_OFF_THE_AXES, true);
                Vec3 bound = BoreBlobPlace.placeFor(eye, look, point, rest, SHIPPED);
                BoreBlobPlace.ViewFrame view = BoreBlobPlace.ViewFrame.of(new CameraEye.Sight(eye, look),
                        YAW_OFF_THE_AXES);
                Vec3 drawn = BoreBlobPlace.easedFor(casterId, view, rest, bound, false, frame * FRAME_TICKS);
                if (lastDrawn != null) {
                    most = Math.max(most, drawn.distanceTo(lastDrawn));
                }
                lastDrawn = drawn;
            }
            return most;
        }

        @Test
        void theBlobGlidesAsTheLookTipsToStraightDown() {
            double most = mostMovedInAFrame(-101, 70f, 90f);
            assertTrue(most < MOST_IN_A_FRAME, "the blob leapt " + most + " in one frame looking down");
        }

        @Test
        void theBlobGlidesAsTheLookTipsToStraightUp() {
            double most = mostMovedInAFrame(-102, -70f, -90f);
            assertTrue(most < MOST_IN_A_FRAME, "the blob leapt " + most + " in one frame looking up");
        }
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
