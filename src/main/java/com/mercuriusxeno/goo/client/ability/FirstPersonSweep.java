package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;

/**
 * Where the slasher's own Reap slash sits in their first-person view. The
 * slash in the world lies along the slasher's look just under the eye, so
 * the slasher's eye alone looks along its edge while the third-person camera
 * looks down onto its face; in first person the slasher's client draws it as
 * the third-person camera sees it, pointing out with its tips beside the
 * slasher and its bow running out ahead, looked down on from two blocks
 * above, with the slash's own shape, outward travel, narrowing, roll and
 * heading. Points run in blocks along the view's right, up and forward.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public final class FirstPersonSweep {

    /** How far above the slash the first-person view looks down from, in blocks, as the third-person camera does. */
    static final double VIEW_HEIGHT = 2;

    private static final double HALF = 0.5;
    private static final double SPAN = 2;

    private FirstPersonSweep() {
    }

    /**
     * A point in the first-person view.
     *
     * @param right   along the view's right, in blocks
     * @param up      along the view's up, in blocks
     * @param forward along the view's forward, in blocks
     */
    public record ViewPoint(double right, double up, double forward) {
    }

    /**
     * Where a point of the slash sits in the slasher's first-person view an age into its life.
     *
     * @param slash the slash
     * @param fan   how far its heading turned off the slasher's look, rightward positive, in radians
     * @param age   ticks since it appeared
     * @param along how far along the arc, 0 at the left tip and 1 at the right
     * @param layer its layer, 1 on the outside edge and 0 on the inside one
     * @return the point in the view
     */
    public static ViewPoint pointAt(ReapSlash slash, double fan, double age, double along, double layer) {
        double kept = slash.keptAt(age);
        double offCentre = along * SPAN - 1;
        double across = offCentre * slash.width() * HALF * kept;
        double out = slash.bulge() + slash.travelledAt(age) - slash.bulge() * kept * offCentre * offCentre
                + slash.thickness() * kept * Math.sin(Math.PI * along) * layer;
        double roll = slash.roll();
        double level = across * Math.cos(roll);
        return new ViewPoint(level * Math.cos(fan) + out * Math.sin(fan), across * Math.sin(roll) - VIEW_HEIGHT,
                out * Math.cos(fan) - level * Math.sin(fan));
    }
}
