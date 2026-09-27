package com.mercuriusxeno.goo.client.ber;

import java.util.Arrays;

/**
 * The low and high corner a set of points reaches along X, Y and Z, grown one point at a time.
 */
final class AxisBounds {

    /** The axes a point spans. */
    static final int AXES = 3;

    private final float[] low = new float[AXES];
    private final float[] high = new float[AXES];

    AxisBounds() {
        Arrays.fill(low, Float.MAX_VALUE);
        Arrays.fill(high, -Float.MAX_VALUE);
    }

    /**
     * Grows the bounds to reach a point.
     *
     * @param x the point's X
     * @param y the point's Y
     * @param z the point's Z
     * @return these bounds
     */
    AxisBounds include(float x, float y, float z) {
        grow(QuadRectClipper.X, x);
        grow(QuadRectClipper.Y, y);
        grow(QuadRectClipper.Z, z);
        return this;
    }

    private void grow(int axis, float value) {
        low[axis] = Math.min(low[axis], value);
        high[axis] = Math.max(high[axis], value);
    }

    /**
     * @param axis {@link QuadRectClipper#X}, {@link QuadRectClipper#Y} or {@link QuadRectClipper#Z}
     * @return the lowest coordinate reached along it
     */
    float low(int axis) {
        return low[axis];
    }

    /**
     * @param axis the axis
     * @return the highest coordinate reached along it
     */
    float high(int axis) {
        return high[axis];
    }

    /**
     * @param axis the axis
     * @return how far the points span along it
     */
    float extent(int axis) {
        return high[axis] - low[axis];
    }

    /**
     * @return the axis the points span least along, Z on a tie
     */
    int thinnest() {
        int thin = QuadRectClipper.Z;
        for (int axis = 0; axis < AXES; axis++) {
            if (extent(axis) < extent(thin)) {
                thin = axis;
            }
        }
        return thin;
    }
}
