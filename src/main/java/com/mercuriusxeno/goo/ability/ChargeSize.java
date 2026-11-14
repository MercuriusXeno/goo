package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.item.GooFormat;

/**
 * How a cast sized by its charge is sized and priced: holding right click
 * grows its size up to the cap its charge names, and its cost grows with its
 * volume, its JSON cost buying the reference radius, so a radius twice that
 * costs eight times as much; the size is cut back to what the holdings pay for.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 */
public final class ChargeSize {

    /** The radius an ability's JSON cost buys. */
    public static final double REFERENCE_RADIUS = 3;
    /** The smallest radius a cast opens at, however short the hold. */
    public static final double MIN_RADIUS = 1;
    private static final double CUBE = 3;
    /** Slack under a whole radius a cube root's rounding may leave, so 2.9999999 reads 3. */
    private static final double WHOLE_SLACK = 1e-9;

    private ChargeSize() {
    }

    /**
     * What a cast at a radius costs: the reference cost scaled by the cube of
     * the radius over the reference radius, rounded up.
     *
     * @param referenceCost the ability's JSON cost, the reference radius's price, in mB
     * @param radius        the radius in blocks
     * @return the cost in mB
     */
    public static int costAt(double referenceCost, double radius) {
        return (int) Math.ceil(referenceCost * Math.pow(radius / REFERENCE_RADIUS, CUBE));
    }

    /**
     * The price a held charge shows for the size it reached.
     *
     * @param referenceCost the ability's JSON cost, in mB
     * @param radius        the size the charge reached, in blocks
     * @return the formatted cost
     */
    public static String priceLabel(double referenceCost, double radius) {
        return GooFormat.formatAmount(costAt(referenceCost, radius));
    }

    /**
     * The radius a cast opens at: the size the charge reached, cut back to
     * the largest the holdings pay for, in whole blocks, since the cast is
     * charged for the whole radius it takes.
     *
     * @param charged       the size the charge reached
     * @param referenceCost the ability's JSON cost
     * @param holdings      the mB the player holds of the ability's goo type
     * @return the whole radius, the smallest radius when the holdings pay for less, which the cast then cannot afford
     */
    public static double affordable(double charged, double referenceCost, int holdings) {
        double paidFor = referenceCost <= 0 ? charged
                : REFERENCE_RADIUS * Math.cbrt(holdings / referenceCost);
        return Math.max(MIN_RADIUS, Math.floor(Math.min(charged, paidFor) + WHOLE_SLACK));
    }
}
