package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.BlobShape;
import net.minecraft.util.Mth;

/**
 * The palm blob's pose through a lift, pure functions of the ticks gone: a
 * lift from the palm hops, pressing a little flatter, recoiling up taller
 * and thinner than the cube with its bottom on the palm, then leaving the
 * palm to rise to the source and settle into the cube; a lift turned round
 * off the palm rises from where it was with no hop; a settle eases back and
 * presses flat on the palm with no hop on landing. Volume holds throughout:
 * a pose's spread sets its width and depth and its height takes the rest.
 * Kept apart from the spray so any lift, Knives' charge's as well as a
 * spray's, runs the same hop and settle.
 * every-lift-begins-with-a-hop
 * palm-blob-rests-pressed-and-stands-into-the-cube
 */
public final class PalmBlobHop {

    /** The ticks the rise from the palm to the source takes, and the settle back. */
    public static final int EASE_TICKS = 6;
    /**
     * The pressed palm blob's width and depth against the cube it stands
     * into; its height is what holds the cube's volume. At rest the shell
     * stands just inside the hand's six-pixel width, 5.76 pixels wide.
     * operator ruling 2026-11-13: at 1.25 the shell's sides sat flush with the gauntlet's and z-fought;
     * they stand just inside the hand
     */
    static final float PRESSED_SPREAD = 1.2f;
    /** The ticks the hop's compression takes, the blob pressing a little flatter. */
    static final int COMPRESS_TICKS = 2;
    /** The ticks the hop's recoil takes, the blob stretching up with its bottom on the palm. */
    static final int RECOIL_TICKS = 3;
    /** The ticks a hop takes before the blob's bottom leaves the palm. */
    public static final int HOP_TICKS = COMPRESS_TICKS + RECOIL_TICKS;
    /** The ticks a lift from the palm takes to reach the source: the hop, then the rise. */
    public static final int LIFT_TICKS = HOP_TICKS + EASE_TICKS;
    /**
     * The compression's spread: the pressed blob flared very slightly wider
     * and deeper, so a little shorter. A first value, for the operator to tune.
     */
    static final float COMPRESSED_SPREAD = PRESSED_SPREAD * 1.06f;
    /**
     * The recoil's spread as the bottom leaves the palm: narrower than the
     * cube, so taller. A first value, for the operator to tune.
     */
    static final float STRETCHED_SPREAD = 0.85f;
    /** The smoothstep's terms, 3s² - 2s³, slow at both ends of an ease. */
    private static final float SMOOTHSTEP_RISE = 3f;
    private static final float SMOOTHSTEP_EASE = 2f;

    private PalmBlobHop() {
    }

    /**
     * A lifted blob's pose.
     *
     * @param share  the share it has risen from the palm toward the source, 0 to 1
     * @param spread its width and depth against the cube's; its height holds the cube's volume
     * @param bottomOffThePalm how far its bottom stands off the palm along its own up, in rest half-edges
     */
    public record Pose(float share, float spread, float bottomOffThePalm) {

        /** The palm blob at rest: pressed, its bottom on the palm. */
        public static final Pose ON_THE_PALM = new Pose(0f, PRESSED_SPREAD, 0f);

        /** @return its box against a cube of its size */
        public BlobShape shape() {
            return shapeOf(spread);
        }

        /**
         * How far its center stands above the resting palm blob's center,
         * along its own up, in rest half-edges: its bottom's rise and its
         * half-height's growth over the resting blob's.
         *
         * @return the center's rise
         */
        public float stand() {
            return bottomOffThePalm + heightOf(spread) - heightOf(PRESSED_SPREAD);
        }
    }

    /**
     * A blob's box at a spread, its height holding the cube's volume.
     *
     * @param spread its width and depth against the cube's
     * @return the box against a cube of its size
     */
    static BlobShape shapeOf(float spread) {
        return new BlobShape(spread, spread, heightOf(spread));
    }

    /** @return the palm blob's box at rest, pressed flat */
    public static BlobShape pressed() {
        return shapeOf(PRESSED_SPREAD);
    }

    private static float heightOf(float spread) {
        return 1f / (spread * spread);
    }

    /**
     * The pose of a lift from the palm, which hops: a brief compression
     * flaring the blob's edges very slightly, a recoil stretching it up and
     * thinning it past the cube with its bottom on the palm, then its bottom
     * leaves the palm and it rises to the source, settling into the cube.
     * Through the rise its center keeps the height it stood at above the
     * palm as it left, so its bottom lifts as it settles shorter.
     * every-lift-begins-with-a-hop
     *
     * @param ticks the ticks since the lift began, with the frame's partial tick
     * @return the pose
     */
    public static Pose hopPoseAt(double ticks) {
        if (ticks < COMPRESS_TICKS) {
            return onThePalm(Mth.lerp(eased(ticks / COMPRESS_TICKS), PRESSED_SPREAD, COMPRESSED_SPREAD));
        }
        if (ticks < HOP_TICKS) {
            return onThePalm(Mth.lerp(eased((ticks - COMPRESS_TICKS) / RECOIL_TICKS), COMPRESSED_SPREAD,
                    STRETCHED_SPREAD));
        }
        float risen = eased((ticks - HOP_TICKS) / EASE_TICKS);
        float spread = Mth.lerp(risen, STRETCHED_SPREAD, 1f);
        return new Pose(risen, spread, heightOf(STRETCHED_SPREAD) - heightOf(spread));
    }

    /**
     * A pose on the palm, its bottom where the palm blob's rests.
     *
     * @param spread its width and depth against the cube's
     * @return the pose
     */
    private static Pose onThePalm(float spread) {
        return new Pose(0f, spread, 0f);
    }

    /**
     * The pose of a lift turned round off the palm, with no hop: it rises
     * from the place and shape it had reached to the source and the cube,
     * its bottom standing where it stood.
     *
     * @param from  the pose it had reached as the lift turned round
     * @param ticks the ticks since it turned round, with the frame's partial tick
     * @return the pose
     */
    public static Pose risePoseAt(Pose from, double ticks) {
        float risen = eased(ticks / EASE_TICKS);
        return new Pose(Mth.lerp(risen, from.share(), 1f), Mth.lerp(risen, from.spread(), 1f), from.bottomOffThePalm());
    }

    /**
     * The pose of a settle back onto the palm, with no hop on landing: from
     * the pose the lift had reached it eases home and presses flat.
     *
     * @param reached the pose the lift had reached as it ended
     * @param ticks   the ticks since the lift ended, with the frame's partial tick
     * @return the pose
     */
    public static Pose settlePoseAt(Pose reached, double ticks) {
        float settled = eased(ticks / EASE_TICKS);
        return new Pose(reached.share() * (1f - settled), Mth.lerp(settled, reached.spread(), PRESSED_SPREAD),
                reached.bottomOffThePalm() * (1f - settled));
    }

    /**
     * An ease's progress, slow at both ends.
     *
     * @param share the share of the ease's ticks gone, clamped to 0 to 1
     * @return the eased share
     */
    static float eased(double share) {
        float clamped = (float) Math.clamp(share, 0.0, 1.0);
        return clamped * clamped * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * clamped);
    }
}
