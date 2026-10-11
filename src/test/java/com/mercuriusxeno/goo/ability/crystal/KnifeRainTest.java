package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shards' glass knives scatter knife to knife across the cone, stream out
 * across the sweep, and arc under gravity (decision shards-sling-then-morph-to-flechettes).
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
