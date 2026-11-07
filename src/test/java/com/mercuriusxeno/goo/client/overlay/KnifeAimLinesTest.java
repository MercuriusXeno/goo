package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives aims one faint line per knife the current charge slings, each
 * leaving the drawn glove hand and following its knife's flight across the
 * cone until it would strike, the same lines every frame of a press and a
 * new scatter each press, the one the server slings the knives from
 * (decision knives-aims-a-spread-fan-not-a-blob-line).
 */
class KnifeAimLinesTest {

    private static final Charge KNIVES = new Charge(30, 24, 50, 8);
    private static final double TOLERANCE = 1e-9;
    /** Facing south, level. */
    private static final float SOUTH = 0f;
    private static final Vec3 HAND = new Vec3(2, 69.5, -5);
    private static final long SEED = 41L;
    private static final long OTHER_SEED = 97L;
    private static final KnifeAimLines.Strike OPEN_AIR = (from, to) -> null;

    @Nested
    class Knives {

        @Test
        void aFreshPressAimsOneLineAndAFullChargeOnePerKnife() {
            assertEquals(1, KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 0f, SEED).size());
            assertEquals(24, KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 1f, SEED).size());
        }

        @Test
        void everyFrameOfAPressAimsTheSameLinesAndANewPressScattersAnew() {
            List<Vec3> first = KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 0.6f, SEED);

            assertEquals(KNIVES.fleckCount(0.6f), first.size());
            assertEquals(first, KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 0.6f, SEED));
            assertNotEquals(first, KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 0.6f, OTHER_SEED));
        }

        @Test
        void theLinesAreTheVelocitiesTheServerSlingsFromThePressSeed() {
            assertEquals(KnifeRain.releaseVelocities(SOUTH, 0f, KNIVES, 0.6f, RandomSource.create(SEED)),
                    KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 0.6f, SEED));
        }

        @Test
        void theLinesScatterAcrossTheChargedCone() {
            double leftmost = Double.MAX_VALUE;
            double rightmost = -Double.MAX_VALUE;
            for (Vec3 velocity : KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 1f, SEED)) {
                double yaw = Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
                leftmost = Math.min(leftmost, yaw);
                rightmost = Math.max(rightmost, yaw);
                assertTrue(Math.abs(yaw) <= 25 + 1e-6, "every line flies inside the cone, flew " + yaw);
            }
            assertTrue(rightmost - leftmost > 40, "the lines reach across the cone, spanned " + (rightmost - leftmost));
        }
    }

    @Nested
    class Flight {

        @Test
        void aLineLeavesTheHandAndFollowsItsKnife() {
            Vec3 velocity = KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 1f, SEED).getFirst();

            List<Vec3> line = KnifeAimLines.flight(HAND, velocity, OPEN_AIR);

            assertEquals(KnifeRain.MOST_FLIGHT_TICKS + 1, line.size());
            for (int tick = 0; tick < line.size(); tick++) {
                assertEquals(0, KnifeRain.positionAt(HAND, velocity, tick).distanceTo(line.get(tick)), TOLERANCE);
            }
        }

        @Test
        void aLineEndsWhereItsKnifeStrikes() {
            double floor = HAND.y - 2;
            KnifeAimLines.Strike ground = (from, to) -> to.y > floor ? null
                    : from.lerp(to, (from.y - floor) / (from.y - to.y));
            Vec3 velocity = KnifeAimLines.velocitiesAt(SOUTH, 0f, KNIVES, 1f, SEED).getFirst();

            List<Vec3> line = KnifeAimLines.flight(HAND, velocity, ground);

            assertEquals(floor, line.getLast().y, TOLERANCE);
            assertTrue(line.get(line.size() - 2).y > floor, "the line runs until it reaches the ground");
            assertTrue(line.size() < KnifeRain.MOST_FLIGHT_TICKS + 1, "the line stops at the ground");
        }
    }
}
