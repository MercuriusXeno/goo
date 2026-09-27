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
     * @param landingY the y the drip's collision box landed at
     * @return the y the splat's flat quad lies at
     */
    static double landQuadY(double landingY) {
        return landingY + SURFACE_MARGIN;
    }
}
