package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' glass knives fly a fixed pattern, spaced evenly across the cone in
 * three staggered rows and leaving at even ticks across the sweep, and arc
 * under gravity (decisions knives-sling-then-morph-to-flechettes,
 * knives-land-in-staggered-rows-with-no-randomness).
 */
class KnifeRainTest {

    private static final double TOLERANCE = 1e-9;
    /** Facing south, level. */
    private static final float SOUTH = 0f;
    /** The charge Knives ships with: 3 knives across 5 degrees to 27 across 45. */
    private static final Charge KNIVES = AbilityJson.decode("crystal_knives").delivery().charge();
    /** Degrees two yaws may differ by and still read as one. */
    private static final double YAW_WITHIN = 1e-4;
    /** Blocks two landings may differ by and still read as one row. */
    private static final double ROW_WITHIN = 1e-6;

    /**
     * The yaw a velocity flies at off a south look, in degrees, rising to
     * the thrower's right.
     *
     * @param velocity the velocity
     * @return the yaw
     */
    private static double yawOffSouth(Vec3 velocity) {
        return Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
    }

    /**
     * The release's velocities ordered from the thrower's left to right.
     *
     * @param share the share of a full charge
     * @return the velocities, leftmost first
     */
    private static List<Vec3> leftToRight(float share) {
        return KnifeRain.releaseVelocities(SOUTH, 0f, KNIVES, share).stream()
                .sorted(Comparator.comparingDouble(KnifeRainTest::yawOffSouth)).toList();
    }

    /**
     * How far from where it left a knife lands, flown over flat ground at
     * the height it left from.
     *
     * @param velocity the knife's velocity leaving the hand
     * @return the blocks it carries, level, before it falls back to its height
     */
    private static double landingDistance(Vec3 velocity) {
        Vec3 before = Vec3.ZERO;
        for (int tick = 1; tick <= KnifeRain.MOST_FLIGHT_TICKS; tick++) {
            Vec3 after = KnifeRain.positionAt(Vec3.ZERO, velocity, tick);
            if (after.y <= 0) {
                double share = before.y / (before.y - after.y);
                return before.horizontalDistance() + (after.horizontalDistance() - before.horizontalDistance())
                        * share;
            }
            before = after;
        }
        throw new AssertionError("the knife " + velocity + " never fell back to its height");
    }

    /**
     * The same charge always slings the same knives, and no randomness
     * enters the pattern (decision knives-land-in-staggered-rows-with-no-randomness).
     */
    @ParameterizedTest
    @ValueSource(floats = {0f, 0.5f, 1f})
    void theSameChargeAlwaysSlingsTheSameKnives(float share) {
        assertEquals(KnifeRain.releaseVelocities(20f, -15f, KNIVES, share),
                KnifeRain.releaseVelocities(20f, -15f, KNIVES, share), "the velocities should repeat");
        int count = KNIVES.fleckCount(share);
        assertArrayEquals(KnifeRain.launchTicks(count, KNIVES.sweepTicks()),
                KnifeRain.launchTicks(count, KNIVES.sweepTicks()), "the launch ticks should repeat");
    }

    /**
     * The knives space evenly left to right across the cone, centered on the
     * look and inside the cone the charge gives
     * (decision knives-land-in-staggered-rows-with-no-randomness).
     */
    @ParameterizedTest
    @ValueSource(floats = {0f, 0.5f, 1f})
    void theKnivesSpaceEvenlyAcrossTheConeCenteredOnTheLook(float share) {
        double[] yaws = leftToRight(share).stream().mapToDouble(KnifeRainTest::yawOffSouth).toArray();
        int count = yaws.length;

        assertEquals(KNIVES.fleckCount(share), count);
        double gap = yaws[1] - yaws[0];
        assertTrue(gap > 0, "neighbouring knives should part, parted by " + gap);
        for (int index = 1; index < count; index++) {
            assertEquals(gap, yaws[index] - yaws[index - 1], YAW_WITHIN, "the gap left of knife " + index);
        }
        for (int index = 0; index < count; index++) {
            assertEquals(-yaws[index], yaws[count - 1 - index], YAW_WITHIN, "knife " + index + " mirrors about the look");
        }
        double half = KNIVES.spreadDegrees(share) / 2;
        assertTrue(Math.abs(yaws[0]) <= half + YAW_WITHIN && Math.abs(yaws[count - 1]) <= half + YAW_WITHIN,
                "the outermost knives fly at " + yaws[0] + " and " + yaws[count - 1] + ", inside " + half);
    }

