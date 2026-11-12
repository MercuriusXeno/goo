package com.mercuriusxeno.goo.client.outsider;

import java.util.List;

/**
 * The outline of a conjured door at a size, in the door's own plane: a thin
 * crack tapering to a point at each end while small, standing nearly the
 * doorway's whole height, widening and squaring off into the one wide, two
 * tall doorway as it grows.
 * decision door-opens-from-a-thin-crack-in-the-air
 */
public final class DoorCrack {

    /** The doorway's half width and half height at full size, as the room's two-tall doorway stands. */
    private static final double HALF_WIDE = 0.5;
    private static final double HALF_TALL = 1.0;
    /** The share of the doorway's height the crack stands at its first sliver. */
    private static final double CRACK_TALL_SHARE = 0.8;
    /** The share of the crack's height each tapered end takes at its first sliver. */
    private static final double CRACK_TAPER_SHARE = 0.35;

    private DoorCrack() {
    }

    /**
     * One corner of the outline, measured from the middle of the door's foot.
     *
     * @param across across the door's width
     * @param up     up the door's height
     */
    public record Corner(double across, double up) {
    }

    /**
     * The door's outline at a size, six corners in order around it: the
     * bottom point, up the one side, the top point, down the other. At full
     * size the points sit on the doorway's edges, so the outline is its
     * rectangle.
     *
     * @param size the door's size, 0 for nothing to 1 for full
     * @return the corners in order around the outline
     */
    public static List<Corner> outline(double size) {
        double opened = Math.clamp(size, 0, 1);
        double half = HALF_WIDE * opened;
        double halfTall = HALF_TALL * (CRACK_TALL_SHARE + (1 - CRACK_TALL_SHARE) * opened);
        double bottom = HALF_TALL - halfTall;
        double top = HALF_TALL + halfTall;
        double taper = (top - bottom) * CRACK_TAPER_SHARE * (1 - opened);
        return List.of(new Corner(0, bottom), new Corner(half, bottom + taper), new Corner(half, top - taper),
                new Corner(0, top), new Corner(-half, top - taper), new Corner(-half, bottom + taper));
    }
}
