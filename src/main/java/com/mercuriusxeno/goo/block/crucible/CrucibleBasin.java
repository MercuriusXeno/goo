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
    /** The basin's depth in model pixels, the steps the fill curve climbs. */
    public static final int PIXEL_COUNT = Math.round((RIM_Y - FLOOR_Y) * 16f);

    /** The volume that raises the surface one pixel off the floor. */
    public static final int FIRST_PIXEL_VOLUME = 1_000;
    /** The volume at which the surface reaches the rim, and no smaller one does. */
    public static final int RIM_VOLUME = 1_000_000_000;

    /** Bisection rounds solving the pixel growth ratio, far past double precision. */
    private static final int SOLVER_ROUNDS = 200;
    /** The share of a bracket each bisection round keeps. */
    private static final double HALF = 0.5;

    /**
     * The volume scale under the log, chosen so the first pixel takes
     * {@link #FIRST_PIXEL_VOLUME} and the eighth ends at {@link #RIM_VOLUME}.
     */
    private static final double VOLUME_SCALE = solveVolumeScale();
    /** The log of the rim volume on that scale, the curve's denominator. */
    private static final double RIM_LOG = Math.log1p(RIM_VOLUME / VOLUME_SCALE);

    private CrucibleBasin() {}

    /**
     * The fill curve every reader of the crucible surface shares: each pixel
     * of height takes more volume than the one below it, and only the rim
     * volume fills the basin (decision each-pixel-harder-to-fill).
     *
     * @param volume the pool and reservoir volume together
     * @return the fill fraction in [0, 1], below one for any volume under the rim volume
     */
    public static float fillFraction(long volume) {
        if (volume <= 0) { return 0f; }
        if (volume >= RIM_VOLUME) { return 1f; }
        float fraction = (float) (Math.log1p(volume / VOLUME_SCALE) / RIM_LOG);
        return Math.min(fraction, Math.nextDown(1f));
    }

    /**
     * The pool and reservoir together, the volume the surface stands for; a
     * long so it never wraps (decision diagnose-then-fix-crucible-overflow).
     *
     * @param poolVolume      the melt pool's total volume in mB
     * @param reservoirVolume the reservoir's total volume in mB
     * @return the crucible's total volume in mB
     */
    public static long heldVolume(long poolVolume, long reservoirVolume) {
        return poolVolume + reservoirVolume;
    }

    /**
     * Places the surface on the floor-to-rim span for a volume.
     *
     * @param volume the pool and reservoir volume together
     * @return the surface Y in block-relative coords
     */
    public static float surfaceYForVolume(long volume) {
        return surfaceY(fillFraction(volume));
    }

    /**
     * The volume at which the surface crosses a pixel line.
     *
     * @param pixel the pixel line, zero at the floor and {@link #PIXEL_COUNT} at the rim
     * @return the volume that raises the surface to that line
     */
    static double pixelCrossingVolume(int pixel) {
        return VOLUME_SCALE * Math.expm1(RIM_LOG * pixel / PIXEL_COUNT);
    }

    /**
     * Solves the ratio each pixel's volume grows by over the one below it, so
     * the pixel volumes, a geometric series from the first pixel's, sum to the
     * rim volume; the scale under the log follows from it.
     *
     * @return the volume scale under the log
     */
    private static double solveVolumeScale() {
        double low = 1.0;
        double high = (double) RIM_VOLUME / FIRST_PIXEL_VOLUME;
        for (int i = 0; i < SOLVER_ROUNDS; i++) {
            double ratio = midpoint(low, high);
            if (rimVolumeAtRatio(ratio) < RIM_VOLUME) {
                low = ratio;
            } else {
                high = ratio;
            }
        }
        return FIRST_PIXEL_VOLUME / (midpoint(low, high) - 1);
    }

    /**
     * @param ratio the growth ratio of each pixel's volume over the one below it
     * @return the volume the pixels sum to, the first taking {@link #FIRST_PIXEL_VOLUME}
     */
    private static double rimVolumeAtRatio(double ratio) {
        return FIRST_PIXEL_VOLUME * (Math.pow(ratio, PIXEL_COUNT) - 1) / (ratio - 1);
    }

    /**
     * @param low  the low end of the bracket
     * @param high the high end of the bracket
     * @return the value halfway between them
     */
    private static double midpoint(double low, double high) {
        return low + (high - low) * HALF;
    }

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
     * Answers whether the basin holds no goo, the only state that shows sparks
     * (decision sparks-only-in-an-empty-crucible).
     *
     * @param reservoirVolume the goo the reservoir holds
     * @param poolVolume      the goo the partially melted item's pool holds
     * @return true when the reservoir and the pool together hold nothing
     */
    public static boolean holdsNoGoo(long reservoirVolume, long poolVolume) {
        return reservoirVolume <= 0 && poolVolume <= 0;
    }

    /**
     * @return the footprint's width along X and Z in block-relative coords
     */
    public static float footprintWidth() {
        return FOOTPRINT_MAX - FOOTPRINT_MIN;
    }
}
