package com.mercuriusxeno.goo.client.outsider;

import java.util.ArrayList;
import java.util.List;

/**
 * The bright rim on a conjured door's torn edge: a thin band of light
 * hugging the rip's jagged outline from the edge outward, one fixed width
 * at every size, so the first hairline already wears it, and nothing at
 * size nothing. The band takes the End portal starfield's own teal and
 * violet, laid along each side row by row: the corners at the foot and at
 * every other row after it teal, the rows between violet, so the color
 * flickers from one to the other along each zigzag of the tear.
 * decision rip-wears-a-bright-rim-on-its-torn-edge
 */
public final class DoorRim {

    /** The rim's width outside the rip's edge, in blocks: one texel of a block. */
    public static final double WIDTH = 1.0 / 16;

    /**
     * The rim's teal: the End portal shader's teal layer, COLORS[13] in
     * rendertype_end_portal.fsh (0.047281, 0.315338, 0.321970), raised until
     * its brightest channel is full, as ARGB.
     */
    public static final int TEAL = 0xFF25FAFF;

    /**
     * The rim's violet: the End portal shader's violet layer, COLORS[12] in
     * rendertype_end_portal.fsh (0.196766, 0.142899, 0.214696), raised until
     * its brightest channel is full, as ARGB.
     */
    public static final int VIOLET = 0xFFEAAAFF;

    /** The way outward from the centerline on the left side and on the right, across the door. */
    private static final int LEFT_OUTWARD = -1;
    private static final int RIGHT_OUTWARD = 1;
    private static final int SIDES = 2;

    private DoorRim() {
    }

    /**
     * One corner of the band, measured from the middle of the door's foot.
     *
     * @param across across the door's width
     * @param up     up the door's height
     * @param color  the corner's ARGB color
     */
    public record RimCorner(double across, double up, int color) {
    }

    /**
     * One quad of the band, along one segment of one side of the rip.
     *
     * @param innerBelow the segment's lower end, on the rip's edge
     * @param innerAbove the segment's upper end, on the rip's edge
     * @param outerAbove the upper end, one rim width outside the edge
     * @param outerBelow the lower end, one rim width outside the edge
     */
    public record RimQuad(RimCorner innerBelow, RimCorner innerAbove, RimCorner outerAbove, RimCorner outerBelow) {

        /**
         * @return the four corners in order around the quad
         */
        public List<RimCorner> corners() {
            return List.of(innerBelow, innerAbove, outerAbove, outerBelow);
        }
    }

    /**
     * The band tracing a rip's outline: for each segment of each side, a
     * quad from the segment outward by the rim's width, square to the
     * segment. An outline with no width has no band.
     *
     * @param outline the rip's rows from foot to head
     * @return one quad for each segment of each side
     */
    public static List<RimQuad> band(List<RipRow> outline) {
        if (outline.stream().allMatch(row -> row.leftReach() <= 0 && row.rightReach() <= 0)) {
            return List.of();
        }
        List<RimQuad> band = new ArrayList<>(SIDES * (outline.size() - 1));
        for (int index = 1; index < outline.size(); index++) {
            RipRow below = outline.get(index - 1);
            RipRow above = outline.get(index);
            RimCorner leftBelow = new RimCorner(below.leftAcross(), below.up(), colorOfRow(index - 1));
            RimCorner leftAbove = new RimCorner(above.leftAcross(), above.up(), colorOfRow(index));
            RimCorner rightBelow = new RimCorner(below.rightAcross(), below.up(), colorOfRow(index - 1));
            RimCorner rightAbove = new RimCorner(above.rightAcross(), above.up(), colorOfRow(index));
            band.add(quadOutside(leftBelow, leftAbove, LEFT_OUTWARD));
            band.add(quadOutside(rightBelow, rightAbove, RIGHT_OUTWARD));
        }
        return List.copyOf(band);
    }

    private static int colorOfRow(int row) {
        return row % SIDES == 0 ? TEAL : VIOLET;
    }

    /**
     * The band quad along one segment, offset square to it, away from the
     * centerline.
     *
     * @param below   the segment's lower end, on the rip's edge
     * @param above   the segment's upper end, on the rip's edge
     * @param outward the way outward across the door on this side
     * @return the band quad along the segment
     */
    private static RimQuad quadOutside(RimCorner below, RimCorner above, int outward) {
        double alongAcross = above.across() - below.across();
        double alongUp = above.up() - below.up();
        double length = Math.hypot(alongAcross, alongUp);
        double offAcross = outward * alongUp / length * WIDTH;
        double offUp = -outward * alongAcross / length * WIDTH;
        return new RimQuad(below, above, new RimCorner(above.across() + offAcross, above.up() + offUp, above.color()),
                new RimCorner(below.across() + offAcross, below.up() + offUp, below.color()));
    }
}
