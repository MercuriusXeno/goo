package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.function.IntSupplier;

/**
 * One crystal cloud's reflected colors, one per sliver, each sampled at
 * most once per client tick and reused by every frame drawn in that tick,
 * so a cloud raycasts per sliver per tick rather than per face per frame
 * (decision render-context-is-the-one-emitter).
 */
final class ReflectionSampler {

    /**
     * The color a ray finds in the world.
     */
    @FunctionalInterface
    interface BlockColorProbe {
        /**
         * Casts a ray and answers the RGB color of what it hits.
         *
         * @param origin    the ray start
         * @param direction the ray direction
         * @return the RGB color of the block hit, or the sky's on a miss
         */
        int colorAlong(Vec3 origin, Vec3 direction);
    }

    /** The tick a sliver reads before its first sample. */
    private static final long NEVER = Long.MIN_VALUE;

    private final int[] colors;
    private final long[] sampledTicks;
    private long lastUsedTick = NEVER;

    /**
     * Creates a sampler holding no sample yet.
     *
     * @param slivers how many slivers the cloud can show
     */
    ReflectionSampler(int slivers) {
        colors = new int[slivers];
        sampledTicks = new long[slivers];
        Arrays.fill(sampledTicks, NEVER);
    }

    /**
     * The sliver's color for this tick, sampling it only when this tick
     * has not sampled it yet.
     *
     * @param sliver the sliver index
     * @param tick   the client tick being drawn
     * @param sample samples the sliver's color
     * @return the sliver's RGB color
     */
    int colorFor(int sliver, long tick, IntSupplier sample) {
        lastUsedTick = tick;
        if (sampledTicks[sliver] != tick) {
            colors[sliver] = sample.getAsInt();
            sampledTicks[sliver] = tick;
        }
        return colors[sliver];
    }

    /**
     * Whether the cloud has gone undrawn for longer than the given span.
     *
     * @param tick     the current client tick
     * @param maxIdle  the ticks a sampler may go undrawn
     * @return true when it may be dropped
     */
    boolean idleSince(long tick, long maxIdle) {
        return lastUsedTick != NEVER && tick - lastUsedTick > maxIdle;
    }
}
