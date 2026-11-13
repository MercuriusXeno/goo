package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;

/**
 * The goo cuboid's size by the volume it holds, against a thrown blob's size
 * in the item model, whose ground display draws it at half: a fleck at
 * 1 mB, growing the same step for each order of magnitude up to the capped
 * size at the cap volume. The three ends are the operator's to tune in play.
 * decision fleck-size-follows-volume-on-a-log-scale
 */
final class GooCuboidSize {

    /** The cuboid's size at 1 mB and under, a little fleck of goo. */
    static final float FLECK_SIZE = 0.5f;
    /** The cuboid's size at the cap volume and above. */
    static final float CAPPED_SIZE = 3.5f;
    /** The volume in mB at which the cuboid stops growing, the largest sprite tier's. */
    static final double CAP_VOLUME = 1_000_000_000d;

    /**
     * The item model's ground display scale, item/generated's: a cuboid of a
     * size in the model stands half as wide in the level, and a dropped goo
     * item draws in the level at that same width.
     */
    static final float GROUND_DISPLAY_SCALE = 0.5f;

    /**
     * How far, in blocks, a resting cuboid's bottom stands clear of the face
     * under it: almost flush, just enough that the shell never shares the
     * floor's face and z-fights with it.
     * operator ruling: "can you just one time remember that z fighting exists"
     * operator ruling: "be almost flush with the face", 0.001 blocks
     */
    static final double FLOOR_CLEARANCE = 0.001;

    private GooCuboidSize() {
    }

    /**
     * How high above the point a goo item stands its resting cuboid's center
     * sits: its bottom just clear of the face under it.
     *
     * @param size the size against a thrown blob's in the item model
     * @return the height in blocks
     */
    static double restingCenterHeight(float size) {
        return halfWidthInLevel(size) + FLOOR_CLEARANCE;
    }

    /**
     * A cuboid's size in the level, against a thrown blob's there, at a size in the item model.
     *
     * @param size the size against a thrown blob's in the item model
     * @return the size in the level
     */
    static float sizeInLevel(float size) {
        return size * GROUND_DISPLAY_SCALE;
    }

    /**
     * A cuboid's half width in the level at a size, its shell's.
     *
     * @param size the size against a thrown blob's in the item model
     * @return the half width in blocks
     */
    static double halfWidthInLevel(float size) {
        return GooFlightRenderer.SHELL_HW * sizeInLevel(size);
    }

    /**
     * The cuboid's size for a volume: linear in log10 of the volume between
     * the fleck at 1 mB and the capped size at the cap volume, held at each
     * end beyond them. A part under 1 mB draws as a fleck.
     *
     * @param volume the goo held, in mB
     * @return the size against a thrown blob's
     */
    static float sizeOf(double volume) {
        if (volume <= 1) {
            return FLECK_SIZE;
        }
        double share = Math.min(1d, Math.log10(volume) / Math.log10(CAP_VOLUME));
        return (float) (FLECK_SIZE + (CAPPED_SIZE - FLECK_SIZE) * share);
    }
}