    /**
     * The knives land in three rows, their depth stepping near, far, middle
     * from the leftmost knife, so no two neighbours share a row and the rows
     * offset like brickwork (decision knives-land-in-staggered-rows-with-no-randomness).
     */
    @Test
    void aFullChargeLandsInThreeStaggeredRowsNearFarMiddle() {
        double[] landings = leftToRight(1f).stream().mapToDouble(KnifeRainTest::landingDistance).toArray();
        double near = landings[0];
        double far = landings[1];
        double middle = landings[2];

        assertTrue(near + 1 < middle && middle + 1 < far, "the rows should stand apart, near " + near
                + ", middle " + middle + ", far " + far);
        double[] cycle = {near, far, middle};
        int[] perRow = new int[3];
        for (int index = 0; index < landings.length; index++) {
            assertEquals(cycle[index % 3], landings[index], ROW_WITHIN, "knife " + index + " lands in row "
                    + index % 3);
            perRow[index % 3]++;
        }
        assertArrayEquals(new int[] {9, 9, 9}, perRow, "the 27 knives split nine to a row");
    }

    /**
     * The knives leave the blob at even intervals across the backhand
     * (decision knives-land-in-staggered-rows-with-no-randomness).
     */
    @ParameterizedTest
    @ValueSource(ints = {3, 15, 27})
    void theKnivesLeaveAtEvenTicksAcrossTheSweep(int count) {
        int sweep = KNIVES.sweepTicks();
        int[] ticks = KnifeRain.launchTicks(count, sweep);

        assertEquals(count, ticks.length);
        assertEquals(0, ticks[0], "the first knife leaves at once");
        assertEquals(sweep, ticks[count - 1], "the last knife leaves at the sweep's end");
        int fewest = Integer.MAX_VALUE;
        int most = Integer.MIN_VALUE;
        for (int index = 1; index < count; index++) {
            int gap = ticks[index] - ticks[index - 1];
            assertTrue(gap >= 0, "the ticks never go back, at knife " + index);
            fewest = Math.min(fewest, gap);
            most = Math.max(most, gap);
        }
        assertTrue(most - fewest <= 1, "the gaps should differ by a tick at most, ran " + fewest + " to " + most);
    }

    @Test
    void knivesAreLobbedAboveTheLook() {
        for (Vec3 velocity : KnifeRain.releaseVelocities(SOUTH, 0f, KNIVES, 1f)) {
            assertTrue(velocity.y > 0, "a level look lobs every knife upward, " + velocity);
        }
    }

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

        @Test
        void theKnivesLeaveTheFloatPointAtTheHandsReachForTheTicksHeld() {
            assertEquals(0, KnifeRain.floatPoint(eye, yaw, look, true, KnifeRain.windUpAcross(18))
                    .distanceTo(KnifeRain.leavesFrom(eye, yaw, look, true, 18, 0)), 1e-9);
        }
    }

    // knives-leave-the-blob-at-a-snowballs-speed
    @Test
    void knivesLeaveTheBlobAtASnowballsSpeed() {
        List<Vec3> velocities = KnifeRain.releaseVelocities(SOUTH, 0f, KNIVES, 1f);

        for (Vec3 velocity : velocities) {
            double speed = velocity.length();
            assertTrue(speed >= 1.2 - TOLERANCE && speed <= 1.8 + TOLERANCE,
                    "every knife leaves at 1.2 to 1.8 blocks a tick, left at " + speed);
        }
        double meanSpeed = velocities.stream().mapToDouble(Vec3::length).average().orElseThrow();
        assertEquals(1.5, meanSpeed, 0.1, "the knives leave at a snowball's speed on average");
    }

    // knives-leave-the-blob-at-a-snowballs-speed
    @Test
    void aLevelThrowCarriesPastEighteenBlocks() {
        Vec3 hand = new Vec3(0, 1.32, 0);
        Vec3 velocity = Vec3.directionFromRotation(-KnifeRain.LIFT_DEGREES, SOUTH).scale(KnifeRain.SPEED);

        double landing = Double.NaN;
        Vec3 before = hand;
        for (int tick = 1; tick <= KnifeRain.MOST_FLIGHT_TICKS; tick++) {
            Vec3 after = KnifeRain.positionAt(hand, velocity, tick);
            if (after.y <= 0) {
                double share = before.y / (before.y - after.y);
                landing = before.z + (after.z - before.z) * share;
                break;
            }
            before = after;
        }
        assertTrue(landing >= 18, "a level throw from the hand carries 18 blocks or farther, landed at " + landing);
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
