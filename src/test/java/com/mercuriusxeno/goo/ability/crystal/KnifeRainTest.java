package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' glass knives scatter knife to knife across the cone, stream out
 * across the sweep, and arc under gravity (decision knives-sling-then-morph-to-flechettes).
 */
class KnifeRainTest {

    private static final double TOLERANCE = 1e-9;
    /** Facing south, level. */
    private static final float SOUTH = 0f;

    @Test
    void knivesScatterKnifeToKnifeAndCoverTheWholeCone() {
        List<Vec3> velocities = KnifeRain.scatteredVelocities(SOUTH, 0f, 24, 50, RandomSource.create(3L));

        Set<Long> distinct = new HashSet<>();
        double leftmost = Double.MAX_VALUE;
        double rightmost = -Double.MAX_VALUE;
        for (Vec3 velocity : velocities) {
            distinct.add(Math.round(velocity.x * 1e6) * 31 + Math.round(velocity.y * 1e6));
            double yaw = Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            leftmost = Math.min(leftmost, yaw);
            rightmost = Math.max(rightmost, yaw);
            assertTrue(Math.abs(yaw) <= 25 + 1e-6, "every knife flies inside the cone, flew " + yaw);
        }
        assertEquals(24, distinct.size(), "no two knives fly alike");
        assertTrue(rightmost - leftmost > 40, "the knives reach across the cone, spanned " + (rightmost - leftmost));
    }

    @Test
    void knivesAreLobbedAboveTheLookAndScatterInPitchAndSpeed() {
        List<Vec3> velocities = KnifeRain.scatteredVelocities(SOUTH, 0f, 24, 50, RandomSource.create(5L));

        Set<Long> speeds = new HashSet<>();
        Set<Long> rises = new HashSet<>();
        for (Vec3 velocity : velocities) {
            speeds.add(Math.round(velocity.length() * 1e4));
            rises.add(Math.round(velocity.y / velocity.length() * 1e4));
        }
        double meanRise = velocities.stream().mapToDouble(velocity -> velocity.y / velocity.length()).average()
                .orElseThrow();
        assertTrue(meanRise > 0, "the cloud is lobbed upward on average, rose " + meanRise);
        assertTrue(speeds.size() > 12 && rises.size() > 12, "speed and pitch scatter knife to knife");
    }

    @Test
    void knivesStreamOutAcrossTheSweepInOrder() {
        int[] ticks = KnifeRain.launchTicks(24, 8, RandomSource.create(7L));

        assertTrue(ticks[0] <= 1, "the first knife leaves at once, left at " + ticks[0]);
        assertTrue(ticks[23] >= 7, "the last knife leaves at the sweep's end, left at " + ticks[23]);
        for (int index = 1; index < ticks.length; index++) {
            assertTrue(ticks[index] >= ticks[index - 1]);
        }
        assertTrue(ticks[12] >= 3 && ticks[12] <= 6, "the middle knife leaves mid-sweep, left at " + ticks[12]);
    }

    // knives-aims-a-spread-fan-not-a-blob-line
    @Test
    void theHandPointSitsAheadBelowAndToTheGloveSideOfTheEye() {
        Vec3 eye = new Vec3(4, 70, -3);
        Vec3 lookSouth = new Vec3(0, 0, 1);

        Vec3 right = KnifeRain.handPoint(eye, SOUTH, lookSouth, true).subtract(eye);
        Vec3 left = KnifeRain.handPoint(eye, SOUTH, lookSouth, false).subtract(eye);

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
