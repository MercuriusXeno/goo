package com.mercuriusxeno.goo.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a world ability sized at will is sized and priced: pressing right
 * click pins its epicenter, dragging the look sets its radius, the distance
 * from the pin to where the look lands, and its cost grows with its volume,
 * its JSON cost buying the reference radius, so a radius twice that costs
 * eight times as much; the radius is capped at what the holdings pay for
 * (decision black-hole-leaves-a-compression-sphere).
 */
public final class DragSize {

    /** The radius an ability's JSON cost buys. */
    public static final double REFERENCE_RADIUS = 3;
    /** The smallest radius a cast opens at, however short the drag. */
    public static final double MIN_RADIUS = 1;
    private static final double CUBE = 3;
    /** Slack under a whole radius a cube root's rounding may leave, so 2.9999999 reads 3. */
    private static final double WHOLE_SLACK = 1e-9;

    private DragSize() {
    }

    /**
     * The radius a drag sets: how far the look has swung off the pin, the
     * distance from the pin to the nearest point of the look's line ahead of
     * the eye, never under the smallest radius; so dragging works at any
     * range and across open sky.
     *
     * @param pin  the pinned epicenter
     * @param eye  the player's eye
     * @param look the player's look, a unit vector
     * @return the radius in blocks
     */
    public static double dragged(Vec3 pin, Vec3 eye, Vec3 look) {
        double along = Math.max(0, pin.subtract(eye).dot(look));
        return Math.max(MIN_RADIUS, pin.distanceTo(eye.add(look.scale(along))));
    }

    /**
     * What a cast at a radius costs: the reference cost scaled by the cube of
     * the radius over the reference radius, rounded up.
     *
     * @param referenceCost the ability's JSON cost, the reference radius's price, in mB
     * @param radius        the radius in blocks
     * @return the cost in mB
     */
    public static int costAt(int referenceCost, double radius) {
        return (int) Math.ceil(referenceCost * Math.pow(radius / REFERENCE_RADIUS, CUBE));
    }

    /**
     * The radius a cast opens at: the radius dragged, cut back to the largest
     * the holdings pay for, in whole blocks, since the hole takes whole
     * blocks and is charged for the radius it takes.
     *
     * @param dragged       the radius the drag set
     * @param referenceCost the ability's JSON cost
     * @param holdings      the mB the player holds of the ability's goo type
     * @return the whole radius, the smallest radius when the holdings pay for less, which the cast then cannot afford
     */
    public static double affordable(double dragged, int referenceCost, int holdings) {
        double paidFor = referenceCost <= 0 ? dragged
                : REFERENCE_RADIUS * Math.cbrt((double) holdings / referenceCost);
        return Math.max(MIN_RADIUS, Math.floor(Math.min(dragged, paidFor) + WHOLE_SLACK));
    }
}
