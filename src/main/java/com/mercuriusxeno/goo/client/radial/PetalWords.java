package com.mercuriusxeno.goo.client.radial;

/**
 * Finds where an ability petal's words sit: a block of lines centered on
 * the petal's center line, at the spot nearest the icon where every corner
 * of the block lies inside the petal, clear of the icon and of the type
 * base. A petal's angle decides how much width it offers a line of text, so
 * a petal at 3 o'clock fits a word beside its icon and one at 12 o'clock
 * fits it above or below.
 * decision abilities-replace-the-hovered-type
 *
 * <p>Coordinates are the wheel's normalized ones: center 0, rim 1, y down positive.
 */
final class PetalWords {

    private static final double HALF = 0.5;
    private static final int CORNERS = 4;
    /** The bit of a corner's index that picks its right side. */
    private static final int RIGHT_BIT = 1;
    /** The bit of a corner's index that picks its bottom side. */
    private static final int BOTTOM_BIT = 2;

    private PetalWords() {
    }

    /**
     * A box's size.
     *
     * @param width  the width in normalized units
     * @param height the height in normalized units
     */
    record Size(double width, double height) {
    }

    /**
     * The center of the words' block: the first spot along the petal's center
     * line, stepping out from the icon toward the hub and toward the tip by
     * turns, where the block fits; or, where none fits, the spot clear of the
     * icon with the fewest corners outside the petal.
     *
     * @param petal      the ability petal
     * @param baseLength the type base's outer radius, which the words stay beyond
     * @param icon       the icon's size, centered on the petal's tip center
     * @param words      the words' block size
     * @param step       the distance between spots tried, a pixel in normalized units
     * @return the block's center
     */
    static PetalMask.Point place(PetalMask.Petal petal, double baseLength, Size icon, Size words, double step) {
        PetalMask.Point tip = petal.tipCenter();
        double axis = petal.start() + petal.arc() * HALF;
        double tipDistance = Math.hypot(tip.x(), tip.y());
        Spot best = new Spot(tip, CORNERS + 1);
        for (double offset = 0; offset <= petal.outer() && best.outside() > 0; offset += step) {
            for (double distance : new double[]{tipDistance - offset, tipDistance + offset}) {
                PetalMask.Point center = PetalMask.Point.polar(axis, distance);
                int outside = overlaps(center, words, tip, icon) ? CORNERS + 1
                        : cornersOutside(petal, baseLength, center, words);
                if (outside < best.outside()) {
                    best = new Spot(center, outside);
                }
            }
        }
        return best.center();
    }

    /**
     * A spot tried for the words and how many of the block's corners it leaves out.
     *
     * @param center  the block's center
     * @param outside the corners outside the petal or inside the base; past four, the icon is covered
     */
    private record Spot(PetalMask.Point center, int outside) {
    }

    private static boolean overlaps(PetalMask.Point center, Size box, PetalMask.Point otherCenter, Size other) {
        return Math.abs(center.x() - otherCenter.x()) < (box.width() + other.width()) * HALF
                && Math.abs(center.y() - otherCenter.y()) < (box.height() + other.height()) * HALF;
    }

    /**
     * How many of a block's corners fall outside the petal or inside the type base.
     *
     * @param petal      the petal
     * @param baseLength the type base's outer radius
     * @param center     the block's center
     * @param box        the block's size
     * @return 0 to 4
     */
    static int cornersOutside(PetalMask.Petal petal, double baseLength, PetalMask.Point center, Size box) {
        int outside = 0;
        for (int corner = 0; corner < CORNERS; corner++) {
            double x = center.x() + ((corner & RIGHT_BIT) == 0 ? -HALF : HALF) * box.width();
            double y = center.y() + ((corner & BOTTOM_BIT) == 0 ? -HALF : HALF) * box.height();
            if (!petal.contains(x, y) || Math.hypot(x, y) <= baseLength) {
                outside++;
            }
        }
        return outside;
    }
}
