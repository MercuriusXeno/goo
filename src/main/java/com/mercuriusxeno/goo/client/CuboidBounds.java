package com.mercuriusxeno.goo.client;

/**
 * Axis-aligned box bounds for BER rendering. Lighter than AABB (floats, no clamping).
 *
 * @param x0   minimum X
 * @param x1   maximum X
 * @param z0   minimum Z
 * @param z1   maximum Z
 * @param yBot bottom Y
 * @param yTop top Y
 */
public record CuboidBounds(float x0, float x1, float z0, float z1, float yBot, float yTop) {

    /** Returns a copy with the Y range replaced. */
    public CuboidBounds withY(float newYBot, float newYTop) {
        return new CuboidBounds(x0, x1, z0, z1, newYBot, newYTop);
    }

    /**
     * Grows the bounds outward by a mingled layer's lift on every side and the
     * top, so each layer sits outside the one below (decision noise-mingled-type-textures).
     *
     * @param lift the distance outward
     * @return the grown bounds
     */
    public CuboidBounds liftedOutward(float lift) {
        return new CuboidBounds(x0 - lift, x1 + lift, z0 - lift, z1 + lift, yBot, yTop + lift);
    }
}
