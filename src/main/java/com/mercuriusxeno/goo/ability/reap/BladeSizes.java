package com.mercuriusxeno.goo.ability.reap;

/**
 * The sizes a Reap blade is drawn from: one size, 0 the smallest and 1 the
 * largest, sets its full width and its full depth together, so a narrow
 * blade is skinny, a wide one deep, and no blade is wide and skinny.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param narrowest  the smallest blade's full width, in blocks
 * @param widest     the largest blade's full width, in blocks
 * @param shallowest the smallest blade's full depth, in blocks
 * @param deepest    the largest blade's full depth, in blocks
 */
public record BladeSizes(double narrowest, double widest, double shallowest, double deepest) {

    /**
     * The full width of a blade of a size.
     *
     * @param size the blade's size, 0 to 1
     * @return the width in blocks
     */
    public double widthAt(double size) {
        return narrowest + (widest - narrowest) * size;
    }

    /**
     * The full depth of a blade of a size.
     *
     * @param size the blade's size, 0 to 1
     * @return the depth in blocks
     */
    public double depthAt(double size) {
        return shallowest + (deepest - shallowest) * size;
    }
}
