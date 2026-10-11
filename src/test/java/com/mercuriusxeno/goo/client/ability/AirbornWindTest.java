package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    @Nested
    class Rise {

        @Test
        void aMoteRisesTheWholeRiseOverItsLife() {
            double start = AirbornWind.motePoint(MOTE, 0).y;
            double end = AirbornWind.motePoint(MOTE, AirbornWind.LIFE_TICKS).y;

            assertEquals(AirbornWind.RISE, end - start, EPSILON);
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

        @Test
        void aFrontMoteStraysToTheSideWithinItsStray() {
            Vec3 start = AirbornWind.frontStart(EYE_HEIGHT, LOOKING_EAST_AND_DOWN, 1);

            assertEquals(AirbornWind.FRONT_STRAY, Math.abs(start.z), EPSILON);
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

    @Test
    void aBodyMoteLeavesOnTheRingAboutTheBody() {
        Vec3 start = AirbornWind.bodyStart(1.1, 0.4);

        assertEquals(AirbornWind.BODY_RADIUS, Math.hypot(start.x, start.z), EPSILON);
        assertEquals(0.4, start.y, EPSILON);
    }
}
