package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a quad to a rectangle in the plane of two axes, the prism those two bounded and the
 * third free, so a shard's run of texel cells draws exactly its own piece of a face of the
 * item's model (decision tiles-of-the-items-image). A cut edge interpolates position, UV and color.
 */
final class QuadRectClipper {

    /** The extent under which a clipped piece counts as collapsed onto the rectangle's edge. */
    private static final float COLLAPSED = 1e-5f;
    private static final int TRIANGLE = 3;
    /** Each fanned quad takes two more corners of the polygon past the last quad's. */
    private static final int FAN_STEP = 2;
    /** Keeps the side of a plane at or above its bound. */
    private static final float KEEP_ABOVE = 1f;
    /** Keeps the side of a plane at or below its bound. */
    private static final float KEEP_BELOW = -1f;

    /** The X axis. */
    static final int X = 0;
    /** The Y axis. */
    static final int Y = 1;
    /** The Z axis. */
    static final int Z = 2;

    private QuadRectClipper() {
    }

    /**
     * One vertex of a quad being cut.
     *
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param u     the texture U
     * @param v     the texture V
     * @param color the baked ARGB color
     */
    record ClipVertex(float x, float y, float z, float u, float v, int color) {

        /**
         * Returns the point a fraction of the way from this vertex to another.
         *
         * @param other the far vertex
         * @param t     the fraction, zero at this vertex
         * @return the interpolated vertex
         */
        ClipVertex toward(ClipVertex other, float t) {
            return new ClipVertex(lerp(x, other.x, t), lerp(y, other.y, t), lerp(z, other.z, t),
                    lerp(u, other.u, t), lerp(v, other.v, t), ARGB.srgbLerp(t, color, other.color));
        }

        /**
         * @param axis {@link #X}, {@link #Y} or {@link #Z}
         * @return the position's coordinate along that axis
         */
        float along(int axis) {
            return axis == X ? x : axis == Y ? y : z;
        }

        /**
         * Returns this vertex with its position carried through a transform.
         *
         * @param transform the affine transform
         * @return the moved vertex, its UV and color kept
         */
        ClipVertex moved(Matrix4fc transform) {
            Vector3f position = transform.transformPosition(x, y, z, new Vector3f());
            return new ClipVertex(position.x(), position.y(), position.z(), u, v, color);
        }

        private static float lerp(float from, float to, float t) {
            return from + (to - from) * t;
        }
    }

    /**
     * An axis-aligned rectangle in the plane of two axes, the first axis read as X and the
     * second as Y.
     *
     * @param minX the low edge along the first axis
     * @param minY the low edge along the second axis
     * @param maxX the high edge along the first axis
     * @param maxY the high edge along the second axis
     */
    record Rect(float minX, float minY, float maxX, float maxY) {
    }

