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
     * The fluid surface's height at a fill fraction.
     *
     * @param fill the fill fraction in [0, 1]
     * @return the surface Y, in block units
     */
    public float fluidSurface(float fill) {
        return bodyBottom + fill * (bodyTop - bodyBottom);
    }
}
