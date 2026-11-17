package com.mercuriusxeno.goo.throwing;

/**
 * The physics a goo drip falls under, shared by the drip particles that draw
 * the fall (decision drip-falls-to-first-surface).
 */
public final class DripFall {

    /** Speed a falling drip gains each tick, in blocks per tick. */
    public static final double GRAVITY = 0.06;

    /** Fraction of a drip's speed kept after each tick. */
    public static final double DRAG = 0.98;

    /** Height every drip quad keeps above the surface its collision box rests on. */
    public static final double SURFACE_MARGIN = 0.02;

    private DripFall() {
    }

    /**
     * The ticks a drip falling from rest takes to drop a height, stepped as
     * the falling drip particle steps, gravity then the move then the drag
     * each tick, so a fall's server-side arrival and its drawn drip's landing
     * share one tick.
     * falling-layer-lands-when-its-drip-arrives
     * goo-moving-downward-falls-as-drips
     *
     * @param height the height to drop, in blocks
     * @return the ticks, at least one
     */
    public static int ticksToFall(double height) {
        double velocity = 0;
        double fallen = 0;
        int ticks = 0;
        do {
            velocity -= GRAVITY;
            fallen -= velocity;
            velocity *= DRAG;
            ticks++;
        } while (fallen < height);
        return ticks;
    }
}
