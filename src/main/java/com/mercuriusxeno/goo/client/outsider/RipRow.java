package com.mercuriusxeno.goo.client.outsider;

/**
 * One row of a conjured door's rip, measured from the middle of the door's
 * foot: a height and how far the rip's edge reaches to each side there.
 *
 * @param up         up the door's height
 * @param leftReach  how far the left edge stands from the centerline
 * @param rightReach how far the right edge stands from the centerline
 */
public record RipRow(double up, double leftReach, double rightReach) {

    /**
     * @return the left edge's place across the door, left of the centerline
     */
    public double leftAcross() {
        return -leftReach;
    }

    /**
     * @return the right edge's place across the door, right of the centerline
     */
    public double rightAcross() {
        return rightReach;
    }
}
