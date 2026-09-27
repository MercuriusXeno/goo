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

    /**
     * Ticks a tap-drip hangs swelling at the spigot before it falls, the
     * fastest particle grade's interval so hanging drops never overlap
     * (decision tap-drop-swells-then-falls).
     */
    public static final int HANG_TICKS = 4;

    private DripFall() {
    }

    /**
     * Ticks a tap-drip takes from leaving the spigot to reaching a surface:
     * it hangs {@link #HANG_TICKS}, then falls (decision tap-drop-swells-then-falls).
     *
     * @param distance   blocks to fall once the hang ends
     * @param leaveSpeed downward speed on leaving, in blocks per tick
     * @return the tick count at which the drip reaches the surface
     */
    public static int arrivalTicks(double distance, double leaveSpeed) {
        return HANG_TICKS + fallTicks(distance, leaveSpeed);
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
