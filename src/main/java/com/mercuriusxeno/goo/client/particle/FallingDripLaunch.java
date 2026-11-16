package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.FallingDripPayload;
import net.minecraft.world.phys.Vec3;

/**
 * How the client launches the drip a falling layer draws as: from the
 * point the layer leaves, from rest, falling under the drip's gravity and
 * drag as the server reckons the fall, living the fall's ticks and the
 * tick it splats on, so it reaches the landing surface as the layer lands.
 * goo-moving-downward-falls-as-drips
 *
 * @param start    where the drip starts
 * @param velocity the drip's velocity at start, blocks per tick
 * @param lifetime the ticks the drip lives
 */
public record FallingDripLaunch(Vec3 start, Vec3 velocity, int lifetime) {

    /** The tick past the fall's last a drip lives, the tick its contact with the surface splats it. */
    public static final int SPLAT_TICK = 1;

    /**
     * @param fall the fall's payload
     * @return the drip's launch
     */
    public static FallingDripLaunch of(FallingDripPayload fall) {
        return new FallingDripLaunch(fall.start(), Vec3.ZERO, fall.ticks() + SPLAT_TICK);
    }
}
