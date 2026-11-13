package com.mercuriusxeno.goo.client.outsider;

import java.util.List;

/**
 * The outline of a conjured door at a size, in the door's own plane: a
 * thin vertical line the doorway's full height, centered on it, widening
 * into the one wide, two tall doorway as it grows. The operator ruled a
 * plain rectangle, with no pointed ends and no change of height.
 * decision door-opens-from-a-thin-crack-in-the-air
 */
public final class DoorCrack {

    /** The doorway's half width and full height at full size, as the room's two-tall doorway stands. */
    private static final double HALF_WIDE = 0.5;
    private static final double FULL_TALL = 2.0;

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
     * The door's outline at a size: its four corners in order around it,
     * the foot then the head, its width the size's share of the doorway's.
     *
     * @param size the door's size, 0 for nothing to 1 for full
     * @return the corners in order around the outline
     */
    public static List<Corner> outline(double size) {
        double half = HALF_WIDE * Math.clamp(size, 0, 1);
        return List.of(new Corner(-half, 0), new Corner(half, 0), new Corner(half, FULL_TALL),
                new Corner(-half, FULL_TALL));
    }
}
