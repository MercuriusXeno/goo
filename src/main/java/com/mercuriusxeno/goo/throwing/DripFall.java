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
}
