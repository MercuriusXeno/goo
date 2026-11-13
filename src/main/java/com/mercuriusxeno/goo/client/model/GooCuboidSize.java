package com.mercuriusxeno.goo.client.model;

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

    private GooCuboidSize() {
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
