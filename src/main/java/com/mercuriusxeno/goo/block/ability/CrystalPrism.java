package com.mercuriusxeno.goo.block.ability;

/**
 * One six-sided quartz prism with a pointed tip, in model pixels, standing on
 * {@link PrismColumn}'s base point: the plain prism's column and every crystal a
 * prism combo grows draw from these.
 * decision prism-is-one-pointed-quartz-column
 *
 * @param tilt   degrees from vertical
 * @param yaw    degrees around the vertical
 * @param length the prism's length to its tip, in pixels
 * @param radius the prism's hexagon radius, in pixels
 */
public record CrystalPrism(double tilt, double yaw, double length, double radius) {

    /** The share of the length the pointed tip takes. */
    private static final double TIP_FRACTION = 0.25;

    /**
     * @return the length the pointed tip takes, in pixels
     */
    public double tipLength() {
        return length * TIP_FRACTION;
    }
}
