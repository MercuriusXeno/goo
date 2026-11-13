package com.mercuriusxeno.goo.client.outsider;

import java.util.List;

/**
 * The outline of a conjured door, in the door's own plane: a pixel at the
 * doorway's head that unzips down to the floor as a slash, a pixel wide,
 * then widens to the left and right about its centerline into the one
 * wide, two tall doorway.
 * decision door-opens-from-a-thin-crack-in-the-air
 * decision door-unzips-from-a-starfield-pixel-then-widens-into-you
 */
public final class DoorCrack {

    /** The doorway's half width and full height at full size, as the room's two-tall doorway stands. */
    private static final double HALF_WIDE = 0.5;
    private static final double FULL_TALL = 2.0;
    /** The pixel the door starts as and the slash's width, in blocks: a starting value for the operator to judge. */
    private static final double PIXEL = 1.0 / 16;
    private static final double HALF_PIXEL = PIXEL / 2;

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
     * The door's outline for its two shares: its four corners in order
     * around it, the foot then the head. The head stays at the doorway's
     * height; the foot stands the unzipped share of that height below it,
     * never less than a pixel; the width runs from a pixel to the
     * doorway's by the widened share.
     *
     * @param unzipped how far it has unzipped, 0 for a pixel to 1 for a slash on the floor
     * @param widened  how far it has widened, 0 for a pixel wide to 1 for the doorway's full width
     * @return the corners in order around the outline
     */
    public static List<Corner> outline(double unzipped, double widened) {
        double foot = FULL_TALL - Math.max(PIXEL, FULL_TALL * Math.clamp(unzipped, 0, 1));
        double half = HALF_PIXEL + (HALF_WIDE - HALF_PIXEL) * Math.clamp(widened, 0, 1);
        return List.of(new Corner(-half, foot), new Corner(half, foot), new Corner(half, FULL_TALL),
                new Corner(-half, FULL_TALL));
    }
}
