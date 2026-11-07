package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;

/**
 * How a held hunk glides toward its hold: each moment it closes a share of
 * the gap that grows with the time passed, the same however the time is cut
 * into frames or ticks, so it eases in and trails the look a little rather
 * than jumping to it. The server moves the hunk by it each tick and the
 * holder's client draws it by it each frame.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class HoldEase {

    /** How fast the gap closes: the share left after a second is e to the minus this. */
    static final double RATE_PER_SECOND = 8;
    /** One server tick, in seconds. */
    public static final double TICK_SECONDS = 0.05;

    private HoldEase() {
    }

    /**
     * Where an easing hunk stands after some time.
     *
     * @param from    where it stood
     * @param hold    where the hold puts it
     * @param seconds the time passed
     * @return where it stands now
     */
    public static Vec3 toward(Vec3 from, Vec3 hold, double seconds) {
        return from.lerp(hold, 1 - Math.exp(-RATE_PER_SECOND * Math.max(0, seconds)));
    }
}
