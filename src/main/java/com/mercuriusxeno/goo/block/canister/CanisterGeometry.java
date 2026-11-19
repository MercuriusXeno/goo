package com.mercuriusxeno.goo.block.canister;

/**
 * Where a canister stands on the Y axis in a host: its body between two one-pixel
 * gasket caps. Every host places the same canister, so a host names only its body
 * bounds and the caps follow (decision machine-base-owns-the-lifecycle). The XZ
 * center varies with the host and lives at the call site.
 *
 * @param gasketBottom the lower cap's bottom edge, in block units
 * @param bodyBottom   the body's bottom edge, where the lower cap ends
 * @param bodyTop      the body's top edge, where the upper cap starts
 * @param gasketTop    the upper cap's top edge
 */
public record CanisterGeometry(float gasketBottom, float bodyBottom, float bodyTop, float gasketTop) {

    /** Canister half-width: 2 px. */
    public static final float HALF_WIDTH = 2f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    public static final float FLUID_INSET = 0.5f / 16f;

    /**
     * The goo surface's height above the bottom cap at the lowest fill: a tenth of a
     * pixel, so a nearly empty canister's goo never shares the cap's plane and z-fights.
     */
    public static final float FLUID_FLOOR = 0.1f / 16f;

    /** Each gasket cap is one pixel thick. */
    private static final float CAP_THICKNESS = 1f / 16f;

    /** A canister standing on the block floor: the canister block's grid and the item form. */
    public static final CanisterGeometry STANDING = at(1f / 16f, 11f / 16f);

    /**
     * A canister whose body spans the given bounds, capped one pixel below and above.
     *
     * @param bodyBottom the body's bottom edge, in block units
     * @param bodyTop    the body's top edge, in block units
     * @return the geometry
     */
    public static CanisterGeometry at(float bodyBottom, float bodyTop) {
        return new CanisterGeometry(bodyBottom - CAP_THICKNESS, bodyBottom, bodyTop, bodyTop + CAP_THICKNESS);
    }

    /**
     * The fluid surface's height at a fill fraction, rising from {@link #FLUID_FLOOR}
     * above the body bottom to the body top.
     *
     * @param fill the fill fraction in [0, 1]
     * @return the surface Y, in block units, clear of the bottom cap at any fill
     */
    // decision one-fluid-surface-clears-the-cap
    public float fluidSurface(float fill) {
        float floor = bodyBottom + FLUID_FLOOR;
        return floor + fill * (bodyTop - floor);
    }

    /**
     * The way a canister's goo settles: up its length when it stands, across its width when it lies on its side.
     * decision sideways-canister-goo-settles-as-if-turned-sideways
     */
    public enum Settling {
        /** Standing: the goo rises from the body's bottom. */
        UPRIGHT,
        /**
         * Lying on its side, turned so its own low X side is down: the goo fills across the body's width from that
         * side along its whole length.
         */
        SIDEWAYS
    }

    /**
     * A box of goo in the canister's own frame, its length along Y.
     *
     * @param minX the box's low X
     * @param maxX the box's high X
     * @param minY the box's low Y
     * @param maxY the box's high Y
     * @param minZ the box's low Z
     * @param maxZ the box's high Z
     */
    public record FluidBox(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
    }

    /**
     * The goo a fill holds inside the body, inset from its walls, as it settles: standing, rising from the body
     * bottom to the fill's surface; lying on its side, the same share of the body's width from its low X side up,
     * along the body's whole length, so the box holds the fill's share of the body either way.
     * decision sideways-canister-goo-settles-as-if-turned-sideways
     * decision one-fluid-surface-clears-the-cap
     *
     * @param settling the way the goo settles
     * @param center   the body's center across X and Z, in block units
     * @param fill     the fill fraction in [0, 1]
     * @return the goo's box
     */
    public FluidBox fluidBox(Settling settling, float center, float fill) {
        float low = center - HALF_WIDTH + FLUID_INSET;
        float high = center + HALF_WIDTH - FLUID_INSET;
        if (settling == Settling.UPRIGHT) {
            return new FluidBox(low, high, bodyBottom, fluidSurface(fill), low, high);
        }
        return new FluidBox(low, low + Math.max(FLUID_FLOOR, fill * (high - low)), bodyBottom, bodyTop, low, high);
    }
}
