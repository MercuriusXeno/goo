package com.mercuriusxeno.goo;

/**
 * Pure math for a goo drip falling under the drip particle's physics, shared
 * by the client particle that draws the fall and the server that times its
 * arrival, so the two meet (decision drip-falls-to-first-surface).
 */
public final class DripFall {

    /** Speed a falling drip gains each tick, in blocks per tick. */
    public static final double GRAVITY = 0.06;

    /** Fraction of a drip's speed kept after each tick. */
    public static final double DRAG = 0.98;

    private DripFall() {
    }

    /**
     * Ticks a drip leaving at a downward speed takes to fall a distance, each
     * tick gaining {@link #GRAVITY}, moving, then keeping {@link #DRAG} of its
     * speed, the order the drip particle ticks in.
     *
     * @param distance   blocks to fall; zero or less arrives at once
     * @param leaveSpeed downward speed on leaving, in blocks per tick
     * @return the tick count at which the drip has covered the distance
     */
    public static int fallTicks(double distance, double leaveSpeed) {
        int ticks = 0;
        double fallen = 0;
        double speed = leaveSpeed;
        while (fallen < distance) {
            speed += GRAVITY;
            fallen += speed;
            speed *= DRAG;
            ticks++;
        }
        return ticks;
    }
}
