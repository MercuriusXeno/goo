package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Airborn's rising wind: each mote rises straight up with a small, slow
 * swirl, fading in and out, and front motes rise in front of the camera often
 * enough that two stand at once (decision airborn-steerable-levitation-and-soft-falls).
 */
class AirbornWindTest {

    private static final double EPSILON = 1e-9;
    private static final double EYE_HEIGHT = 1.62;
    private static final Vec3 LOOKING_EAST_AND_DOWN = new Vec3(0.8, -0.6, 0);
    private static final AirbornWind.Mote MOTE = new AirbornWind.Mote(new Vec3(0.5, 0.3, -0.2), 0.7, 0L);
    private static final double VERTICAL_FOV = 70;
    private static final double WIDE_ASPECT = 16.0 / 9;

    @Nested
    class Rise {

        /** float-motes-ring-closer-span-the-view-and-run-twice-as-fast */
        @Test
        void aMoteRisesItsWholeRiseInThirtyTicksAndNoHigher() {
            double start = AirbornWind.motePoint(MOTE, 0).y;

            assertEquals(0.8, AirbornWind.motePoint(MOTE, 15).y - start, EPSILON);
            for (int age = 30; age <= 90; age++) {
                assertEquals(1.6, AirbornWind.motePoint(MOTE, age).y - start, EPSILON);
            }
        }

        @Test
        void aMoteSwirlsWithinItsSmallRadiusOfWhereItLeft() {
            for (int age = 0; age <= AirbornWind.LIFE_TICKS; age++) {
                Vec3 point = AirbornWind.motePoint(MOTE, age);
                double offAxis = Math.hypot(point.x - MOTE.start().x, point.z - MOTE.start().z);
                assertEquals(AirbornWind.SWIRL_RADIUS, offAxis, EPSILON);
            }
        }

        @Test
        void theSwirlTurnsLessThanOnceOverALife() {
            assertTrue(AirbornWind.SWIRL_RATE * AirbornWind.LIFE_TICKS < 2 * Math.PI);
        }

        @Test
        void aMoteFadesInAndOutAndHoldsBetween() {
            assertEquals(0, AirbornWind.opacity(0), EPSILON);
            assertEquals(1, AirbornWind.opacity(0.5), EPSILON);
            assertEquals(0, AirbornWind.opacity(1), EPSILON);
        }
    }

    @Nested
    class InView {

        @Test
        void aFrontMoteLeavesAheadOfTheEyeAlongTheLevelLookAndBelowIt() {
            Vec3 start = AirbornWind.frontStart(EYE_HEIGHT, LOOKING_EAST_AND_DOWN, 0);

            assertEquals(AirbornWind.FRONT_REACH, start.x, EPSILON);
            assertEquals(0, start.z, EPSILON);
            assertEquals(EYE_HEIGHT - AirbornWind.FRONT_DROP, start.y, EPSILON);
        }

        /** float-motes-ring-closer-span-the-view-and-run-twice-as-fast */
        @Test
        void aFullStrayToEachSidePutsAFrontMoteOnThatSidesViewEdge() {
            double horizontalFov = 2 * Math.atan(Math.tan(Math.toRadians(VERTICAL_FOV) / 2) * WIDE_ASPECT);
            double viewEdge = AirbornWind.FRONT_REACH * Math.tan(horizontalFov / 2);
            double spread = AirbornWind.frontSpread(VERTICAL_FOV, WIDE_ASPECT);

            Vec3 right = AirbornWind.frontStart(EYE_HEIGHT, LOOKING_EAST_AND_DOWN, spread);
            Vec3 left = AirbornWind.frontStart(EYE_HEIGHT, LOOKING_EAST_AND_DOWN, -spread);

            assertEquals(AirbornWind.FRONT_REACH, right.x, EPSILON);
            assertEquals(viewEdge, right.z, EPSILON);
            assertEquals(AirbornWind.FRONT_REACH, left.x, EPSILON);
            assertEquals(-viewEdge, left.z, EPSILON);
        }

        @Test
        void aWiderFieldOfViewOrAspectWidensTheFrontSpread() {
            double spread = AirbornWind.frontSpread(VERTICAL_FOV, WIDE_ASPECT);

            assertTrue(AirbornWind.frontSpread(VERTICAL_FOV + 20, WIDE_ASPECT) > spread);
            assertTrue(AirbornWind.frontSpread(VERTICAL_FOV, WIDE_ASPECT + 0.5) > spread);
        }

        @Test
        void aFrontMoteRisesThroughTheEyesHeight() {
            double top = EYE_HEIGHT - AirbornWind.FRONT_DROP + AirbornWind.RISE;

            assertTrue(top > EYE_HEIGHT);
        }

        @Test
        void atLeastTwoFrontMotesStandAtOnce() {
            assertTrue(AirbornWind.LIFE_TICKS / AirbornWind.FRONT_EVERY >= 2);
        }
    }

    /**
     * A front mote leaves below the camera's eased eye for the local player
     * in first person, so a sneak moves no new mote's start until the view
     * eases, and below any other player's own eye
     * (decision diagnose-and-correct-eye-drawn-visuals-jumping-on-sneak).
     */
    @Nested
    class EasesWithTheCamera {

        private static final double STANDING_EYE = 1.62;
        private static final double CROUCHED_EYE = 1.27;
        private static final double EASING_CAMERA_EYE = 1.47;

        private static Vec3 frontStart(boolean followsTheCamera, double entityEyeHeight) {
            return AirbornWind.frontStart(CameraEye.eyeHeight(followsTheCamera, EASING_CAMERA_EYE, entityEyeHeight),
                    LOOKING_EAST_AND_DOWN, 0);
        }

        @Test
        void theLocalFirstPersonMoteLeavesBelowTheCamerasEasedEyeWhateverTheEntityEye() {
            assertEquals(frontStart(true, STANDING_EYE), frontStart(true, CROUCHED_EYE));
            assertEquals(EASING_CAMERA_EYE - AirbornWind.FRONT_DROP, frontStart(true, CROUCHED_EYE).y, EPSILON);
        }

        @Test
        void anyOtherMoteLeavesBelowTheEntitysOwnEye() {
            assertEquals(STANDING_EYE - AirbornWind.FRONT_DROP, frontStart(false, STANDING_EYE).y, EPSILON);
            assertEquals(CROUCHED_EYE - AirbornWind.FRONT_DROP, frontStart(false, CROUCHED_EYE).y, EPSILON);
        }
    }

    /** float-motes-ring-closer-span-the-view-and-run-twice-as-fast */
    @Test
    void aBodyMoteLeavesOnTheRingAboutTheBody() {
        Vec3 start = AirbornWind.bodyStart(1.1, 0.4);

        assertEquals(0.45, Math.hypot(start.x, start.z), EPSILON);
        assertEquals(0.4, start.y, EPSILON);
    }

    /** float-motes-ring-closer-span-the-view-and-run-twice-as-fast */
    @Test
    void aBodyMoteLeavesEveryFourTicksAndAFrontMoteEveryTen() {
        assertEveryGapIs(4, LongStream.range(1000, 1040).filter(AirbornWind::bodyMoteLeavesOn).toArray());
        assertEveryGapIs(10, LongStream.range(1000, 1040).filter(AirbornWind::frontMoteLeavesOn).toArray());
    }

    private static void assertEveryGapIs(long gap, long[] leavingTicks) {
        assertTrue(leavingTicks.length >= 2);
        for (int index = 1; index < leavingTicks.length; index++) {
            assertEquals(gap, leavingTicks[index] - leavingTicks[index - 1]);
        }
    }
}
