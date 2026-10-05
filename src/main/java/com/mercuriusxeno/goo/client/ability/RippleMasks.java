package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The solid boxes one silhouette of an afterimage's ripple fills its mask
 * channel with: every cube of the echoed body grown on every side, its six
 * faces drawn whole, so the cubes' union covers the grown body's 2D
 * projection with no seam inside it for the edge pass to find.
 * Decision afterimage-is-one-shared-effect.
 */
final class RippleMasks {

    /** Corners of a box, indexed by bits: x 1, y 2, z 4, each set on the box's high side. */
    static final int CORNERS = 8;

    /** A corner index's x bit, set on the box's high x side. */
    private static final int X_BIT = 1;
    /** A corner index's y bit. */
    private static final int Y_BIT = 2;
    /** A corner index's z bit. */
    private static final int Z_BIT = 4;
    /** The three axis bits of a corner index. */
    private static final int[] AXIS_BITS = {X_BIT, Y_BIT, Z_BIT};

    private RippleMasks() {
    }

    /**
     * The corners of a box grown on every side, indexed as CORNERS names.
     *
     * @param min    the box's low corner
     * @param max    the box's high corner
     * @param growth how far every face moves out
     * @return the eight corners
     */
    static Vector3f[] grownCorners(Vector3fc min, Vector3fc max, float growth) {
        Vector3f[] corners = new Vector3f[CORNERS];
        for (int corner = 0; corner < CORNERS; corner++) {
            corners[corner] = new Vector3f(
                    (corner & X_BIT) == 0 ? min.x() - growth : max.x() + growth,
                    (corner & Y_BIT) == 0 ? min.y() - growth : max.y() + growth,
                    (corner & Z_BIT) == 0 ? min.z() - growth : max.z() + growth);
        }
        return corners;
    }

    /**
     * The corner indices of one face, in winding order: the face across an
     * axis, on its low or high side.
     *
     * @param axis the axis index, 0 x, 1 y, 2 z
     * @param high true for the face on the axis' high side
     * @return the face's four corner indices
     */
    static int[] faceCorners(int axis, boolean high) {
        int side = high ? AXIS_BITS[axis] : 0;
        int first = AXIS_BITS[(axis + 1) % AXIS_BITS.length];
        int second = AXIS_BITS[(axis + AXIS_BITS.length - 1) % AXIS_BITS.length];
        return new int[] {side, side | first, side | first | second, side | second};
    }

    /**
     * Fills one grown cube's six faces into the mask.
     *
     * @param buffer     the vertex sink
     * @param cubeToView the transform from the cube's space into camera space
     * @param min        the cube's low corner
     * @param max        the cube's high corner
     * @param growth     blocks the silhouette stands out from the body
     * @param color      the ARGB color whose alpha the mask channel takes
     */
    static void fillGrownCube(VertexConsumer buffer, Matrix4fc cubeToView, Vector3fc min, Vector3fc max,
            float growth, int color) {
        Vector3f[] corners = grownCorners(min, max, growth);
        for (Vector3f corner : corners) {
            cubeToView.transformPosition(corner);
        }
        for (int axis = 0; axis < AXIS_BITS.length; axis++) {
            for (boolean high : new boolean[] {false, true}) {
                for (int corner : faceCorners(axis, high)) {
                    buffer.addVertex(corners[corner].x, corners[corner].y, corners[corner].z).setColor(color);
                }
            }
        }
    }
}
