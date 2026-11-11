package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;

/**
 * The slasher's own Reap slash in their first-person view. The slash in the
 * world lies in a plane just under the eye with its body 4 to 8 blocks out,
 * so the eye looks along it almost edge on: perspective squeezes the whole
 * body into a few degrees under the crosshair, and its ends stand beside the
 * slasher outside the view. In first person the slasher's client draws a
 * crescent that still lies flat under the eye and sweeps outward, with its
 * outline exaggerated to undo that squeeze: both edges are drawn as arcs of
 * circles on the screen, placed by the field of view, and laid back onto the
 * plane one eye height under the eye. From there the crescent narrows,
 * travels outward, tilts by its roll and turns by its heading in plain
 * perspective. Points run along the view's right, up and forward in eye
 * heights; screen heights run from -1 at the bottom to 0 at the crosshair.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public final class FirstPersonSweep {

    /** The screen height the bow's top lands at for a full-width slash as it appears. */
    static final double BOW_TOP = -0.36;
    /** The screen height the hollow's top lands at, nearly the bottom of the screen. */
    static final double HOLLOW_TOP = -0.92;
    /** The screen height the tips meet at, below the bottom of the screen. */
    static final double TIPS = -1.35;
    /** The world width of the slash whose outside edge leaves through the screen's bottom corners. */
    static final double FULL_WIDTH = 5;
    /** How far a slash travels outward over its life, as a share of its own depth, as the widest does in the world. */
    static final double TRAVEL_SHARE = 0.31;

    private static final double BOTTOM = -1;
    private static final double SPAN = 2;

    private FirstPersonSweep() {
    }

    /**
     * How wide and tall the screen opens.
     *
     * @param tanHalfWidth  the tangent of half the screen's width as an angle
     * @param tanHalfHeight the tangent of half the screen's height as an angle
     */
    public record View(double tanHalfWidth, double tanHalfHeight) {

        double aspect() {
            return tanHalfWidth / tanHalfHeight;
        }

        /**
         * How far out a point of the plane one eye height under the eye stands to show at a screen height.
         *
         * @param screenHeight the screen height, -1 at the bottom and 0 at the crosshair
         * @return the distance out along the look, in eye heights
         */
        double depthAt(double screenHeight) {
            return 1 / (tanHalfHeight * -screenHeight);
        }
    }

    /**
     * A point in the first-person view.
     *
     * @param right   along the view's right, in eye heights
     * @param up      along the view's up, in eye heights
     * @param forward along the view's forward, in eye heights
     */
    public record ViewPoint(double right, double up, double forward) {
    }

    /**
     * A point on the plane under the eye.
     *
     * @param across to the right of the look, in eye heights
     * @param out    ahead along the look, in eye heights
     */
    private record PlanePoint(double across, double out) {
    }

    /**
     * Where a point of the slash stands in the slasher's first-person view an age into its life.
     *
     * @param slash the slash
     * @param fan   how far its heading turned off the slasher's look, rightward positive, in radians
     * @param view  how wide and tall the screen opens
     * @param age   ticks since it appeared
     * @param along how far along the arc, 0 at the left tip and 1 at the right
     * @param layer its layer, 1 on the outside edge and 0 on the inside one
     * @return the point in the view
     */
    public static ViewPoint pointAt(ReapSlash slash, double fan, View view, double age, double along, double layer) {
        double sweep = along * SPAN - 1;
        PlanePoint inside = onThePlane(view, HOLLOW_TOP, sweep);
        PlanePoint outside = onThePlane(view, BOW_TOP, sweep);
        PlanePoint fresh = new PlanePoint(inside.across() + (outside.across() - inside.across()) * layer,
                inside.out() + (outside.out() - inside.out()) * layer);
        return inTheView(aged(fresh, slash, view, age), slash.roll(), fan);
    }

    /**
     * The plane point showing at a point of one edge's arc on the screen. The
     * outside edge's circle runs through the screen's bottom corners and the
     * bow's top; both edges' arcs end at the tips that circle reaches below
     * the screen.
     *
     * @param view  how wide and tall the screen opens
     * @param top   the screen height of the edge's top
     * @param sweep how far along the arc, -1 at the left tip and 1 at the right
     * @return the point on the plane
     */
    private static PlanePoint onThePlane(View view, double top, double sweep) {
        double rise = BOW_TOP - BOTTOM;
        double outsideRadius = (view.aspect() * view.aspect() + rise * rise) / (SPAN * rise);
        double underCentre = TIPS - (BOW_TOP - outsideRadius);
        double tipAcross = Math.sqrt(outsideRadius * outsideRadius - underCentre * underCentre);
        double sag = top - TIPS;
        double radius = (tipAcross * tipAcross + sag * sag) / (SPAN * sag);
        double angle = sweep * SPAN * Math.atan(sag / tipAcross);
        double out = view.depthAt(top - radius + radius * Math.cos(angle));
        return new PlanePoint(radius * Math.sin(angle) / view.aspect() * view.tanHalfWidth() * out, out);
    }

    /**
     * A point of the fresh crescent an age into the slash's life and at the
     * slash's size: narrowed about the hollow's top and sent outward.
     *
     * @param fresh the point as a full-width slash appears
     * @param slash the slash
     * @param view  how wide and tall the screen opens
     * @param age   ticks since it appeared
     * @return the point on the plane
     */
    private static PlanePoint aged(PlanePoint fresh, ReapSlash slash, View view, double age) {
        double size = slash.width() / FULL_WIDTH;
        double kept = slash.keptAt(age) * size;
        double hollow = view.depthAt(HOLLOW_TOP);
        double travelled = TRAVEL_SHARE * view.depthAt(BOW_TOP) * size * Math.clamp(age / slash.lifeTicks(), 0, 1);
        return new PlanePoint(fresh.across() * kept, hollow + (fresh.out() - hollow) * kept + travelled);
    }

    /**
     * A plane point in the view: the plane one eye height under the eye,
     * rolled about the slash's heading and turned with it off the look.
     *
     * @param point the point on the plane
     * @param roll  the slash's roll, its right end raised for a positive roll, in radians
     * @param fan   how far its heading turned off the look, rightward positive, in radians
     * @return the point in the view
     */
    private static ViewPoint inTheView(PlanePoint point, double roll, double fan) {
        double level = point.across() * Math.cos(roll) + Math.sin(roll);
        return new ViewPoint(level * Math.cos(fan) + point.out() * Math.sin(fan),
                point.across() * Math.sin(roll) - Math.cos(roll),
                point.out() * Math.cos(fan) - level * Math.sin(fan));
    }
}