    /**
     * Reads a baked quad's four vertices.
     *
     * @param quad the baked quad
     * @return its vertices, in winding order
     */
    static List<ClipVertex> verticesOf(BakedQuad quad) {
        List<ClipVertex> vertices = new ArrayList<>(BakedQuad.VERTEX_COUNT);
        for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
            Vector3fc position = quad.position(i);
            long uv = quad.packedUV(i);
            vertices.add(new ClipVertex(position.x(), position.y(), position.z(),
                    UVPair.unpackU(uv), UVPair.unpackV(uv), quad.bakedColors().color(i)));
        }
        return vertices;
    }

    /**
     * Returns the XY rectangle a set of vertices spans.
     *
     * @param vertices the vertices
     * @return their extent in X and Y
     */
    static Rect extentOf(List<ClipVertex> vertices) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (ClipVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
        }
        return new Rect(minX, minY, maxX, maxY);
    }

    /**
     * Cuts one quad spanning X and Y to a rectangle.
     *
     * @param quad the quad's four vertices
     * @param rect the rectangle to cut to
     * @return the quads of the piece inside the rectangle, four vertices each
     */
    static List<List<ClipVertex>> clip(List<ClipVertex> quad, Rect rect) {
        return clip(quad, rect, X, Y);
    }

    /**
     * Cuts one quad to a rectangle in the plane of two axes: whole when it lies inside,
     * nothing when it lies outside or only touches an edge, otherwise the piece inside as
     * quads, a piece beyond four corners fanned into several.
     *
     * @param quad  the quad's four vertices
     * @param rect  the rectangle to cut to
     * @param axisA the axis the rectangle's X runs along
     * @param axisB the axis the rectangle's Y runs along
     * @return the quads of the piece inside the rectangle, four vertices each
     */
    static List<List<ClipVertex>> clip(List<ClipVertex> quad, Rect rect, int axisA, int axisB) {
        if (insideWhole(quad, rect, axisA, axisB)) {
            return List.of(quad);
        }
        List<ClipVertex> piece = clipAxis(quad, axisA, rect.minX(), rect.maxX());
        piece = clipAxis(piece, axisB, rect.minY(), rect.maxY());
        if (piece.size() < TRIANGLE) {
            return List.of();
        }
        return fan(piece);
    }

    private static boolean insideWhole(List<ClipVertex> quad, Rect rect, int axisA, int axisB) {
        for (ClipVertex vertex : quad) {
            if (vertex.along(axisA) < rect.minX() || vertex.along(axisA) > rect.maxX()
                    || vertex.along(axisB) < rect.minY() || vertex.along(axisB) > rect.maxY()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuts a polygon to a slab along one axis, dropping a piece collapsed onto its edge.
     *
     * @param polygon the polygon to cut
     * @param axis    the axis to cut along
     * @param low     the slab's low bound
     * @param high    the slab's high bound
     * @return the piece inside the slab, or none
     */
    private static List<ClipVertex> clipAxis(List<ClipVertex> polygon, int axis, float low, float high) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        List<ClipVertex> piece = clipPlane(clipPlane(polygon, axis, low, KEEP_ABOVE), axis, high, KEEP_BELOW);
        return piece.isEmpty() || extent(piece, axis) < COLLAPSED ? List.of() : piece;
    }

    /**
     * Returns how far a polygon spans along one axis.
     *
     * @param polygon the polygon
     * @param axis    the axis
     * @return its highest coordinate less its lowest
     */
    private static float extent(List<ClipVertex> polygon, int axis) {
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (ClipVertex vertex : polygon) {
            low = Math.min(low, vertex.along(axis));
            high = Math.max(high, vertex.along(axis));
        }
        return high - low;
    }

    /**
     * Keeps the side of one plane where side times (coordinate minus bound) is not negative,
     * one Sutherland-Hodgman pass.
     *
     * @param polygon the polygon to cut
     * @param axis    the axis the plane is normal to
     * @param bound   the plane's coordinate
     * @param side    {@link #KEEP_ABOVE} or {@link #KEEP_BELOW}
     * @return the vertices kept, with one cut vertex where an edge crosses the plane
     */
    private static List<ClipVertex> clipPlane(List<ClipVertex> polygon, int axis, float bound, float side) {
        List<ClipVertex> kept = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i < polygon.size(); i++) {
            ClipVertex from = polygon.get(i);
            ClipVertex to = polygon.get((i + 1) % polygon.size());
            float fromDistance = side * (from.along(axis) - bound);
            float toDistance = side * (to.along(axis) - bound);
            if (fromDistance >= 0f) {
                kept.add(from);
            }
            if (Math.signum(fromDistance) * Math.signum(toDistance) < 0f) {
                kept.add(from.toward(to, fromDistance / (fromDistance - toDistance)));
            }
        }
        return kept;
    }

    /**
     * Splits a convex polygon into quads sharing its first vertex; a triangle left over
     * repeats its last vertex.
     *
     * @param polygon the convex polygon, three corners or more
     * @return its quads, four vertices each
     */
    private static List<List<ClipVertex>> fan(List<ClipVertex> polygon) {
        List<List<ClipVertex>> quads = new ArrayList<>();
        ClipVertex hub = polygon.getFirst();
        int last = polygon.size() - 1;
        for (int i = 1; i < last; i += FAN_STEP) {
            quads.add(List.of(hub, polygon.get(i), polygon.get(i + 1), polygon.get(Math.min(i + FAN_STEP, last))));
        }
        return quads;
    }
}
