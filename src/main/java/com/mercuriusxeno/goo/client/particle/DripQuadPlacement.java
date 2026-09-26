package com.mercuriusxeno.goo.client.particle;

/**
 * Where a drip's quads sit against the surface its collision box lands on,
 * so neither the falling drop nor its splat shares the block top's plane
 * (decision diagnose-then-fix-drip-z-fighting).
 */
final class DripQuadPlacement {

    /** Height every drip quad keeps above the surface its collision box rests on. */
    static final double SURFACE_MARGIN = 0.02;

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
     * @param landingY the y the drip's collision box landed at
     * @return the y the splat's flat quad lies at
     */
    static double landQuadY(double landingY) {
        return landingY + SURFACE_MARGIN;
    }
}
