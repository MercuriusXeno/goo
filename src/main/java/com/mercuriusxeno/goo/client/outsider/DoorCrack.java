package com.mercuriusxeno.goo.client.outsider;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline of a conjured door at a size, in the door's own plane: a
 * jagged rip, both sides zigzagging unevenly like torn cloth, each side its
 * own line, coming to a single point on the centerline at head and foot.
 * It stands the doorway's full height from the first hairline and widens
 * across only, each side's reach scaling with the size, so the opening
 * holds the rip's shape the whole way to the one wide, two tall doorway.
 * The zigzag is a fixed pattern, so the rip holds still as it moves.
 * decision conjured-door-opens-as-a-jagged-rip
 * decision door-opens-from-a-thin-crack-in-the-air
 */
public final class DoorCrack {

    /** The doorway's half width and full height at full size, as the room's two-tall doorway stands. */
    private static final double HALF_WIDE = 0.5;
    private static final double FULL_TALL = 2.0;

    /** Each row's height as a share of the doorway's, spaced unevenly so the tear reads as torn. */
    private static final double[] HEIGHT_SHARE = {0, 0.11, 0.235, 0.34, 0.475, 0.59, 0.705, 0.83, 0.915, 1};

    /**
     * Each side's reach at each row as a share of the doorway's half width.
     * Each side peaks at the full half width once, at its own height, so the
     * full rip spans the doorway; the two sides zigzag out of step.
     */
    private static final double[] LEFT_SHARE = {0, 0.55, 0.85, 0.6, 1, 0.7, 0.9, 0.5, 0.35, 0};
    private static final double[] RIGHT_SHARE = {0, 0.4, 0.75, 1, 0.65, 0.95, 0.6, 0.8, 0.3, 0};

    private DoorCrack() {
    }

    /**
     * The door's outline at a size, as rows from foot to head, the foot row
     * and the head row meeting on the centerline.
     *
     * @param size the door's size, 0 for nothing to 1 for full
     * @return the rows from foot to head
     */
    public static List<RipRow> outline(double size) {
        double half = HALF_WIDE * Math.clamp(size, 0, 1);
        List<RipRow> rows = new ArrayList<>(HEIGHT_SHARE.length);
        for (int index = 0; index < HEIGHT_SHARE.length; index++) {
            rows.add(new RipRow(FULL_TALL * HEIGHT_SHARE[index], half * LEFT_SHARE[index],
                    half * RIGHT_SHARE[index]));
        }
        return List.copyOf(rows);
    }
}
