package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;

/**
 * Where the slasher's own Reap slash sits on their screen in first person.
 * The slash in the world lies along the slasher's look, so the slasher's eye
 * alone looks along its edge while every other view looks onto its face; in
 * first person the slasher's client draws its face instead, standing up as
 * an arch bowing up across the screen, its tips coming up from below the
 * bottom, the widest spanning past both sides and reaching near the top. It
 * keeps the crescent's own width, bow and thickness, narrows as the slash
 * does over its life, tilts by its roll and shifts across by its heading.
 * Screen coordinates run -1 to 1 from the left side to the right and from
 * the bottom to the top.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public final class FirstPersonSweep {

    /** Screen half-widths a block of the slash's width spans, so a 5-wide slash spans past both sides. */
    static final double ACROSS_PER_BLOCK = 0.48;
    /** Screen half-heights a block of the slash's bow and thickness rises, so a 5-wide slash nears the top. */
    static final double UP_PER_BLOCK = 0.25;
    /** How far below the screen's middle the tips stand, in screen half-heights: just past the bottom. */
    static final double TIPS_BELOW = 1.15;

    private static final double HALF = 0.5;
    private static final double SPAN = 2;

    private FirstPersonSweep() {
    }

    /**
     * A point on the screen.
     *
     * @param x across, -1 at the left side and 1 at the right
     * @param y up, -1 at the bottom and 1 at the top
     */
    public record ScreenPoint(double x, double y) {
    }

    /**
     * Where a point of the slash sits on the slasher's screen an age into its life.
     *
     * @param slash        the slash
     * @param fan          how far its heading turned off the slasher's look, rightward positive, in radians
     * @param tanHalfWidth the tangent of half the screen's width as an angle
     * @param age          ticks since it appeared
     * @param along        how far along the arc, 0 at the left tip and 1 at the right
     * @param layer        its layer, 1 on the outside edge and 0 on the inside one
     * @return the point on the screen
     */
    public static ScreenPoint pointAt(ReapSlash slash, double fan, double tanHalfWidth, double age, double along,
            double layer) {
        double kept = slash.keptAt(age);
        double offCentre = along * SPAN - 1;
        double across = offCentre * slash.width() * HALF * kept * ACROSS_PER_BLOCK;
        double up = (slash.bulge() * (1 - offCentre * offCentre)
                + slash.thickness() * Math.sin(Math.PI * along) * layer) * kept * UP_PER_BLOCK;
        double roll = slash.roll();
        double x = across * Math.cos(roll) - up * Math.sin(roll) + Math.tan(fan) / tanHalfWidth;
        double y = across * Math.sin(roll) + up * Math.cos(roll) - TIPS_BELOW;
        return new ScreenPoint(x, y);
    }
}
