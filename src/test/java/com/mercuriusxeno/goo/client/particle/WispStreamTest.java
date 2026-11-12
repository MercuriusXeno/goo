package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A streamed wisp flies as Decay's gnats do, chaotic and volatile, yet the
 * player's pull wins: it bursts off the drained mob, darts and swirls off the
 * line between them, and still lands on the player
 * (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
 */
class WispStreamTest {

    private static final Vec3 MOB = new Vec3(0, 0.9, 0);
    private static final Vec3 PLAYER = new Vec3(3.2, 1.3, 2.1);
    private static final int WISPS = 300;
    private static final double LAUNCH_SPEED = 0.12;
    /** The least the wisps stray off the straight line on average, in blocks, for a flow that reads chaotic. */
    private static final double LEAST_MEAN_STRAY = 0.25;

    /**
     * Flies one wisp from the mob until it lands or its time runs out.
     *
     * @param seed the wisp's random seed
     * @return the farthest it strayed off the line, or negative where it never landed
     */
    private static double fly(long seed) {
        RandomSource random = RandomSource.create(seed);
        Vec3 at = MOB;
        Vec3 velocity = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                .normalize().scale(LAUNCH_SPEED);
        double sway = random.nextDouble() * Math.PI * 2;
        Vec3 line = PLAYER.subtract(MOB).normalize();
        double strayed = 0;
        for (int shown = 1; shown <= WispStream.MOST_TICKS; shown++) {
            velocity = WispStream.step(velocity, at, PLAYER, shown, random, sway);
            at = at.add(velocity);
            Vec3 offset = at.subtract(MOB);
            strayed = Math.max(strayed, offset.subtract(line.scale(offset.dot(line))).length());
            if (at.distanceTo(PLAYER) <= WispStream.LANDS_WITHIN) {
                return strayed;
            }
        }
        return -1;
    }

    @Test
    void everyStreamedWispLandsOnThePlayer() {
        for (long seed = 0; seed < WISPS; seed++) {
            assertTrue(fly(seed) >= 0, "wisp " + seed + " never landed");
        }
    }

    @Test
    void streamedWispsStrayWellOffTheStraightLine() {
        double total = 0;
        for (long seed = 0; seed < WISPS; seed++) {
            total += Math.max(0, fly(seed));
        }
        double mean = total / WISPS;
        assertTrue(mean >= LEAST_MEAN_STRAY, "mean stray " + mean);
    }
}
