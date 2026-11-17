package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' glove hand eases across the chest through the wind-up with the
 * blob floating ahead of and above it, sweeps out through the backhand, and
 * a glass knife arcs under gravity (decisions knives-sling-then-morph-to-flechettes,
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives).
 */
class KnifeRainTest {

    private static final double TOLERANCE = 1e-9;
    /** Facing south, level. */
    private static final float SOUTH = 0f;

    // knives-aims-a-spread-fan-not-a-blob-line
    @Test
    void theHandPointSitsAheadBelowAndToTheGloveSideOfTheEye() {
        Vec3 eye = new Vec3(4, 70, -3);
        Vec3 lookSouth = new Vec3(0, 0, 1);

        Vec3 right = KnifeRain.handPoint(eye, SOUTH, lookSouth, true, 0f).subtract(eye);
        Vec3 left = KnifeRain.handPoint(eye, SOUTH, lookSouth, false, 0f).subtract(eye);

        assertEquals(0, new Vec3(-0.35, -0.3, 0.4).distanceTo(right), 1e-6, "facing south, the right hand is west");
        assertEquals(0, new Vec3(0.35, -0.3, 0.4).distanceTo(left), 1e-6, "a left glove mirrors the right");
    }

    /**
     * The blob Knives' charge lifts floats ahead of and above the glove hand
     * and moves with the hand as the wind-up eases it across the chest
     * (decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives).
     */
    @Nested
    class FloatsAheadOfAndAboveTheHand {

        private final Vec3 eye = new Vec3(3, 65.62, -2);
        private final float yaw = 20f;
        private final Vec3 look = Vec3.directionFromRotation(30f, yaw);

        @ParameterizedTest
        @ValueSource(floats = {0f, 1f})
        void atTheWindUpsStartAndItsFurthestTheBlobFloatsAboveAndAheadOfTheHand(float across) {
            Vec3 floating = KnifeRain.floatPoint(eye, yaw, look, true, across);
            Vec3 hand = KnifeRain.handPoint(eye, yaw, look, true, across);

            assertTrue(floating.y > hand.y, "the blob " + floating + " should float above the hand " + hand);
            assertTrue(floating.subtract(eye).dot(look) > hand.subtract(eye).dot(look), "the blob " + floating
                    + " should float farther along the look than the hand " + hand);
        }

        @Test
        void theBlobMovesBetweenTheTwoByTheHandsOwnTravelAcrossTheChest() {
            Vec3 handTravel = KnifeRain.handPoint(eye, yaw, look, true, 1f)
                    .subtract(KnifeRain.handPoint(eye, yaw, look, true, 0f));
            Vec3 blobTravel = KnifeRain.floatPoint(eye, yaw, look, true, 1f)
                    .subtract(KnifeRain.floatPoint(eye, yaw, look, true, 0f));

            assertTrue(handTravel.length() > 0.1, "the wind-up should carry the hand across the chest, "
                    + handTravel);
            assertEquals(0, handTravel.distanceTo(blobTravel), 1e-9, "the blob should travel " + handTravel
                    + " with the hand, travelled " + blobTravel);
        }

        @Test
        void aLongerHoldEasesTheHandFurtherAcrossUntilTheWindUpIsDone() {
            assertEquals(0f, KnifeRain.windUpAcross(0), 1e-6f, "a fresh press should leave the hand at the glove side");
            assertTrue(KnifeRain.windUpAcross(10) < KnifeRain.windUpAcross(20), "a longer hold should reach further");
            assertEquals(1f, KnifeRain.windUpAcross(KnifeRain.WIND_UP_TICKS), 1e-6f, "the wind-up should be done");
            assertEquals(1f, KnifeRain.windUpAcross(KnifeRain.WIND_UP_TICKS * 2), 1e-6f, "and reach no further");
        }

        @Test
        void throughTheBackhandTheHandSweepsOutPastTheGloveSideHoldsThereAndComesBackSmoothly() {
            float out = KnifeRain.BACKHAND_OUT_TICKS;
            float held = out + KnifeRain.BACKHAND_HOLD_TICKS;
            float back = held + KnifeRain.BACKHAND_RETURN_TICKS;

            assertEquals(0.8f, KnifeRain.backhandAcross(0.8f, 0), 1e-6f, "the backhand starts where the wind-up got to");
            assertTrue(KnifeRain.backhandAcross(0.8f, out) < 0f, "flung, the hand stands out past the glove side");
            assertEquals(KnifeRain.backhandAcross(0.8f, out), KnifeRain.backhandAcross(0.8f, held), 1e-6f,
                    "the hand holds flung");
            assertEquals(0f, KnifeRain.backhandAcross(0.8f, back), 1e-6f, "the hand comes back to the glove side");
            for (double ticks = 0; ticks < back; ticks += 0.25) {
                float step = KnifeRain.backhandAcross(0.8f, ticks + 0.25) - KnifeRain.backhandAcross(0.8f, ticks);
                assertTrue(Math.abs(step) < 0.3f, "the hand should move smoothly, stepped " + step + " at " + ticks);
            }
        }

    }

    @Test
    void aKnifeArcsDownUnderGravity() {
        Vec3 start = new Vec3(0, 10, 0);
        Vec3 velocity = new Vec3(0, 0.2, 1);

        Vec3 next = KnifeRain.velocityAt(velocity, 1);
        assertEquals(0.2 * KnifeRain.DRAG - KnifeRain.GRAVITY, next.y, TOLERANCE);
        assertEquals(0, start.add(velocity).add(next).distanceTo(KnifeRain.positionAt(start, velocity, 2)), TOLERANCE);
        assertTrue(KnifeRain.positionAt(start, velocity, 30).y < start.y, "the knife falls below where it left");
        assertTrue(KnifeRain.positionAt(start, velocity, 30).z > 20, "the knife carries well ahead as it falls");
    }
}
