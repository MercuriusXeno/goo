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

    private DragSize() {
    }

    /**
     * The radius a drag sets: the distance from the pin to where the look
     * lands, never under the smallest radius.
     *
     * @param pin    the pinned epicenter
     * @param cursor where the look lands now
     * @return the radius in blocks
     */
    public static double dragged(Vec3 pin, Vec3 cursor) {
        return Math.max(MIN_RADIUS, pin.distanceTo(cursor));
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
     * the holdings pay for.
     *
     * @param dragged       the radius the drag set
     * @param referenceCost the ability's JSON cost
     * @param holdings      the mB the player holds of the ability's goo type
     * @return the radius, the smallest radius when the holdings pay for less, which the cast then cannot afford
     */
    public static double affordable(double dragged, int referenceCost, int holdings) {
        if (referenceCost <= 0) {
            return dragged;
        }
        double paidFor = REFERENCE_RADIUS * Math.cbrt((double) holdings / referenceCost);
        return Math.max(MIN_RADIUS, Math.min(dragged, paidFor));
    }
}
