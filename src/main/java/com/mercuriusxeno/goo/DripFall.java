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

    /** The tap-drip's half extent: a 2-pixel drop. */
    public static final float TAP_DROP_HALF_SIZE = 1f / 16f;

    /** Height every drip quad keeps above the surface its collision box rests on. */
    public static final double SURFACE_MARGIN = 0.02;

    /**
     * How far below the spigot the hanging tap-drip's collision box starts:
     * the drop's full height and the surface margin.
     */
    public static final double TAP_HANGING_DROP = 2.0 * TAP_DROP_HALF_SIZE + SURFACE_MARGIN;

    private DripFall() {
    }

    /**
     * Ticks a tap-drip takes from leaving the spigot to meeting a surface, as
     * the client drop runs it: it hangs {@link #HANG_TICKS}, then falls from
     * {@link #TAP_HANGING_DROP} below the spigot, meeting the surface on a move;
     * where the surface sits closer than that, it splats as the hang ends
     * (decision tap-drop-swells-then-falls).
     *
     * @param spigotToSurface blocks from the spigot underside down to the surface
     * @param leaveSpeed      downward speed on leaving, in blocks per tick
     * @return the tick count at which the drip meets the surface
     */
    public static int arrivalTicks(double spigotToSurface, double leaveSpeed) {
        if (spigotToSurface < TAP_HANGING_DROP) {
            return HANG_TICKS;
        }
        return HANG_TICKS + Math.max(1, fallTicks(spigotToSurface - TAP_HANGING_DROP, leaveSpeed));
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
