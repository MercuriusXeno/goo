package com.mercuriusxeno.goo.client.particle;

/**
 * Where a drip's quads sit against the surface its collision box lands on,
 * so neither the falling drop nor its splat shares the block top's plane
 * (decision diagnose-then-fix-drip-z-fighting).
 */
public final class DripQuadPlacement {

    /** Height every drip quad keeps above the surface its collision box rests on. */
    public static final double SURFACE_MARGIN = 0.02;

    /** Half extents in a quad's full height. */
    private static final double HALVES_PER_QUAD = 2.0;

    private DripQuadPlacement() {
    }

    /**
     * Lifts the quad's center off the collision box's bottom by a half extent
     * and the margin, so its lower half no longer sinks through the surface.
     *
     * @param particleY the falling drip's y, the bottom of its collision box
     * @param halfSize  the quad's half extent
     * @return the y the falling drip's camera-facing quad is centered on
     */
    static double fallQuadCenterY(double particleY, float halfSize) {
        return particleY + halfSize + SURFACE_MARGIN;
    }

    /**
     * A camera-facing quad keeps its horizontal axis level, so its lowest
     * corner sits one half extent under its center at any camera pitch.
     *
     * @param particleY the falling drip's y, the bottom of its collision box
     * @param halfSize  the quad's half extent
     * @return the lowest y the falling drip's quad reaches
     */
    static double fallQuadLowestY(double particleY, float halfSize) {
        return fallQuadCenterY(particleY, halfSize) - halfSize;
    }

    /**
     * A drop hanging from a spigot: its quad's top sits at the spigot's
     * underside, so its collision box starts a quad height and the margin
     * lower, where its drawn bottom sits.
     *
     * @param spigotY  the spigot's underside
     * @param halfSize the quad's half extent
     * @return the y the hanging drop's collision box starts at
     */
    static double hangingSpawnY(double spigotY, float halfSize) {
        return spigotY - hangingDrop(halfSize);
    }

    /**
     * @param halfSize the quad's half extent
     * @return how far below the spigot a hanging drop's collision box starts
     */
    static double hangingDrop(float halfSize) {
        return HALVES_PER_QUAD * halfSize + SURFACE_MARGIN;
    }

    /**
     * @param roomBelow the clear height under the spigot, up to {@link #hangingDrop}
     * @param halfSize  the quad's half extent
     * @return whether a drop of that size hangs from the spigot clear of the surface
     */
    static boolean dropFits(double roomBelow, float halfSize) {
        return roomBelow >= hangingDrop(halfSize);
    }

    /**
     * What a drop hung at the spigot does once its hang ends: fall where it
     * has room to hang clear of the surface, else splat on the surface
     * (decision tap-drop-swells-then-falls).
     */
    enum HangEnd {
        /** The drop falls from where it hung. */
        FALL,
        /** The drop vanishes and its splat appears on the surface below the spigot. */
        SPLAT
    }

    /**
     * @param roomBelow the clear height under the spigot, up to {@link #hangingDrop}
     * @param halfSize  the quad's half extent
     * @return what the hung drop does when its hang ends
     */
    static HangEnd hangEnd(double roomBelow, float halfSize) {
        return dropFits(roomBelow, halfSize) ? HangEnd.FALL : HangEnd.SPLAT;
    }

    /**
     * @param hungTicks   hang ticks already run
     * @param partialTick the partial tick for interpolation
     * @param hangTicks   the whole hang's length
     * @return how far the drop has swelled, nothing at 0 and its full square at 1
     */
    static float hangProgress(int hungTicks, float partialTick, int hangTicks) {
        return Math.min(1f, Math.max(0f, (hungTicks + partialTick) / hangTicks));
    }

    /**
     * The swelling drop's half extent: its full half extent scaled by the
     * swell, capped so its bottom stays the margin above a surface too close
     * to fit the full square (decision diagnose-then-fix-drip-z-fighting).
     *
     * @param halfSize  the drop's full half extent
     * @param progress  the swell, from {@link #hangProgress}
     * @param roomBelow the clear height under the spigot, up to {@link #hangingDrop}
     * @return the half extent the hanging drop draws at
     */
    static float hangingHalfSize(float halfSize, float progress, double roomBelow) {
        float roomHalf = (float) Math.max(0.0, (roomBelow - SURFACE_MARGIN) / HALVES_PER_QUAD);
        return Math.min(halfSize * progress, roomHalf);
    }

    /**
     * @param spigotY         the spigot's underside, where the drop's top is pinned
     * @param swollenHalfSize the half extent the drop draws at, from {@link #hangingHalfSize}
     * @return the y the hanging drop's camera-facing quad is centered on
     */
    static double hangingQuadCenterY(double spigotY, float swollenHalfSize) {
        return spigotY - swollenHalfSize;
    }

    /**
     * @param landingY the y the drip's collision box landed at
     * @return the y the splat's flat quad lies at
     */
    static double landQuadY(double landingY) {
        return landingY + SURFACE_MARGIN;
    }
}
