package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;

/**
 * Where a drip's quads sit against the surface its collision box lands on,
 * so neither the falling drop nor its splat shares the block top's plane
 * (decision diagnose-then-fix-drip-z-fighting).
 */
public final class DripQuadPlacement {

    /** Height every drip quad keeps above the surface its collision box rests on. */
    public static final double SURFACE_MARGIN = DripFall.SURFACE_MARGIN;

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
     * The hanging drop's cuboid: it grows in all three dimensions from a point
     * to a 2x2 footprint one half extent tall, its top pinned to the spigot,
     * the growth capped so its bottom stays the margin above a surface too
     * close to fit it (decisions tap-drop-swells-then-falls, diagnose-then-fix-drip-z-fighting).
     *
     * @param spigotY   the spigot's underside
     * @param halfSize  the drop's full half extent
     * @param progress  the swell, from {@link #hangProgress}
     * @param roomBelow the clear height under the spigot, up to {@link #hangingDrop}
     * @return the cuboid the hanging drop draws
     */
    static DripCuboid.Extent hangingCuboid(double spigotY, float halfSize, float progress, double roomBelow) {
        float roomScale = (float) Math.max(0.0, (roomBelow - SURFACE_MARGIN) / halfSize);
        float scale = Math.min(progress, roomScale);
        float height = halfSize * scale;
        return new DripCuboid.Extent(spigotY - height, halfSize * scale, height);
    }

    /**
     * The falling drop's cube, a full quad height tall, its bottom the margin
     * above its collision box's bottom (decision diagnose-then-fix-drip-z-fighting).
     *
     * @param particleY the falling drip's y, the bottom of its collision box
     * @param halfSize  the drop's half extent
     * @return the cube the falling drop draws
     */
    static DripCuboid.Extent fallingCuboid(double particleY, float halfSize) {
        return new DripCuboid.Extent(particleY + SURFACE_MARGIN, halfSize, (float) (HALVES_PER_QUAD * halfSize));
    }

    /**
     * @param landingY the y the drip's collision box landed at
     * @return the y the splat's flat quad lies at
     */
    static double landQuadY(double landingY) {
        return landingY + SURFACE_MARGIN;
    }
}
