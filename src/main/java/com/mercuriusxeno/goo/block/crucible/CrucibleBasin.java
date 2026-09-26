package com.mercuriusxeno.goo.block.crucible;

import org.jspecify.annotations.Nullable;

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

    /** The volume at which the puddle touches the walls and the level starts to rise. */
    public static final int SPREAD_VOLUME = 1_000;
    /** The volume at which the surface reaches the rim, and no smaller one does. */
    public static final int RIM_VOLUME = 1_000_000_000;
    /** The puddle's fixed depth above the floor while it spreads, a quarter pixel. */
    public static final float PUDDLE_DEPTH = 1f / 64f;
    /** The smallest puddle's half-width, so the first drop draws as a small square. */
    public static final float PUDDLE_MIN_HALF_WIDTH = 1f / 32f;
    /** The volume the rise curve is tuned on, a moderate amount of goo. */
    static final int MODERATE_VOLUME = 16_000;
    /** The height in pixels above the floor the moderate volume stands at. */
    static final double MODERATE_HEIGHT_PIXELS = 1.5;

    /** The footprint's center in block-relative X and Z. */
    private static final float FOOTPRINT_CENTER = (FOOTPRINT_MIN + FOOTPRINT_MAX) / 2f;
    /** The full basin's half-width, the puddle's at the spread volume. */
    private static final float FULL_HALF_WIDTH = (FOOTPRINT_MAX - FOOTPRINT_MIN) / 2f;
    /** The puddle's depth as a share of the floor-to-rim span. */
    private static final double PUDDLE_SHARE = PUDDLE_DEPTH / (RIM_Y - FLOOR_Y);

    /** Bisection rounds solving the volume scale, far past double precision. */
    private static final int SOLVER_ROUNDS = 200;
    /** The share of a bracket each bisection round keeps. */
    private static final double HALF = 0.5;
    /** The lowest volume scale the solver brackets. */
    private static final double SCALE_LOW = 1e-6;
    /** The highest volume scale the solver brackets. */
    private static final double SCALE_HIGH = 1e12;

    /** The volume the rise curve spans, from the spread volume to the rim. */
    private static final double RISE_SPAN = (double) RIM_VOLUME - SPREAD_VOLUME;
    /**
     * The volume scale under the log, chosen so {@link #MODERATE_VOLUME}
     * stands {@link #MODERATE_HEIGHT_PIXELS} above the floor.
     */
    private static final double VOLUME_SCALE = solveVolumeScale();
    /** The log of the rise span on that scale, the curve's denominator. */
    private static final double RIM_LOG = Math.log1p(RISE_SPAN / VOLUME_SCALE);

    /**
     * The square the goo covers, centered on the basin floor.
     *
     * @param min the low X and Z edge in block-relative coords
     * @param max the high X and Z edge in block-relative coords
     */
    public record PuddleFootprint(float min, float max) {

        /**
         * @return the footprint's half-width in block-relative coords
         */
        public float halfWidth() {
            return (float) ((max - min) * HALF);
        }
    }

    private CrucibleBasin() {}

    /**
     * The square the goo covers: below {@link #SPREAD_VOLUME} a puddle whose
     * area grows with volume, at and past it the whole floor (decision
     * puddle-touches-walls-at-a-thousand).
     *
     * @param volume the reservoir volume
     * @return the footprint, strictly inside the walls below the spread volume
     */
    public static PuddleFootprint footprintForVolume(long volume) {
        if (volume >= SPREAD_VOLUME) {
            return new PuddleFootprint(FOOTPRINT_MIN, FOOTPRINT_MAX);
        }
        double spread = Math.sqrt(Math.max(volume, 0L) / (double) SPREAD_VOLUME);
        float halfWidth = Math.max(PUDDLE_MIN_HALF_WIDTH, (float) (FULL_HALF_WIDTH * spread));
        return new PuddleFootprint(FOOTPRINT_CENTER - halfWidth, FOOTPRINT_CENTER + halfWidth);
    }

    /**
     * The rise curve past the spread volume: each pixel of height takes more
     * volume than the one below it, and only the rim volume fills the basin
     * (decisions each-pixel-harder-to-fill, puddle-touches-walls-at-a-thousand).
     *
     * @param volume the reservoir volume
     * @return the rise fraction in [0, 1], zero up to the spread volume and below one under the rim volume
     */
    public static float fillFraction(long volume) {
        if (volume <= SPREAD_VOLUME) { return 0f; }
        if (volume >= RIM_VOLUME) { return 1f; }
        float fraction = (float) (Math.log1p((volume - SPREAD_VOLUME) / VOLUME_SCALE) / RIM_LOG);
        return Math.min(fraction, Math.nextDown(1f));
    }

    /**
     * The surface height as a share of the floor-to-rim span: the puddle depth
     * while the goo spreads, then the rise curve on the span above it.
     *
     * @param volume the reservoir volume
     * @return the height fraction in [0, 1], zero only for an empty basin
     */
    public static float heightFraction(long volume) {
        if (volume <= 0) { return 0f; }
        if (volume >= RIM_VOLUME) { return 1f; }
        float height = (float) (PUDDLE_SHARE + fillFraction(volume) * (1.0 - PUDDLE_SHARE));
        return Math.min(height, Math.nextDown(1f));
    }

    /**
     * The surface drawn for the crucible's volumes, from the reservoir alone, so an
     * unmelted item's pool never raises goo that has not melted (decision reservoir-volume-drives-fill).
     *
     * @param volumes the reservoir and pool volumes
     * @return the surface's footprint and height, or null while the reservoir is empty
     */
    public static @Nullable DrawnSurface drawnSurface(Volumes volumes) {
        long reservoir = volumes.reservoir();
        if (reservoir <= 0) {
            return null;
        }
        return new DrawnSurface(footprintForVolume(reservoir), surfaceYForVolume(reservoir));
    }

    /**
     * The goo a crucible holds, in its two stores.
     *
     * @param reservoir the melted goo the reservoir holds, in mB
     * @param pool      the unmelted goo the partially melted item's pool holds, in mB
     */
    public record Volumes(long reservoir, long pool) {
        /** A crucible holding no goo. */
        public static final Volumes EMPTY = new Volumes(0, 0);
    }

    /**
     * The fluid surface a crucible draws.
     *
     * @param footprint the square the goo covers
     * @param surfaceY  the surface Y in block-relative coords
     */
    public record DrawnSurface(PuddleFootprint footprint, float surfaceY) {
    }

    /**
     * The pool and reservoir together, every mB the crucible holds; a
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
     * @param volume the reservoir volume
     * @return the surface Y in block-relative coords
     */
    public static float surfaceYForVolume(long volume) {
        return surfaceY(heightFraction(volume));
    }

    /**
     * The volume at which the rising surface crosses a pixel line above the puddle.
     *
     * @param pixel the pixel line, one above the floor up to {@link #PIXEL_COUNT} at the rim
     * @return the volume that raises the surface to that line
     */
    static double pixelCrossingVolume(int pixel) {
        double rise = ((double) pixel / PIXEL_COUNT - PUDDLE_SHARE) / (1.0 - PUDDLE_SHARE);
        return SPREAD_VOLUME + VOLUME_SCALE * Math.expm1(RIM_LOG * rise);
    }

    /**
     * Solves the scale under the log so the moderate volume stands at its
     * pixel height; the rise fraction falls as the scale grows, so bisection
     * on the log of the scale finds it.
     *
     * @return the volume scale under the log
     */
    private static double solveVolumeScale() {
        double targetRise = (MODERATE_HEIGHT_PIXELS / PIXEL_COUNT - PUDDLE_SHARE) / (1.0 - PUDDLE_SHARE);
        double moderateRise = (double) MODERATE_VOLUME - SPREAD_VOLUME;
        double low = Math.log(SCALE_LOW);
        double high = Math.log(SCALE_HIGH);
        for (int i = 0; i < SOLVER_ROUNDS; i++) {
            double logScale = midpoint(low, high);
            double scale = Math.exp(logScale);
            if (Math.log1p(moderateRise / scale) / Math.log1p(RISE_SPAN / scale) > targetRise) {
                low = logScale;
            } else {
                high = logScale;
            }
        }
        return Math.exp(midpoint(low, high));
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
     * (decision sparks-only-in-an-empty-crucible); a long so a reservoir past the
     * int range reads whole (decision diagnose-then-fix-holds-no-goo-width).
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
