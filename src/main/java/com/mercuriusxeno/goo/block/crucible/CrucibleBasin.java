package com.mercuriusxeno.goo.block.crucible;

/**
 * The crucible basin's geometry, read by the client surface renderer and the
 * server particle helper alike so the drawn surface and the bubbles agree
 * (decision surface-spans-the-basin).
 *
 * <p>The bounds are the inverted cavity element of
 * {@code assets/goo/models/block/goocible.json} (and its {@code goocible_lit}
 * twin), from [4, 8, 4] to [12, 16, 12]; a change to that model's basin
 * belongs here.
 */
public final class CrucibleBasin {

    /** The basin floor in block-relative Y, the cavity's bottom face. */
    public static final float FLOOR_Y = 8f / 16f;
    /** The basin rim in block-relative Y, the top of the block. */
    public static final float RIM_Y = 16f / 16f;
    /** The footprint's low X and Z edge in block-relative coords. */
    public static final float FOOTPRINT_MIN = 4f / 16f;
    /** The footprint's high X and Z edge in block-relative coords. */
    public static final float FOOTPRINT_MAX = 12f / 16f;

    private CrucibleBasin() {}

    /**
     * Maps a fill fraction onto the basin's floor-to-rim span.
     *
     * @param fillFraction the fill fraction, clamped to [0, 1]
     * @return the surface Y in block-relative coords
     */
    public static float surfaceY(float fillFraction) {
        float clamped = Math.clamp(fillFraction, 0f, 1f);
        return FLOOR_Y + clamped * (RIM_Y - FLOOR_Y);
    }

    /**
     * @return the footprint's width along X and Z in block-relative coords
     */
    public static float footprintWidth() {
        return FOOTPRINT_MAX - FOOTPRINT_MIN;
    }
}
